package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.UUID;

/**
 * Callback for when a claim is transferred to a new owner.
 */
@FunctionalInterface
public interface ClaimTransferredCallback {
    Event<ClaimTransferredCallback> EVENT = EventFactory.createArrayBacked(
        ClaimTransferredCallback.class,
        listeners -> (claim, newOwner, player) -> {
            for (ClaimTransferredCallback listener : listeners) {
                listener.onClaimTransferred(claim, newOwner, player);
            }
        }
    );

    void onClaimTransferred(
            @org.jetbrains.annotations.NotNull ClaimSnapshot claim,
            @org.jetbrains.annotations.NotNull UUID newOwner,
            @org.jetbrains.annotations.Nullable ServerPlayer player);
}
