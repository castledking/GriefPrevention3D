package com.griefprevention.fabric;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** A Fabric economy backend for claim-block purchases, sales and paid transfers. */
interface FabricEconomyProvider
{
    @NotNull String name();

    long balance(@NotNull ServerPlayer player);

    boolean withdraw(@NotNull ServerPlayer player, long amount);

    void deposit(@NotNull UUID playerId, long amount);

    @NotNull String format(long amount);

    @NotNull String formatChat(long amount);
}
