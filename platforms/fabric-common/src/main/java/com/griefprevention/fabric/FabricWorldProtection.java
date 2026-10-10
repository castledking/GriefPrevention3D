package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.messages.MessageKey;
import com.griefprevention.persistence.ClaimDocument;
import com.griefprevention.protection.BlockPoint;
import com.griefprevention.protection.ClaimLookup;
import com.griefprevention.protection.FirePolicy;
import com.griefprevention.protection.FluidFlowPolicy;
import com.griefprevention.protection.PistonMode;
import com.griefprevention.protection.PistonMovementPolicy;
import com.griefprevention.protection.PvpProtectionPolicy;
import com.griefprevention.protection.WorldProtectionSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
    private static volatile @Nullable FabricWorldProtection active;

    private final @NotNull FabricClaimRepository claims;
    private final @NotNull FabricSettings settings;
    private final @NotNull FabricDenialFeedback feedback;

    private FabricWorldProtection(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricSettings settings,
            @NotNull FabricDenialFeedback feedback)
    {
        this.claims = claims;
        this.settings = settings;
        this.feedback = feedback;
    }

    static void install(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricSettings settings,
            @NotNull FabricDenialFeedback feedback)
    {
        active = new FabricWorldProtection(claims, settings, feedback);
    }

    // ---------------------------------------------------------------------------------------------
    // Fluids
    // ---------------------------------------------------------------------------------------------

    /** @param direction the way the fluid flows into {@code to} */
    public static boolean mayFlow(@NotNull LevelAccessor level, @NotNull BlockPos to, @NotNull Direction direction)
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
        return FluidFlowPolicy.mayFlow(
                fromClaim,
                toClaim,
                protection.claims.isRestrictedSubdivision(toClaim),
                protection.claims::claimById);
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
        return PistonMovementPolicy.mayMove(
                mode,
                protection.claims.worldKey(serverLevel),
                protection.claims.findClaimAt(serverLevel, pistonPos),
                affected,
                invaded,
                protection.lookup(serverLevel));
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

    // ---------------------------------------------------------------------------------------------
    // Entity damage
    // ---------------------------------------------------------------------------------------------

    /**
     * Paper's entity protection: monsters are never protected; players follow the PvP safe-zone
     * rules; frames, stands, crystals, paintings, vehicles and villagers in a claim need build trust;
     * other creatures in a claim need container trust.
     */
    public static boolean mayDamage(@NotNull Entity target, @NotNull Level targetLevel, @NotNull DamageSource source)
    {
        Player attacker = source.getEntity() instanceof Player player ? player : null;
        return mayDamage(target, targetLevel, attacker, source.getDirectEntity());
    }

    /** The same rules for a player's melee attack, checked before the attack runs. */
    public static boolean mayAttack(@NotNull Entity target, @NotNull Level targetLevel, @NotNull Player attacker)
    {
        return mayDamage(target, targetLevel, attacker, attacker);
    }

    /**
     * @param targetLevel the target's level, passed in: {@code Entity.level()} moved to an interface
     *                    in 1.21.9, so no one call reaches it on every release this adapter serves
     */
    private static boolean mayDamage(
            @NotNull Entity target,
            @NotNull Level targetLevel,
            @Nullable Player attacker,
            @Nullable Entity direct)
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
        boolean protectedKind = isBuildProtected(target) || target instanceof Villager
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

    private static boolean isBuildProtected(@NotNull Entity target)
    {
        return target instanceof HangingEntity
                || target instanceof ArmorStand
                || target instanceof EndCrystal
                || target instanceof VehicleEntity;
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
