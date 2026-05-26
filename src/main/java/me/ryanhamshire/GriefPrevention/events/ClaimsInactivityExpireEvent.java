package me.ryanhamshire.GriefPrevention.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Called when a player's claims are being removed due to inactivity.
 *
 * <p>Per-claim cancellation remains available through {@link ClaimExpirationEvent}.
 */
public class ClaimsInactivityExpireEvent extends Event
{
    private static final HandlerList HANDLERS = new HandlerList();
    private final @Nullable UUID playerUUID;
    private final @NotNull List<Claim> expiredClaims;

    public ClaimsInactivityExpireEvent(@Nullable UUID playerUUID, @NotNull List<Claim> expiredClaims)
    {
        this.playerUUID = playerUUID;
        this.expiredClaims = Collections.unmodifiableList(new ArrayList<>(expiredClaims));
    }

    public @Nullable UUID getPlayerUUID()
    {
        return this.playerUUID;
    }

    public @NotNull List<Claim> getExpiredClaims()
    {
        return this.expiredClaims;
    }

    public int getTotalClaimBlocksFreed()
    {
        int total = 0;
        for (Claim claim : this.expiredClaims)
        {
            total += claim.getArea();
        }
        return total;
    }

    public static HandlerList getHandlerList()
    {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers()
    {
        return HANDLERS;
    }
}
