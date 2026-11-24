package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.fabric.mixin.EntityAccessor;
import com.griefprevention.messages.MessageKey;
import com.griefprevention.persistence.ClaimDocument;
import com.griefprevention.protection.BlockPoint;
import com.griefprevention.protection.BoundaryViolationAlerts;
import com.griefprevention.protection.ClaimLookup;
import com.griefprevention.protection.EnderPearlSettings;
import com.griefprevention.protection.FirePolicy;
import com.griefprevention.protection.FluidFlowPolicy;
import com.griefprevention.protection.PistonMode;
import com.griefprevention.protection.PistonMovementPolicy;
import com.griefprevention.protection.PvpProtectionPolicy;
import com.griefprevention.protection.WorldProtectionSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the world-protection mixins ask: may this fluid flow, this piston move, this fire spread,
 * this entity be hurt. Each answer comes from a platform-neutral policy in gp3d-core; this class only
 * translates Minecraft positions and entities into claims.
 *
 * <p>Until the mod initializer installs it, every question is answered with vanilla behavior: these
 * hooks guard ticking worlds, which only exist after initialization.
 */
@ApiStatus.Internal
public final class FabricWorldProtection
{
    private static final Identifier SULFUR_CUBE = Identifier.withDefaultNamespace("sulfur_cube");
    private static final Identifier CUSHION = Identifier.withDefaultNamespace("cushion");

    private static volatile @Nullable FabricWorldProtection active;

    private final @NotNull FabricClaimRepository claims;
    private final @NotNull FabricSettings settings;
    private final @NotNull FabricDenialFeedback feedback;
    private final @NotNull FabricClaimAlerts alerts;
    private final @NotNull FabricWeavingProtection weaving;

    private FabricWorldProtection(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricSettings settings,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricClaimAlerts alerts)
    {
        this.claims = claims;
        this.settings = settings;
        this.feedback = feedback;
        this.alerts = alerts;
        this.weaving = new FabricWeavingProtection(claims, settings.world());
    }

    static void install(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricSettings settings,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricClaimAlerts alerts)
    {
        active = new FabricWorldProtection(claims, settings, feedback, alerts);
        ModPlatform.current().onPlayerJoin((player, server) -> markFreshSpawn(player));
    }

    // ---------------------------------------------------------------------------------------------
    // Fluids
    // ---------------------------------------------------------------------------------------------

    /**
     * @param direction the way the fluid flows into {@code to}
     * @param fluid the fluid that would flow there
     */
    public static boolean mayFlow(
            @NotNull LevelAccessor level,
            @NotNull BlockPos to,
            @NotNull Direction direction,
            @NotNull FluidState fluid)
    {
        FabricWorldProtection protection = active;
        // Fluids may always fall straight down, as on Paper: claims span every height.
        if (protection == null || direction == Direction.DOWN || !(level instanceof ServerLevel serverLevel))
        {
            return true;
        }
        if (!protection.applies(serverLevel))
        {
            return true;
        }

        ClaimSnapshot toClaim = protection.claims.findClaimAt(serverLevel, to);
        if (toClaim == null)
        {
            return true;
        }
        ClaimSnapshot fromClaim = protection.claims.findClaimAt(serverLevel, to.relative(direction.getOpposite()));
        boolean allowed = FluidFlowPolicy.mayFlow(
                fromClaim,
                toClaim,
                protection.claims.isRestrictedSubdivision(toClaim),
                protection.claims::claimById);
        // As on Paper, the owner hears about fluids flowing in from the wilderness.
        if (!allowed && fromClaim == null)
        {
            protection.alerts.record(serverLevel, toClaim, to, BoundaryViolationAlerts.Kind.LIQUID,
                    BoundaryViolationAlerts.Direction.EXTERNAL,
                    BoundaryViolationAlerts.liquidRadius(fluid.is(FluidTags.LAVA), serverLevel.dimension() == Level.NETHER));
        }
        return allowed;
    }

    // ---------------------------------------------------------------------------------------------
    // Pistons
    // ---------------------------------------------------------------------------------------------

