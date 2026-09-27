package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback for when a claim is deleted.
 */
@FunctionalInterface
public interface ClaimDeletedCallback {
    Event<ClaimDeletedCallback> EVENT = EventFactory.createArrayBacked(
        ClaimDeletedCallback.class,
        listeners -> (claim, player) -> {
            for (ClaimDeletedCallback listener : listeners) {
                listener.onClaimDeleted(claim, player);
            }
        }
    );

    void onClaimDeleted(@org.jetbrains.annotations.NotNull ClaimSnapshot claim, @org.jetbrains.annotations.Nullable ServerPlayer player);
}
