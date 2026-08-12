package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBlockAccrual;
import com.griefprevention.claims.ClaimBlockSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Delivers and lifecycle-persists Bukkit-compatible playtime claim-block accrual. */
final class FabricClaimBlockAccrual
{
    static final long DELIVERY_INTERVAL_TICKS = 20L * 60L * 10L;
    private static final String ACCRUAL_PERMISSION = "griefprevention.accruals";
    private static final String AFK_BYPASS_PERMISSION = "griefprevention.accruals.afkbypass";

    private final @NotNull FabricClaimBlockService claimBlocks;
    private final @NotNull Logger logger;
    private final boolean scheduledAtStartup;
    private final @NotNull Map<UUID, PlayerPosition> lastCheckPositions = new LinkedHashMap<>();
    private long ticksUntilDelivery = DELIVERY_INTERVAL_TICKS;

    FabricClaimBlockAccrual(
            @NotNull FabricClaimBlockService claimBlocks,
            @NotNull Logger logger)
    {
        this.claimBlocks = claimBlocks;
        this.logger = logger;
        this.scheduledAtStartup = claimBlocks.settings().blocksAccruedPerHour() > 0;
    }

    void register()
    {
        if (this.scheduledAtStartup)
        {
            ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
            this.logger.info(
                    "Fabric playtime claim-block accrual scheduled every 10 minutes at {} blocks/hour.",
                    this.claimBlocks.settings().blocksAccruedPerHour()
            );
        }
        else
        {
            this.logger.info("Fabric playtime claim-block accrual is disabled at startup.");
        }

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            this.lastCheckPositions.remove(player.getUUID());
            flush(player.getUUID(), "disconnect");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> flushAll("server shutdown"));
    }

    private void onEndServerTick(@NotNull MinecraftServer server)
    {
        if (!advanceTick())
        {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            deliver(player);
        }
    }

    private void deliver(@NotNull ServerPlayer player)
    {
        UUID playerId = player.getUUID();
        ClaimBlockSettings settings = this.claimBlocks.settings();
        PlayerPosition current = PlayerPosition.from(player);
        PlayerPosition previous = this.lastCheckPositions.put(playerId, current);
        int thresholdSquared = ClaimBlockAccrual.idleThresholdSquared(
                settings.accruedIdleThreshold()
        );
        boolean detectedIdle = isIdle(
                player.isPassenger(),
                !player.level().getFluidState(player.blockPosition()).isEmpty(),
                previous,
                current,
                thresholdSquared
        );

        boolean operatorDefault = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        boolean bypassesAfk = this.claimBlocks.permissionOrDefault(
                playerId,
                AFK_BYPASS_PERMISSION,
                operatorDefault
        );
        boolean idle = detectedIdle && !bypassesAfk;
        if (!this.claimBlocks.permissionOrDefault(playerId, ACCRUAL_PERMISSION, true))
        {
            return;
        }

        int blocks = ClaimBlockAccrual.blocksForDelivery(
                settings.blocksAccruedPerHour(),
                idle,
                settings.accruedIdlePercent()
        );
        try
        {
            this.claimBlocks.accrueBlocks(playerId, blocks);
        }
        catch (IOException exception)
        {
            this.logger.error(
                    "Could not safely persist Fabric claim-block accrual for {} during scheduled delivery; "
                            + "it will be retried at disconnect or shutdown.",
                    playerId,
                    exception
            );
        }
    }

    private void flush(@NotNull UUID playerId, @NotNull String reason)
    {
        try
        {
            this.claimBlocks.flushAccrual(playerId);
        }
        catch (IOException exception)
        {
            this.logger.error(
                    "Could not safely persist Fabric claim-block accrual for {} during {}.",
                    playerId,
                    reason,
                    exception
            );
        }
    }

    private void flushAll(@NotNull String reason)
    {
        for (UUID playerId : this.claimBlocks.playersWithAccrualState())
        {
            flush(playerId, reason);
        }
        this.lastCheckPositions.clear();
    }

    boolean advanceTick()
    {
        if (--this.ticksUntilDelivery > 0L)
        {
            return false;
        }
        this.ticksUntilDelivery = DELIVERY_INTERVAL_TICKS;
        return true;
    }

    static boolean isIdle(
            boolean passenger,
            boolean inLiquid,
            @Nullable PlayerPosition previous,
            @NotNull PlayerPosition current,
            int thresholdSquared)
    {
        if (passenger || inLiquid)
        {
            return true;
        }
        return previous != null
                && previous.dimension.equals(current.dimension)
                && previous.distanceSquared(current) <= thresholdSquared;
    }

    static final class PlayerPosition
    {
        private final @NotNull String dimension;
        private final double x;
        private final double y;
        private final double z;

        PlayerPosition(@NotNull String dimension, double x, double y, double z)
        {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static @NotNull PlayerPosition from(@NotNull ServerPlayer player)
        {
            return new PlayerPosition(
                    player.level().dimension().identifier().toString(),
                    player.getX(),
                    player.getY(),
                    player.getZ()
            );
        }

        private double distanceSquared(@NotNull PlayerPosition other)
        {
            double deltaX = this.x - other.x;
            double deltaY = this.y - other.y;
            double deltaZ = this.z - other.z;
            return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        }
    }
}
