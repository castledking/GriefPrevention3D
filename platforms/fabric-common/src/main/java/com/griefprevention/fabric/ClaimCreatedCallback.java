package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback for when a claim is created.
 */
@FunctionalInterface
public interface ClaimCreatedCallback {
    Event<ClaimCreatedCallback> EVENT = EventFactory.createArrayBacked(
        ClaimCreatedCallback.class,
        listeners -> (claim, player) -> {
            for (ClaimCreatedCallback listener : listeners) {
                listener.onClaimCreated(claim, player);
            }
        }
    );

    void onClaimCreated(@org.jetbrains.annotations.NotNull ClaimSnapshot claim, @org.jetbrains.annotations.Nullable ServerPlayer player);
}
