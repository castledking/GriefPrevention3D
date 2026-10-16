package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /trapped}, as on Paper: a player stuck in a claim they may not build in is moved to the
 * nearest unclaimed land after standing still for ten seconds.
 */
final class FabricPlayerRescue
{
    /** Paper's PlayerRescueTask runs 200 ticks after the command. */
    private static final long RESCUE_DELAY_TICKS = 200L;
    /** Moving further than this from where /trapped was used calls the rescue off. */
    private static final double MAXIMUM_DRIFT = 3.0D;
    /** Stepping out of claims is a walk west and north; this bounds it should claims chain on forever. */
    private static final int MAXIMUM_CLAIMS_STEPPED_OVER = 10_000;

    private final FabricClaimRepository claims;
    private final FabricDenialFeedback feedback;
    private final FabricSettings settings;
    private final Logger logger;
    private final Map<UUID, Rescue> pending = new HashMap<>();
    private long tick;

    FabricPlayerRescue(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricSettings settings,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.feedback = feedback;
        this.settings = settings;
        this.logger = logger;
    }

    void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(this::rescueDue);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                this.pending.remove(handler.getPlayer().getUUID()));
    }

    /** {@code /trapped} and {@code /claim trapped}. */
    boolean trapped(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.TRAPPED))
        {
            return true;
        }
        // As on Paper, a second /trapped while one is pending is ignored.
        if (this.pending.containsKey(player.getUUID()))
        {
            return true;
        }

        ServerLevel level = (ServerLevel) player.level();
        ClaimSnapshot claim = this.claims.findClaimAt(level, player.blockPosition());
        if (claim == null || this.claims.allows(claim, player, ClaimTrustLevel.BUILD))
        {
            sender.sendError(MessageKey.NOT_TRAPPED_HERE);
            return true;
        }
        // There is no telling where a safe spot is in the nether or the end.
        if (level.dimension() != Level.OVERWORLD
                || (claim.adminClaim() && !this.settings.allowTrappedInAdminClaims()))
        {
            sender.sendError(MessageKey.TRAPPED_WONT_WORK_HERE);
            return true;
        }

        sender.send(TextMode.INSTRUCTION, MessageKey.RESCUE_PENDING);
        this.pending.put(player.getUUID(), new Rescue(level, positionOf(player), this.tick + RESCUE_DELAY_TICKS));
        return true;
    }

    private void rescueDue(@NotNull MinecraftServer server)
    {
        this.tick++;
        if (this.pending.isEmpty())
        {
            return;
        }
        for (Map.Entry<UUID, Rescue> entry : new ArrayList<>(this.pending.entrySet()))
        {
            Rescue rescue = entry.getValue();
            if (rescue.dueTick > this.tick)
            {
                continue;
            }
            this.pending.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null)
            {
                rescue(player, rescue);
            }
        }
    }

    private void rescue(@NotNull ServerPlayer player, @NotNull Rescue rescue)
    {
        if (player.level() != rescue.level || positionOf(player).distanceTo(rescue.from) > MAXIMUM_DRIFT)
        {
            this.feedback.sendError(player, MessageKey.RESCUE_ABORTED_MOVED);
            return;
        }

        BlockPos destination = unclaimedSpot(rescue.level, player.blockPosition());
        if (destination == null)
        {
            this.feedback.sendError(player, MessageKey.TRAPPED_WONT_WORK_HERE);
            return;
        }
        player.stopRiding();
        player.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
        // Logged in case admins want to look into the "trap".
        this.logger.info("Rescued trapped player {} from {} to {}.", FabricPlayerLookup.nameOf(player),
                friendly(rescue.level, BlockPos.containing(rescue.from)), friendly(rescue.level, destination));
    }

    /**
     * Paper's {@code ejectPlayer}: step past the west and north corner of each claim in the way until
     * no claim is underfoot, then stand two blocks above the surface there.
     */
    private @Nullable BlockPos unclaimedSpot(@NotNull ServerLevel level, @NotNull BlockPos from)
    {
        BlockPos candidate = from;
        for (int stepped = 0; stepped < MAXIMUM_CLAIMS_STEPPED_OVER; stepped++)
        {
            ClaimSnapshot claim = this.claims.findClaimAt(level, candidate);
            if (claim == null)
            {
                // Loads, or generates, the chunk, so the surface height is known.
                level.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
                BlockPos destination = new BlockPos(candidate.getX(), surface + 1, candidate.getZ());
                // A claim whose floor lies above where the search walks can still cover the surface.
                claim = this.claims.findClaimAt(level, destination);
                if (claim == null)
                {
                    return destination;
                }
            }
            ClaimBounds bounds = claim.bounds();
            candidate = new BlockPos(bounds.minX() - 1, bounds.minY(), bounds.minZ() - 1);
        }
        return null;
    }

    /** {@code Entity.position()} moved to an interface in 1.21.9, so no one call reaches it on every release. */
    private static @NotNull Vec3 positionOf(@NotNull ServerPlayer player)
    {
        return new Vec3(player.getX(), player.getY(), player.getZ());
    }

    private @NotNull String friendly(@NotNull ServerLevel level, @NotNull BlockPos pos)
    {
        return this.claims.worldKey(level) + ": x" + pos.getX() + ", z" + pos.getZ();
    }

    private static final class Rescue
    {
        private final @NotNull ServerLevel level;
        private final @NotNull Vec3 from;
        private final long dueTick;

        private Rescue(@NotNull ServerLevel level, @NotNull Vec3 from, long dueTick)
        {
            this.level = level;
            this.from = from;
            this.dueTick = dueTick;
        }
    }
}
