package com.griefprevention.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Charges and refunds command fees through Vault. Kept apart from the plugin class so Vault types
 * only load when a paid command actually runs, which matters on servers without Vault.
 */
public final class VaultFees {

    private VaultFees() {}

    /** The outcome of charging a fee. */
    public enum Status {
        PAID,
        NO_ECONOMY,
        NOT_ENOUGH_MONEY
    }

    /** A charge attempt, with the amounts formatted by the economy for messages. */
    public static final class Charge {
        private final @NotNull Status status;
        private final @NotNull String formattedFee;
        private final @NotNull String formattedBalance;

        private Charge(@NotNull Status status, @NotNull String formattedFee, @NotNull String formattedBalance) {
            this.status = status;
            this.formattedFee = formattedFee;
            this.formattedBalance = formattedBalance;
        }

        public @NotNull Status status() {
            return status;
        }

        public @NotNull String formattedFee() {
            return formattedFee;
        }

        public @NotNull String formattedBalance() {
            return formattedBalance;
        }
    }

    /**
     * Withdraws {@code fee} from the player's balance.
     *
     * @return {@link Status#PAID} once the money is taken; otherwise why it was not
     */
    public static @NotNull Charge withdraw(@NotNull Server server, @NotNull OfflinePlayer player, double fee) {
        net.milkbowl.vault.economy.Economy economy = resolveEconomy(server);
        if (economy == null) {
            return new Charge(Status.NO_ECONOMY, VaultEconomyFormatting.format(server, fee), "");
        }

        double balance = economy.getBalance(player);
        String formattedFee = economy.format(fee);
        String formattedBalance = economy.format(balance);
        if (balance < fee || !economy.withdrawPlayer(player, fee).transactionSuccess()) {
            return new Charge(Status.NOT_ENOUGH_MONEY, formattedFee, formattedBalance);
        }
        return new Charge(Status.PAID, formattedFee, formattedBalance);
    }

    /** Gives a fee back, for when the paid action could not go ahead after all. */
    public static void refund(@NotNull Server server, @NotNull OfflinePlayer player, double fee) {
        net.milkbowl.vault.economy.Economy economy = resolveEconomy(server);
        if (economy != null) {
            economy.depositPlayer(player, fee);
        }
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
