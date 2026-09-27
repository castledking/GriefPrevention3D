package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback for when a claim is modified (bounds changed).
 */
@FunctionalInterface
public interface ClaimModifiedCallback {
    Event<ClaimModifiedCallback> EVENT = EventFactory.createArrayBacked(
        ClaimModifiedCallback.class,
        listeners -> (oldClaim, newClaim, player) -> {
            for (ClaimModifiedCallback listener : listeners) {
                listener.onClaimModified(oldClaim, newClaim, player);
            }
        }
    );

    void onClaimModified(
            @org.jetbrains.annotations.NotNull ClaimSnapshot oldClaim,
            @org.jetbrains.annotations.NotNull ClaimSnapshot newClaim,
            @org.jetbrains.annotations.Nullable ServerPlayer player);
}
