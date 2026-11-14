package com.griefprevention.fabric;

import com.reazip.economycraft.api.v1.EconomyCraftApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/** Locates an optional Fabric economy provider (currently EconomyCraft) at runtime. */
final class FabricEconomy
{
    private static final String ECONOMYCRAFT_MOD_ID = "economycraft";

    private final @NotNull Logger logger;
    private volatile boolean loggedUnavailable;

    FabricEconomy(@NotNull Logger logger)
    {
        this.logger = logger;
    }

    @Nullable FabricEconomyProvider provider(@NotNull MinecraftServer server)
    {
        if (FabricLoader.getInstance().isModLoaded(ECONOMYCRAFT_MOD_ID))
        {
            try
            {
                return new EconomyCraftProvider(server);
            }
            catch (Throwable throwable)
            {
                if (!this.loggedUnavailable)
                {
                    this.loggedUnavailable = true;
                    this.logger.warn("EconomyCraft is present but its API could not be used for claim-block economy: {}",
                            throwable.toString());
                }
                return null;
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
}
