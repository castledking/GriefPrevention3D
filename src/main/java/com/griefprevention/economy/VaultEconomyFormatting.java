package com.griefprevention.economy;

import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Formats currency amounts using the active Vault economy provider when available.
 */
public final class VaultEconomyFormatting {

    private VaultEconomyFormatting() {}

    public static @NotNull String format(@NotNull Server server, double amount) {
        net.milkbowl.vault.economy.Economy economy = resolveEconomy(server);
        if (economy != null) {
            return economy.format(amount);
        }
        return fallbackFormat(amount);
    }

    public static @NotNull String format(@NotNull net.milkbowl.vault.economy.Economy economy, double amount) {
        return economy.format(amount);
    }

    private static @NotNull String fallbackFormat(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private static @Nullable net.milkbowl.vault.economy.Economy resolveEconomy(@NotNull Server server) {
        try {
            if (server.getPluginManager().getPlugin("Vault") == null) {
                return null;
            }

            @SuppressWarnings("null")
            org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
                server.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            return registration != null ? registration.getProvider() : null;
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
    }
}