    /**
     * @param facing the way the piston faces
     * @param pushDirection the way the resolved blocks move, the opposite of {@code facing} when pulling
     */
    public static boolean mayMovePiston(
            @NotNull Level level,
            @NotNull BlockPos pistonPos,
            @NotNull Direction facing,
            boolean extending,
            @NotNull Direction pushDirection,
            @NotNull List<BlockPos> toPush,
            @NotNull List<BlockPos> toDestroy)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(level instanceof ServerLevel serverLevel) || !protection.applies(serverLevel))
        {
            return true;
        }
        PistonMode mode = protection.settings.world().pistonMode();
        if (mode == PistonMode.IGNORED)
        {
            return true;
        }

        List<BlockPoint> affected = new ArrayList<>(toPush.size() * 2 + toDestroy.size());
        for (BlockPos pos : toPush)
        {
            affected.add(point(pos));
            affected.add(point(pos.relative(pushDirection)));
        }
        for (BlockPos pos : toDestroy)
        {
            affected.add(point(pos));
        }
        BlockPoint invaded = affected.isEmpty() && extending ? point(pistonPos.relative(facing)) : null;
        PistonMovementPolicy.Decision decision = PistonMovementPolicy.decide(
                mode,
                protection.claims.worldKey(serverLevel),
                protection.claims.findClaimAt(serverLevel, pistonPos),
                affected,
                invaded,
                protection.lookup(serverLevel));
        ClaimSnapshot violated = decision.violatedClaim();
        if (violated != null && decision.violation() != null)
        {
            // Where Paper points the owner: the piston for a push into their claim, the block in front
            // of it otherwise.
            boolean leaving = decision.violation() == PistonMovementPolicy.Violation.LEFT_CLAIM;
            BlockPos at = decision.violation() == PistonMovementPolicy.Violation.ENTERED_CLAIM
                    ? pistonPos
                    : pistonPos.relative(facing);
            protection.alerts.record(serverLevel, violated, at, BoundaryViolationAlerts.Kind.PISTON,
                    leaving ? BoundaryViolationAlerts.Direction.INTERNAL : BoundaryViolationAlerts.Direction.EXTERNAL,
                    BoundaryViolationAlerts.PISTON_RADIUS);
        }
        return decision.allowed();
    }

    /** Whether pistons only work inside claims in this world, which Paper warns piston placers about. */
    static boolean pistonsNeedClaims(@NotNull ServerLevel level)
    {
        FabricWorldProtection protection = active;
        return protection != null
                && protection.applies(level)
                && protection.settings.world().pistonMode() == PistonMode.CLAIMS_ONLY;
    }

    // ---------------------------------------------------------------------------------------------
    // Fire
    // ---------------------------------------------------------------------------------------------

    /** What a fire block may do to a neighboring block. */
    public enum FireVerdict
    {
        ALLOW,
        DENY,
        /** Deny, and put the fire out so it does not burn on forever beside what it cannot burn. */
        DENY_AND_EXTINGUISH
    }

    /** @param burning the block the fire at {@code fire} would burn away */
    public static @NotNull FireVerdict mayBurn(@NotNull Level level, @NotNull BlockPos burning, @NotNull BlockPos fire)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(level instanceof ServerLevel serverLevel) || !protection.applies(serverLevel))
        {
            return FireVerdict.ALLOW;
        }
        boolean allowed = FirePolicy.mayBurn(
                protection.settings.world(),
                protection.claims.findClaimAt(serverLevel, burning),
                protection.claims.findClaimAt(serverLevel, fire),
                protection.claims::claimById);
        return allowed ? FireVerdict.ALLOW : FireVerdict.DENY_AND_EXTINGUISH;
    }

    /** @param target the block the fire at {@code fire} would ignite */
    public static @NotNull FireVerdict maySpreadFire(
            @NotNull ServerLevel level,
            @NotNull BlockPos fire,
            @NotNull BlockPos target)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !protection.applies(level))
        {
            return FireVerdict.ALLOW;
        }
        WorldProtectionSettings settings = protection.settings.world();
        if (!settings.fireSpreads())
        {
            return FireVerdict.DENY_AND_EXTINGUISH;
        }
        boolean allowed = FirePolicy.maySpread(
                settings,
                protection.claims.findClaimAt(level, fire),
                protection.claims.findClaimAt(level, target),
                protection.claims::claimById);
        return allowed ? FireVerdict.ALLOW : FireVerdict.DENY_AND_EXTINGUISH;
    }

    /** Lava only ever ignites what is near it; Paper stops that wherever fire may not spread. */
    public static boolean mayLavaIgnite(@NotNull ServerLevel level)
    {
        FabricWorldProtection protection = active;
        return protection == null || !protection.applies(level) || protection.settings.world().fireSpreads();
    }

    /** Puts out a fire that is not burning on a block that keeps fire alight forever. */
    public static void extinguish(@NotNull ServerLevel level, @NotNull BlockPos fire)
    {
        BlockState state = level.getBlockState(fire);
        if (!(state.getBlock() instanceof BaseFireBlock))
        {
            return;
        }
        BlockState below = level.getBlockState(fire.below());
        if (below.is(BlockTags.INFINIBURN_OVERWORLD)
                || below.is(BlockTags.INFINIBURN_NETHER)
                || below.is(BlockTags.INFINIBURN_END))
        {
            return;
        }
        level.removeBlock(fire, false);
    }

    // ---------------------------------------------------------------------------------------------
    // Dispensers, farmland, endermen
    // ---------------------------------------------------------------------------------------------

    /** Dispensers and droppers may only act within their own claim, or wilderness to wilderness. */
    public static boolean mayDispense(@NotNull ServerLevel level, @NotNull BlockState state, @NotNull BlockPos pos)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !protection.applies(level) || !state.hasProperty(DispenserBlock.FACING))
        {
            return true;
        }
        ClaimSnapshot from = protection.claims.findClaimAt(level, pos);
        ClaimSnapshot to = protection.claims.findClaimAt(level, pos.relative(state.getValue(DispenserBlock.FACING)));
        if (from == null || to == null)
        {
            return from == to;
        }
        return from.id() != null && from.id().equals(to.id());
    }

    /**
     * Crops may only be trampled by a player with build trust. Creatures never trample them where
     * claims apply, and elsewhere only with {@code CreaturesTrampleCrops}, as on Paper.
     */
    public static boolean mayTrample(@Nullable Entity entity, @NotNull Level level, @NotNull BlockPos pos)
    {
        FabricWorldProtection protection = active;
        if (protection == null || entity == null || !(level instanceof ServerLevel serverLevel))
        {
            return true;
        }
        boolean applies = protection.applies(serverLevel);
        if (entity instanceof Player player)
        {
            if (!applies)
            {
                return true;
            }
            ClaimSnapshot claim = protection.claims.findClaimAt(serverLevel, pos);
            return claim == null || protection.claims.allows(claim, player, ClaimTrustLevel.BUILD);
        }
        if (applies || !protection.settings.world().creaturesTrampleCrops())
        {
            return false;
        }
        return !(entity.getFirstPassenger() instanceof Player);
    }

    /** {@code EndermenMoveBlocks}, which Paper applies in every world. */
    public static boolean mayEndermenMoveBlocks()
    {
        FabricWorldProtection protection = active;
        return protection == null || protection.settings.world().endermenMoveBlocks();
    }

    public static boolean maySilverfishBreakBlocks()
    {
        FabricWorldProtection protection = active;
        return protection == null || protection.settings.world().silverfishBreakBlocks();
    }

    public static boolean mayRabbitsEatCrops()
    {
        FabricWorldProtection protection = active;
        return protection == null || protection.settings.world().rabbitsEatCrops();
    }

    public static boolean mayRavagersBreakBlocks()
    {
        FabricWorldProtection protection = active;
        return protection == null || protection.settings.world().ravagersBreakBlocks();
    }

    /** Wither block breaking follows the claim explosion flags and global block-explosion setting. */
    public static boolean mayWitherGrief(@NotNull Level level, @NotNull BlockPos pos)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(level instanceof ServerLevel serverLevel) || !protection.applies(serverLevel))
        {
            return true;
        }
        return FabricExplosionProtection.mayWitherBreak(serverLevel, pos);
    }

    /** Silverfish entering blocks, rabbit crop eating, ravager path-breaking in claims. */
    public static boolean mayMobChangeClaimedBlock(@NotNull Level level, @NotNull BlockPos pos)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(level instanceof ServerLevel serverLevel) || !protection.applies(serverLevel))
        {
            return true;
        }
        return protection.claims.findClaimAt(serverLevel, pos) == null;
    }

    /**
     * LimitTreeGrowth/structure growth: every changed block that lands in a different owner's claim
     * (or in a claim when source is wilderness) is denied. The placement hook skips those blocks.
     */
    public static boolean mayGrowIntoClaims(@NotNull ServerLevel level, @NotNull BlockPos source, @NotNull java.util.List<BlockPos> changed)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !protection.settings.world().limitTreeGrowth() || !protection.applies(level))
        {
            return true;
        }
        ClaimSnapshot sourceClaim = protection.claims.findClaimAt(level, source);

        for (BlockPos pos : changed)
        {
            ClaimSnapshot claim = protection.claims.findClaimAt(level, pos);
            if (claim == null) continue;
            if (sourceClaim == null || !protection.sameOwnerTree(sourceClaim, claim))
            {
                return false;
            }
        }
        return true;
    }

    /** Nether portal frame placement over claims requires build trust in every affected claim. */
    public static boolean mayCreatePortal(@NotNull ServerLevel level, @NotNull java.util.List<BlockPos> blocks, @Nullable Entity creator)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !protection.applies(level)) return true;
        for (BlockPos pos : blocks)
        {
            ClaimSnapshot claim = protection.claims.findClaimAt(level, pos);
            if (claim == null) continue;
            if (creator instanceof ServerPlayer player)
            {
                if (!protection.claims.allows(claim, player, ClaimTrustLevel.BUILD))
                {
                    protection.feedback.denied(player, claim, ClaimTrustLevel.BUILD);
                    return false;
                }
            }
            else
            {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Entity damage
    // ---------------------------------------------------------------------------------------------

    /**
     * Paper's entity protection: monsters are never protected; players follow the PvP safe-zone
     * rules; frames, stands, crystals, paintings and villagers in a claim need build trust;
     * vehicles and other creatures in a claim need container trust.
     */
    public static boolean mayDamage(@NotNull Entity target, @NotNull Level targetLevel, @NotNull DamageSource source)
    {
        Player attacker = source.getEntity() instanceof Player player ? player : null;
        return mayDamage(target, targetLevel, attacker, source.getDirectEntity(), source);
    }

    /** The same rules for a player's melee attack, checked before the attack runs. */
    public static boolean mayAttack(@NotNull Entity target, @NotNull Level targetLevel, @NotNull Player attacker)
    {
        return mayDamage(target, targetLevel, attacker, attacker, null);
    }

    /**
     * @param targetLevel the target's level, passed in: {@code Entity.level()} moved to an interface
     *                    in 1.21.9, so no one call reaches it on every release this adapter serves
     */
    private static boolean mayDamage(
            @NotNull Entity target,
            @NotNull Level targetLevel,
            @Nullable Player attacker,
            @Nullable Entity direct,
            @Nullable net.minecraft.world.damagesource.DamageSource damageSource)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(targetLevel instanceof ServerLevel level) || target instanceof Enemy)
        {
            return true;
        }

        if (target instanceof Player defender)
        {
            return attacker == null || attacker == defender || protection.mayFight(level, attacker, defender);
        }
        if (!protection.applies(level))
        {
            return true;
        }

        if (target instanceof Villager)
        {
            // Zombies and raiders may still attack villagers, so admins can build villages players defend.
            if (attacker == null || !protection.settings.world().protectCreatures())
            {
                return true;
            }
            return protection.requireTrust(level, target, attacker, ClaimTrustLevel.BUILD, false);
        }
        if (target instanceof VehicleEntity)
        {
            if (attacker != null)
            {
                ClaimTrustLevel requiredTrust = protection.settings.vehiclesRequireBuildTrust()
                        ? ClaimTrustLevel.BUILD
                        : ClaimTrustLevel.CONTAINER;
                return protection.requireTrust(level, target, attacker, requiredTrust, true);
            }
            return protection.claims.findClaimAt(level, target.blockPosition()) == null;
        }
        if (isBuildProtected(target))
        {
            if (attacker != null)
            {
                return protection.requireTrust(level, target, attacker, ClaimTrustLevel.BUILD, false);
            }
            return protection.claims.findClaimAt(level, target.blockPosition()) == null;
        }
        if (target instanceof Mob && protection.settings.world().protectCreatures())
        {
            if (attacker != null)
            {
                if (target instanceof TamableAnimal pet && pet.isOwnedBy(attacker))
                {
                    return true;
                }
                return protection.requireTrust(level, target, attacker, ClaimTrustLevel.CONTAINER, true);
            }
            // Mobs fighting mobs is left alone; arrows from skeletons and dispensers are not.
            if (direct instanceof Projectile)
            {
                return protection.claims.findClaimAt(level, target.blockPosition()) == null;
            }
            // Protect tamed pets in claims from environmental/easy damage sources.
            if (target instanceof TamableAnimal pet && pet.isTame()
                    && protection.claims.findClaimAt(level, target.blockPosition()) != null
                    && damageSource != null && protection.isEasyPetDamage(damageSource))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Explosions never hurt what a claim protects: its creatures, frames, stands and vehicles.
     * Players and monsters take explosion damage as usual.
     */
    public static boolean mayExplosionDamage(@NotNull ServerLevel level, @NotNull Entity target)
    {
        FabricWorldProtection protection = active;
        if (protection == null || target instanceof Player || target instanceof Enemy || !protection.applies(level))
        {
            return true;
        }
        boolean protectedKind = isBuildProtected(target) || target instanceof VehicleEntity || target instanceof Villager
                || (target instanceof Mob && protection.settings.world().protectCreatures());
        return !protectedKind || protection.claims.findClaimAt(level, target.blockPosition()) == null;
    }

    private boolean mayFight(@NotNull ServerLevel level, @NotNull Player attacker, @NotNull Player defender)
    {
        // Leave combat to vanilla where PvP is off anyway, and where claims do not apply.
        if (!FabricVersionCompat.isPvpAllowed(level) || !applies(level))
        {
            return true;
        }
        if (freshImmune(attacker) || freshImmune(defender)) {
            rateLimitedError(attacker, MessageKey.CANT_FIGHT_WHILE_IMMUNE);
            return false;
        }
        if (isSafeZone(this.claims.findClaimAt(level, attacker.blockPosition())))
        {
            rateLimitedError(attacker, MessageKey.CANT_FIGHT_WHILE_IMMUNE);
            return false;
        }
        if (isSafeZone(this.claims.findClaimAt(level, defender.blockPosition())))
        {
            rateLimitedError(attacker, MessageKey.PLAYER_IN_PVP_SAFE_ZONE);
            return false;
        }
        return true;
    }

    private static final java.util.Set<Player> FRESH = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static void markFreshSpawn(ServerPlayer player) {
        FabricWorldProtection protection = active;
        if (protection != null && protection.settings.protectFreshSpawns() && !player.getAbilities().instabuild
                && !protection.claims.hasPermission(player, "griefprevention.nopvpimmunity", false)
                && player.getInventory().isEmpty()) FRESH.add(player);
    }
    public static boolean freshImmune(Player player) {
        FabricWorldProtection protection = active;
        if (protection == null || !protection.settings.protectFreshSpawns() || !player.getInventory().isEmpty()
                || player.getAbilities().instabuild || player instanceof ServerPlayer serverPlayer && protection.claims.hasPermission(serverPlayer, "griefprevention.nopvpimmunity", false)) {
            FRESH.remove(player);
            return false;
        }
        return FRESH.contains(player);
    }
    private static final java.util.Map<Entity, String> SPAWN_REASONS = new java.util.WeakHashMap<>();
    public static void rememberSpawnReason(Mob mob, String reason) { SPAWN_REASONS.put(mob, reason); }
    public static boolean maySpawn(ServerLevel level, Entity entity) {
        FabricWorldProtection protection = active;
        if (protection == null || !protection.applies(level)) return true;
        if (protection.settings.world().creativeRulesApply(protection.claims.worldKey(level))) {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.ExperienceOrb) return false;
            if (entity instanceof Mob || entity instanceof ArmorStand) {
                ClaimSnapshot claim = protection.claims.findClaimAt(level, entity.blockPosition());
                if (claim == null) return false;
                String reason = SPAWN_REASONS.remove(entity);
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
                if (!(entity instanceof ArmorStand) && !type.equals("iron_golem") && !type.equals("snow_golem")
                        && !"SPAWN_ITEM_USE".equals(reason) && !"DISPENSER".equals(reason)) return false;
                while (claim.parentId() != null) {
                    ClaimSnapshot parent = protection.claims.claimById(claim.parentId());
                    if (parent == null) break;
                    claim = parent;
                }
                if (claim.ownerId() != null && claim.bounds().area() <= 10000) {
                    var b = claim.bounds();
                    final ClaimSnapshot root = claim;
                    int count = level.getEntities(entity, new net.minecraft.world.phys.AABB(b.minX(), b.minY(), b.minZ(), b.maxX()+1.0, b.maxY()+1.0, b.maxZ()+1.0),
                            other -> !(other instanceof Player) && root.bounds().contains(other.blockPosition().getX(), other.blockPosition().getY(), other.blockPosition().getZ(), !root.threeDimensional())).size();
                    if (count >= b.area() / 50) return false;
                }
            }
        }
        var context = FabricMutationContext.current();
        if (context != null && context.death() && context.actor() instanceof ServerPlayer player
                && entity instanceof net.minecraft.world.entity.item.ItemEntity item
                && protection.settings.lockDeathDrops(FabricVersionCompat.isPvpAllowed(level))) item.setTarget(player.getUUID());
        return true;
    }
    public static boolean mayMoveSulfurCube(Entity entity, net.minecraft.world.phys.Vec3 movement) {
        FabricWorldProtection protection = active;
        if (protection == null || !isSulfurCube(entity) || !(((com.griefprevention.fabric.mixin.EntityAccessor)entity).griefPrevention$getLevel() instanceof ServerLevel level) || !protection.applies(level)) return true;
        ClaimSnapshot from = protection.claims.findClaimAt(level, entity.blockPosition());
        if (from == null) return true;
        ClaimSnapshot to = protection.claims.findClaimAt(level, BlockPos.containing(entity.getX() + movement.x, entity.getY() + movement.y, entity.getZ() + movement.z));
        return to != null && protection.sameOwnerTree(from, to);
    }

    private ClaimSnapshot rootClaim(ClaimSnapshot claim) {
        while (claim.parentId() != null) {
            ClaimSnapshot parent = this.claims.claimById(claim.parentId());
            if (parent == null) break;
            claim = parent;
        }
        return claim;
    }

    private boolean sameOwnerTree(ClaimSnapshot first, ClaimSnapshot second) {
        ClaimSnapshot a = rootClaim(first);
        ClaimSnapshot b = rootClaim(second);
        return java.util.Objects.equals(a.id(), b.id())
                || a.ownerId() != null && a.ownerId().equals(b.ownerId());
    }

    private boolean isSafeZone(@Nullable ClaimSnapshot claim)
    {
        if (claim == null || claim.id() == null)
        {
            return false;
        }
        ClaimDocument document = this.claims.documentFor(claim.id());
        boolean pvpEnabled = document == null || document.pvpEnabled();
        boolean pvpToggled = document != null && document.pvpToggled();
        return PvpProtectionPolicy.isSafeZone(
                this.settings.world(), claim, pvpEnabled, pvpToggled, this.claims::claimById);
    }

    private boolean isEasyPetDamage(@NotNull DamageSource source)
    {
        try
        {
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING))
            {
                return true;
            }
        }
        catch (Throwable ignored) {}
        String msg = "";
        try { msg = String.valueOf(source.getMsgId()); } catch (Throwable ignored) {}
        return "cactus".equals(msg) || "sweetBerryBush".equals(msg) || "inWall".equals(msg) || "cramming".equals(msg)
                || "lava".equals(msg) || "hotFloor".equals(msg) || "drown".equals(msg) || "fall".equals(msg);
    }

    private boolean requireTrust(
            @NotNull ServerLevel level,
            @NotNull Entity target,
            @NotNull Player attacker,
            @NotNull ClaimTrustLevel required,
            boolean creature)
    {
        ClaimSnapshot claim = this.claims.findClaimAt(level, target.blockPosition());
        if (claim == null || this.claims.allows(claim, attacker, required))
        {
            return true;
        }
        if (creature && attacker instanceof ServerPlayer player)
        {
            // Paper names the owner of the creature rather than the missing trust level.
            this.feedback.sendRateLimitedError(player, MessageKey.NO_DAMAGE_CLAIMED_ENTITY,
                    this.feedback.ownerName(player, claim));
        }
        else
        {
            this.feedback.denied(attacker, claim, required);
        }
        return false;
    }

    private void rateLimitedError(@NotNull Player player, @NotNull MessageKey key)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            this.feedback.sendRateLimitedError(serverPlayer, key);
        }
    }

    /**
     * Sulfur cubes (26.2+) are creatures but not animals. They are recognized by id: the 1.21.11 and
     * 26.1 adapters are compiled before the class existed.
     */
    private static boolean isSulfurCube(@NotNull Entity entity)
    {
        return SULFUR_CUBE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /**
     * Cushions (26.3+) are placed and broken like decorations, so breaking one needs build trust.
     * Recognized by id for the same reason as the sulfur cube: no adapter predates the class.
     */
    public static boolean isCushion(@NotNull Entity entity)
    {
        return CUSHION.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    private static boolean isBuildProtected(@NotNull Entity target)
    {
        return target instanceof HangingEntity
                || target instanceof ArmorStand
                || target instanceof EndCrystal
                || isCushion(target);
    }

    // ---------------------------------------------------------------------------------------------
    // Ender pearls, chorus fruit, lightning, fishing rods and falling blocks
    // ---------------------------------------------------------------------------------------------

    /**
     * Paper's {@code EnderPearlsRequireAccessTrust}: a pearl may not carry its thrower into a claim
     * they have no access trust in. A refused pearl is given back with {@code RefundDeniedEnderPearls},
     * unless the thrower is in creative mode and never spent it.
     */
    public static boolean mayPearlLand(@NotNull Projectile pearl, @NotNull HitResult hit)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(pearl.getOwner() instanceof ServerPlayer player))
        {
            return true;
        }
        EnderPearlSettings pearls = protection.settings.enderPearls();
        if (!pearls.requireAccessTrust()
                || !(((EntityAccessor) pearl).griefPrevention$getLevel() instanceof ServerLevel level)
                || !protection.applies(level))
        {
            return true;
        }

        ClaimSnapshot claim = protection.claims.findClaimAt(level, BlockPos.containing(hit.getLocation()));
        if (claim == null || protection.claims.allows(claim, player, ClaimTrustLevel.ACCESS))
        {
            return true;
        }
        protection.feedback.denied(player, claim, ClaimTrustLevel.ACCESS);
        if (pearls.refundDenied() && !player.getAbilities().instabuild)
        {
            player.getInventory().add(new ItemStack(
                    BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("ender_pearl"))));
        }
        return false;
    }

    /**
     * Chorus fruit falls under the same rule: a jump that landed in a claim the eater has no access
     * trust in is undone, so vanilla tries another spot.
     *
     * @return false when the jump was undone
     */
    public static boolean mayChorusLand(@NotNull LivingEntity entity, double fromX, double fromY, double fromZ)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(entity instanceof ServerPlayer player)
                || !protection.settings.enderPearls().requireAccessTrust())
        {
            return true;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!protection.applies(level))
        {
            return true;
        }

        ClaimSnapshot claim = protection.claims.findClaimAt(level, player.blockPosition());
        if (claim == null || protection.claims.allows(claim, player, ClaimTrustLevel.ACCESS))
        {
            return true;
        }
        protection.feedback.denied(player, claim, ClaimTrustLevel.ACCESS);
        player.teleportTo(fromX, fromY, fromZ);
        return false;
    }

    /** Paper cancels the fire a lightning strike would start in a claim. */
    public static boolean mayLightningStartFire(@NotNull Level level, @NotNull Entity bolt)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(level instanceof ServerLevel serverLevel) || !protection.applies(serverLevel))
        {
            return true;
        }
        return protection.claims.findClaimAt(serverLevel, bolt.blockPosition()) == null;
    }

    /**
     * Paper's PlayerFishEvent rule: reeling a claimed animal, armor stand, sulfur cube or cushion in
     * takes container trust.
     */
    public static boolean mayReelIn(@Nullable Player angler, @NotNull Entity hooked)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(angler instanceof ServerPlayer player)
                || !(hooked instanceof ArmorStand || hooked instanceof Animal || isSulfurCube(hooked)
                        || isCushion(hooked)))
        {
            return true;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!protection.applies(level))
        {
            return true;
        }

        ClaimSnapshot claim = protection.claims.findClaimAt(level, hooked.blockPosition());
        if (claim == null || protection.claims.allows(claim, player, ClaimTrustLevel.CONTAINER))
        {
            return true;
        }
        protection.feedback.sendRateLimitedError(player, MessageKey.NO_DAMAGE_CLAIMED_ENTITY,
                protection.feedback.ownerName(player, claim));
        return false;
    }

    /**
     * Paper's rule against sand cannons. A block that fell straight down always lands. One that
     * drifted sideways may land in the wilderness, or in a claim that holds where it fell from, or in
     * an unrestricted subdivision when it fell from the parent claim around it; anywhere else, and
     * anywhere at all in a creative-rules world, it drops as an item.
     *
     * @param start where the block fell from
     */
    public static boolean mayFallingBlockLand(@NotNull Level level, @NotNull BlockPos start, @NotNull BlockPos landing)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(level instanceof ServerLevel serverLevel) || !protection.applies(serverLevel))
        {
            return true;
        }
        // A falling block that was summoned rather than dropped has no origin; Paper lets those land.
        if (start.equals(BlockPos.ZERO) || (start.getX() == landing.getX() && start.getZ() == landing.getZ()))
        {
            return true;
        }
        String worldKey = protection.claims.worldKey(serverLevel);
        if (protection.settings.world().creativeRulesApply(worldKey))
        {
            return false;
        }

        ClaimSnapshot claim = protection.claims.findClaimAt(serverLevel, landing);
        if (claim == null || claim.contains(worldKey, start.getX(), start.getY(), start.getZ(), false))
        {
            return true;
        }
        Long parentId = claim.parentId();
        if (parentId == null || protection.claims.isRestrictedSubdivision(claim))
        {
            return false;
        }
        ClaimSnapshot origin = protection.claims.findClaimAt(serverLevel, start);
        return origin != null && parentId.equals(origin.id());
    }

    /** Each weaving web is checked at its destination, independently of where the entity died. */
    public static boolean mayPlaceWeavingWeb(
            @NotNull ServerLevel level,
            @NotNull BlockPos pos,
            @Nullable LivingEntity source)
    {
        FabricWorldProtection protection = active;
        if (protection == null) return true;

        boolean ignoring = source instanceof ServerPlayer player
                && protection.claims.isIgnoringClaims(player.getUUID())
                && protection.claims.hasPermission(player, FabricPermissionDefaults.IGNORE_CLAIMS);
        return protection.weaving.mayPlaceWeb(protection.claims.worldKey(level), point(pos),
                source instanceof Player player
                        ? (claim, trust) -> protection.claims.allows(claim, player, trust)
                        : null,
                ignoring);
    }

    /** Splash and lingering weaving use container trust for claimed animals and villagers. */
    public static boolean mayApplyWeaving(
            @NotNull LivingEntity target,
            @NotNull Level targetLevel,
            @Nullable Entity source)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(targetLevel instanceof ServerLevel level) || !protection.applies(level))
        {
            return true;
        }

        Entity actor = source instanceof AreaEffectCloud cloud ? cloud.getOwner() : source;
        if (actor instanceof Projectile projectile && projectile.getOwner() != null) actor = projectile.getOwner();
        if (actor == target) return true;
        if (target instanceof Player defender)
        {
            return !(actor instanceof Player attacker) || protection.mayFight(level, attacker, defender);
        }
        if (!(target instanceof Animal || target instanceof Villager)) return true;

        Entity responsible = actor;
        boolean allowed = protection.weaving.mayApplyToCreature(
                protection.claims.worldKey(level), point(target.blockPosition()),
                responsible instanceof Player player
                        ? (claim, trust) -> protection.claims.allows(claim, player, trust)
                        : null,
                source == null ? null : FabricPotionSources.origin(source));
        if (!allowed && responsible instanceof Player player)
        {
            protection.requireTrust(level, target, player, ClaimTrustLevel.CONTAINER, true);
        }
        return allowed;
    }

    /** Non-weaving splash/lingering effects: protect claimed creatures and PvP-safe players. */
    public static boolean mayApplyPotionEffect(
            @NotNull LivingEntity target,
            @NotNull Level targetLevel,
            @Nullable Entity source,
            @NotNull net.minecraft.world.effect.MobEffectInstance effect)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !(targetLevel instanceof ServerLevel level) || !protection.applies(level))
        {
            return true;
        }
        if (effect.getEffect().equals(net.minecraft.world.effect.MobEffects.WEAVING)) return true;

        Entity actor = source instanceof AreaEffectCloud cloud ? cloud.getOwner() : source;
        if (actor instanceof Projectile projectile && projectile.getOwner() != null) actor = projectile.getOwner();
        if (actor == target) return true;

        boolean harmful = isNegativePotion(effect);
        if (target instanceof Player defender)
        {
            if (!harmful) return true;
            return !(actor instanceof Player attacker) || protection.mayFight(level, attacker, defender);
        }
        if (!(target instanceof Animal || target instanceof Villager)) return true;
        if (!harmful && !effect.getEffect().equals(net.minecraft.world.effect.MobEffects.JUMP_BOOST)) return true;

        Entity responsible = actor;
        boolean allowed = protection.weaving.mayApplyToCreature(
                protection.claims.worldKey(level), point(target.blockPosition()),
                responsible instanceof Player player
                        ? (claim, trust) -> protection.claims.allows(claim, player, trust)
                        : null,
                source == null ? null : FabricPotionSources.origin(source));
        if (!allowed && responsible instanceof Player player)
        {
            protection.requireTrust(level, target, player, ClaimTrustLevel.CONTAINER, true);
        }
        return allowed;
    }

    private static boolean isNegativePotion(@NotNull net.minecraft.world.effect.MobEffectInstance effect)
    {
        try
        {
            return !effect.getEffect().value().isBeneficial();
        }
        catch (Throwable ignored)
        {
            return true;
        }
    }

    public static void rememberDispensedProjectile(
            @NotNull Entity projectile,
            @NotNull ServerLevel level,
            @NotNull BlockPos dispenser)
    {
        FabricWorldProtection protection = active;
        if (protection != null)
        {
            FabricPotionSources.remember(projectile,
                    new FabricPotionSources.Origin(protection.claims.worldKey(level), point(dispenser)));
        }
    }

    /** Paper limits hoppers, beacons and spawners to one per 100 claim blocks in creative worlds. */
    public static boolean mayPlaceCreativeActive(Level level, BlockPos pos, BlockState state) {
        FabricWorldProtection protection = active;
        var context = FabricMutationContext.current();
        if (protection == null || !(level instanceof ServerLevel server) || context == null || context.portal()
                || !(context.actor() instanceof ServerPlayer player)
                || protection.claims.hasPermission(player, FabricPermissionDefaults.ADMIN_CLAIMS)
                || !protection.settings.world().creativeRulesApply(protection.claims.worldKey(server))
                || !isCreativeActive(state) || isCreativeActive(level.getBlockState(pos))) return true;
        ClaimSnapshot claim = protection.claims.findClaimAt(server, pos);
        if (claim == null) return false;
        while (claim.parentId() != null) {
            ClaimSnapshot parent = protection.claims.claimById(claim.parentId());
            if (parent == null) break;
            claim = parent;
        }
        var b = claim.bounds();
        int count = 0;
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) {
            for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++) {
                var chunk = server.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var block : chunk.getBlockEntities().values()) {
                    var at = block.getBlockPos();
                    if (b.contains(at.getX(), at.getY(), at.getZ(), !claim.threeDimensional()) && isCreativeActive(block.getBlockState())) count++;
                }
            }
        }
        if (count < b.area() / 100) return true;
        protection.rateLimitedError(player, MessageKey.TOO_MANY_ACTIVE_BLOCKS_IN_CLAIM);
        return false;
    }
    private static boolean isCreativeActive(BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.HOPPER)
                || state.is(net.minecraft.world.level.block.Blocks.BEACON)
                || state.is(net.minecraft.world.level.block.Blocks.SPAWNER);
    }

    public static boolean mayScopedChange(Level level, BlockPos pos) {
        var context = FabricMutationContext.current();
        if (context == null) return true;
        if (context.silverfish() && !maySilverfishBreakBlocks()) return false;
        if (context.rabbit() && !mayRabbitsEatCrops()) return false;
        if (context.growthOrigin() != null && level instanceof ServerLevel server)
            return mayGrowIntoClaims(server, context.growthOrigin(), java.util.List.of(pos));
        if (context.portal() && level instanceof ServerLevel server)
            return mayCreatePortal(server, java.util.List.of(pos), context.actor());
        return true;
    }

    public static boolean mayMobDestroy(Level level, BlockPos pos, Entity actor) {
        if (!mayScopedChange(level, pos)) return false;
        if (actor == null) return true;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()).getPath();
        if (id.equals("silverfish")) return maySilverfishBreakBlocks();
        if (id.equals("rabbit")) return mayRabbitsEatCrops();
        if (id.equals("ravager")) return mayRavagersBreakBlocks();
        if (id.equals("wither")) return mayWitherGrief(level, pos);
        return true;
    }

    private record ContainerLocation(ServerLevel level, BlockPos pos) {}
    private static ContainerLocation containerLocation(net.minecraft.world.Container container) {
        if (container instanceof net.minecraft.world.level.block.entity.BlockEntity block && block.getLevel() instanceof ServerLevel level)
            return new ContainerLocation(level, block.getBlockPos());
        if (container instanceof Entity entity && ((com.griefprevention.fabric.mixin.EntityAccessor)entity).griefPrevention$getLevel() instanceof ServerLevel level)
            return new ContainerLocation(level, entity.blockPosition());
        return null;
    }
    public static boolean mayTransferItems(net.minecraft.world.Container source, net.minecraft.world.Container destination) {
        FabricWorldProtection protection = active;
        if (protection == null || source == null || !protection.settings.hoppersRequireBuildTrust()) return true;
        if (source instanceof net.minecraft.world.CompoundContainer) {
            var halves = (com.griefprevention.fabric.mixin.CompoundContainerAccessor) source;
            return mayTransferItems(halves.griefPrevention$first(), destination) && mayTransferItems(halves.griefPrevention$second(), destination);
        }
        if (destination instanceof net.minecraft.world.CompoundContainer) {
            var halves = (com.griefprevention.fabric.mixin.CompoundContainerAccessor) destination;
            return mayTransferItems(source, halves.griefPrevention$first()) && mayTransferItems(source, halves.griefPrevention$second());
        }
        ContainerLocation from = containerLocation(source), to = containerLocation(destination);
        if (from == null || to == null || !protection.applies(from.level())) return true;
        ClaimSnapshot a = protection.claims.findClaimAt(from.level(), from.pos());
        ClaimSnapshot b = protection.claims.findClaimAt(to.level(), to.pos());
        return a == null ? b == null : b != null && java.util.Objects.equals(a.id(), b.id());
    }
    public static boolean mayHopperPickup(net.minecraft.world.Container hopper, net.minecraft.world.entity.item.ItemEntity item) {
        if (((com.griefprevention.fabric.mixin.ItemEntityAccessor)item).griefPrevention$getTarget() != null) return false;
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Lecterns
    // ---------------------------------------------------------------------------------------------

    /** The lectern behind each open lectern menu: the menu itself only holds the book. */
    private static final Map<AbstractContainerMenu, LecternBlockEntity> LECTERN_MENUS = new WeakHashMap<>();

    public static void lecternMenuOpened(@NotNull AbstractContainerMenu menu, @NotNull LecternBlockEntity lectern)
    {
        LECTERN_MENUS.put(menu, lectern);
    }

    /**
     * Paper's PlayerTakeLecternBookEvent rule: reading may only take access trust, but taking the
     * book off the lectern takes container trust.
     */
    public static boolean mayTakeLecternBook(@NotNull AbstractContainerMenu menu, @NotNull Player player)
    {
        FabricWorldProtection protection = active;
        LecternBlockEntity lectern = LECTERN_MENUS.get(menu);
        if (protection == null || lectern == null || !(lectern.getLevel() instanceof ServerLevel level))
        {
            return true;
        }

        ClaimSnapshot claim = protection.claims.findClaimAt(level, lectern.getBlockPos());
        if (claim == null || protection.claims.allows(claim, player, ClaimTrustLevel.CONTAINER))
        {
            return true;
        }
        protection.feedback.denied(player, claim, ClaimTrustLevel.CONTAINER);
        if (player instanceof ServerPlayer serverPlayer)
        {
            serverPlayer.closeContainer();
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private boolean applies(@NotNull ServerLevel level)
    {
        return this.settings.world().appliesTo(this.claims.worldKey(level));
    }

    /**
     * Paper's {@code RaidTriggerEvent} rule: build trust is required in the raid's village claim;
     * when no claim covers the raid center, the player must have build trust where they stand.
     */
    public static boolean mayTriggerRaid(@NotNull ServerPlayer player, @NotNull BlockPos raidCenter)
    {
        FabricWorldProtection protection = active;
        if (protection == null || !protection.settings.raidTriggersRequireBuildTrust())
        {
            return true;
        }

        ServerLevel level = (ServerLevel) player.level();
        if (!protection.applies(level))
        {
            return true;
        }

        ClaimSnapshot claim = protection.claims.findClaimAt(level, raidCenter);
        if (claim == null)
        {
            claim = protection.claims.findClaimAt(level, player.blockPosition());
        }
        return claim == null || protection.claims.allows(claim, player, ClaimTrustLevel.BUILD);
    }

    private @NotNull ClaimLookup lookup(@NotNull ServerLevel level)
    {
        FabricClaimRepository repository = this.claims;
        return new ClaimLookup()
        {
            @Override
            public @Nullable ClaimSnapshot claimAt(int x, int y, int z)
            {
                return repository.findClaimAt(level, new BlockPos(x, y, z));
            }

            @Override
            public @Nullable ClaimSnapshot claimById(long id)
            {
                return repository.claimById(id);
            }
        };
    }

    private static @NotNull BlockPoint point(@NotNull BlockPos pos)
    {
        return new BlockPoint(pos.getX(), pos.getY(), pos.getZ());
    }

}
