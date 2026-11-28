package com.griefprevention.fabric;

import com.reazip.economycraft.api.v1.EconomyCraftApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Locates an optional Fabric/NeoForge economy provider (EconomyCraft or Allium) at runtime. */
final class FabricEconomy
{
    private static final String ECONOMYCRAFT_MOD_ID = "economycraft";
    private static final String ALLIUM_MOD_ID = "allium";
    private static final String ALLIUM_ECONOMY_API = "codes.castled.allium.portable.AlliumEconomyApi";

    private final @NotNull Logger logger;
    private volatile boolean loggedEconomyCraftUnavailable;
    private volatile boolean loggedAlliumUnavailable;

    FabricEconomy(@NotNull Logger logger)
    {
        this.logger = logger;
    }

    @Nullable FabricEconomyProvider provider(@NotNull MinecraftServer server)
    {
        if (ModPlatform.current().isModLoaded(ECONOMYCRAFT_MOD_ID))
        {
            try
            {
                return new EconomyCraftProvider(server);
            }
            catch (Throwable throwable)
            {
                if (!this.loggedEconomyCraftUnavailable)
                {
                    this.loggedEconomyCraftUnavailable = true;
                    this.logger.warn("EconomyCraft is present but its API could not be used for claim-block economy: {}",
                            throwable.toString());
                }
            }
        }
        if (ModPlatform.current().isModLoaded(ALLIUM_MOD_ID))
        {
            try
            {
                return new AlliumProvider(server);
            }
            catch (Throwable throwable)
            {
                if (!this.loggedAlliumUnavailable)
                {
                    this.loggedAlliumUnavailable = true;
                    this.logger.warn("Allium is present but its native economy API could not be used for claim-block economy: {}",
                            throwable.toString());
                }
            }
        }
        return null;
    }

    private static final class EconomyCraftProvider implements FabricEconomyProvider
    {
        private final @NotNull EconomyCraftApi api;

        private EconomyCraftProvider(@NotNull MinecraftServer server)
        {
            this.api = EconomyCraftApi.get(server);
        }

        @Override
        public @NotNull String name()
        {
            return "EconomyCraft";
        }

        @Override
        public long balance(@NotNull ServerPlayer player)
        {
            return this.api.balances().getBalance(player.getUUID());
        }

        @Override
        public boolean withdraw(@NotNull ServerPlayer player, long amount)
        {
            return this.api.balances().removeMoney(player.getUUID(), amount).successful();
        }

        @Override
        public void deposit(@NotNull UUID playerId, long amount)
        {
            this.api.balances().addMoney(playerId, amount);
        }

        @Override
        public @NotNull String format(long amount)
        {
            return this.api.formatMoney(amount);
        }

        @Override
        public @NotNull String formatChat(long amount)
        {
            try
            {
                return this.api.formatMoneyChat(amount);
            }
            catch (Throwable ignored)
            {
                return this.api.formatMoney(amount);
            }
        }
    }

    private static final class AlliumProvider implements FabricEconomyProvider
    {
        private final @NotNull MinecraftServer server;
        private final @NotNull Path worldRoot;
        private final @NotNull Method getWholeBalance;
        private final @NotNull Method withdrawWhole;
        private final @NotNull Method depositWhole;
        private final @NotNull Method formatWhole;

        private AlliumProvider(@NotNull MinecraftServer server) throws ReflectiveOperationException
        {
            this.server = server;
            this.worldRoot = server.getWorldPath(LevelResource.ROOT);
            Class<?> apiClass = Class.forName(ALLIUM_ECONOMY_API);
            this.getWholeBalance = apiClass.getMethod("getWholeBalance", Path.class, UUID.class);
            this.withdrawWhole = apiClass.getMethod("withdrawWhole", Path.class, UUID.class, String.class, long.class);
            this.depositWhole = apiClass.getMethod("depositWhole", Path.class, UUID.class, String.class, long.class);
            this.formatWhole = apiClass.getMethod("formatWhole", long.class);
        }

        @Override
        public @NotNull String name()
        {
            return "Allium";
        }

        @Override
        public long balance(@NotNull ServerPlayer player)
        {
            try
            {
                Object raw = this.getWholeBalance.invoke(null, this.worldRoot, player.getUUID());
                return raw instanceof Number ? ((Number) raw).longValue() : 0L;
            }
            catch (ReflectiveOperationException exception)
            {
                return 0L;
            }
        }

        @Override
        public boolean withdraw(@NotNull ServerPlayer player, long amount)
        {
            try
            {
                Object raw = this.withdrawWhole.invoke(null, this.worldRoot, player.getUUID(),
                        player.getScoreboardName(), amount);
                return Boolean.TRUE.equals(raw);
            }
            catch (ReflectiveOperationException exception)
            {
                return false;
            }
        }

        @Override
        public void deposit(@NotNull UUID playerId, long amount)
        {
            try
            {
                ServerPlayer online = this.server.getPlayerList().getPlayer(playerId);
                String playerName = online != null ? online.getScoreboardName() : null;
                this.depositWhole.invoke(null, this.worldRoot, playerId, playerName, amount);
            }
            catch (ReflectiveOperationException ignored)
            {
            }
        }

        @Override
        public @NotNull String format(long amount)
        {
            try
            {
                Object raw = this.formatWhole.invoke(null, amount);
                if (raw != null)
                {
                    return raw.toString();
                }
            }
            catch (ReflectiveOperationException ignored)
            {
            }
            return String.format(Locale.US, "$%,d.00", amount);
        }

        @Override
        public @NotNull String formatChat(long amount)
        {
            return format(amount);
        }
    }
}
