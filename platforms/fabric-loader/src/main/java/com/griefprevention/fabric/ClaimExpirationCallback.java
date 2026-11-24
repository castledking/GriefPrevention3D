package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback before a claim expires because its owner stopped playing, as Paper's cancellable
 * {@code ClaimExpirationEvent}. A listener that returns false keeps the claim.
 */
@FunctionalInterface
public interface ClaimExpirationCallback {
    Event<ClaimExpirationCallback> EVENT = EventFactory.createArrayBacked(
        ClaimExpirationCallback.class,
        listeners -> claim -> {
            for (ClaimExpirationCallback listener : listeners) {
                if (!listener.allowExpiration(claim)) {
                    return false;
                }
            }
            return true;
        }
    );

    boolean allowExpiration(@org.jetbrains.annotations.NotNull ClaimSnapshot claim);
}
