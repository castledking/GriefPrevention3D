package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.fabric.mixin.EntityAccessor;
import com.griefprevention.protection.BlockPlaceClimbTracker;
import com.griefprevention.protection.BlockPlaceClimbTracker.Box;
import com.griefprevention.protection.BlockPlaceClimbTracker.Position;
import com.griefprevention.protection.BlockPoint;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Corrects only movement associated with a GP-denied block placement near the player's feet. */
@ApiStatus.Internal
public final class FabricBlockPlaceClimbProtection
{
    private static volatile FabricBlockPlaceClimbProtection active;
    private final FabricClaimRepository claims;
    private final FabricSettings settings;
    private final Map<UUID, State> players = new ConcurrentHashMap<>();

    private FabricBlockPlaceClimbProtection(FabricClaimRepository claims, FabricSettings settings)
    {
        this.claims = claims;
        this.settings = settings;
    }

    static void install(FabricClaimRepository claims, FabricSettings settings)
    {
        active = new FabricBlockPlaceClimbProtection(claims, settings);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.getPlayer()));
    }

    static void deniedPlacement(Player actor, ServerLevel level, InteractionHand hand, BlockHitResult hit)
    {
        FabricBlockPlaceClimbProtection protection = active;
        if (protection == null || !(actor instanceof ServerPlayer player) || !protection.enabled(player, level)) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item)) return;
        BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
        BlockPos pos = context.getClickedPos();
        ClaimSnapshot claim = protection.claims.findClaimAt(level, pos);
        if (claim == null || protection.claims.allows(claim, player, ClaimTrustLevel.BUILD)) return;
        BlockState placed = item.getBlock().getStateForPlacement(context);
        if (placed == null) return;
        boolean climbable = placed.is(BlockTags.CLIMBABLE);
        List<Box> collision = placed.getCollisionShape(level, pos).toAabbs().stream()
                .map(box -> new Box(pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ,
                        pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ)).toList();
        if (collision.isEmpty() && !climbable) return;

        State state = protection.state(player, level);
        Position current = position(player);
        long now = now();
        if (canStand(level, player, current)) state.tracker.recordSupported(current, now);
        boolean realClimbing = player.onClimbable();
        if (realClimbing) state.tracker.allowRealClimbing();
        Position back = !climbable || realClimbing ? null : state.tracker.denyClimbablePlacement(current,
                Collections.singletonList(new BlockPoint(pos.getX(), pos.getY(), pos.getZ())), now);
        if (back == null) back = state.tracker.denyPlacement(current, collision, now);
        if (back != null && canStand(level, player, back)) correct(player, back);
    }

    /** Called before vanilla processes movement, after the packet reaches the server thread. */
    public static boolean beforeMove(ServerPlayer player, double x, double y, double z, float yaw, float pitch)
    {
        FabricBlockPlaceClimbProtection protection = active;
        ServerLevel level = (ServerLevel) ((EntityAccessor) player).griefPrevention$getLevel();
        if (protection == null || !level.getServer().isSameThread() || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || !protection.enabled(player, level)) return false;
        State state = protection.state(player, level);
        Position current = position(player);
        Position destination = new Position(x, y, z);
        long now = now();
        if (state.tracker.hasClimbableAttempt(now) && (player.onClimbable() || hasClimbable(level, destination)))
            state.tracker.allowRealClimbing();
        if (supported(level, player, current)) state.tracker.recordSupported(current, now);
        Position back = state.tracker.setback(destination, now);
        if (back == null || !canStand(level, player, back)) return false;
        correct(player, back, yaw, pitch);
        return true;
    }

    public static void moved(ServerPlayer player)
    {
        FabricBlockPlaceClimbProtection protection = active;
        ServerLevel level = (ServerLevel) ((EntityAccessor) player).griefPrevention$getLevel();
        if (protection == null || !level.getServer().isSameThread() || !protection.enabled(player, level)) return;
        Position current = position(player);
        if (supported(level, player, current)) protection.state(player, level).tracker.recordSupported(current, now());
    }

    public static void clear(ServerPlayer player)
    {
        FabricBlockPlaceClimbProtection protection = active;
        if (protection != null) protection.players.remove(player.getUUID());
    }

    private boolean enabled(ServerPlayer player, ServerLevel level)
    {
        if (!settings.preventBlockPlaceClimbing() || !settings.world().appliesTo(claims.worldKey(level))
                || player.getAbilities().instabuild || player.isSpectator() || player.getAbilities().flying
                || player.isPassenger() || (player.isFallFlying() && !hasClimbableAttempt(player))
                || player.isSwimming() || player.isInWater() || !player.isAlive()
                || (claims.isIgnoringClaims(player.getUUID())
                        && claims.hasPermission(player, FabricPermissionDefaults.IGNORE_CLAIMS)))
        {
            players.remove(player.getUUID());
            return false;
        }
        return true;
    }

    private boolean hasClimbableAttempt(ServerPlayer player)
    {
        State state = players.get(player.getUUID());
        return state != null && state.tracker.hasClimbableAttempt(now());
    }

    private State state(ServerPlayer player, ServerLevel level)
    {
        return players.compute(player.getUUID(), (id, old) -> old != null && old.level == level ? old : new State(level));
    }

    private static boolean supported(ServerLevel level, ServerPlayer player, Position feet)
    {
        if (hasClimbable(level, feet)) return true;
        AABB contact = new AABB(feet.x - 0.299, feet.y - 0.075, feet.z - 0.299,
                feet.x + 0.299, feet.y + 0.001, feet.z + 0.299);
        return loaded(level, contact) && !level.noCollision(player, contact);
    }

    private static boolean hasClimbable(ServerLevel level, Position feet)
    {
        BlockPos pos = BlockPos.containing(feet.x, feet.y, feet.z);
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    private static boolean canStand(ServerLevel level, ServerPlayer player, Position feet)
    {
        AABB body = new AABB(feet.x - 0.299, feet.y + 0.001, feet.z - 0.299,
                feet.x + 0.299, feet.y + player.getBbHeight() - 0.001, feet.z + 0.299);
        return supported(level, player, feet) && loaded(level, body) && level.noCollision(player, body);
    }

    private static boolean loaded(ServerLevel level, AABB bounds)
    {
        for (int x = ((int) Math.floor(bounds.minX)) >> 4; x <= ((int) Math.floor(bounds.maxX)) >> 4; x++)
        {
            for (int z = ((int) Math.floor(bounds.minZ)) >> 4; z <= ((int) Math.floor(bounds.maxZ)) >> 4; z++)
            {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private static void correct(ServerPlayer player, Position back)
    {
        correct(player, back, player.getYRot(), player.getXRot());
    }

    private static void correct(ServerPlayer player, Position back, float yaw, float pitch)
    {
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(back.x, back.y, back.z, yaw, pitch);
    }

    private static Position position(ServerPlayer player) { return new Position(player.getX(), player.getY(), player.getZ()); }
    private static long now() { return System.nanoTime() / 1_000_000; }
    private static final class State
    {
        final ServerLevel level;
        final BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        State(ServerLevel level) { this.level = level; }
    }
}
