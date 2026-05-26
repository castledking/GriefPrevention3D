package me.ryanhamshire.GriefPrevention.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called before a claim is deleted.
 *
 * <p>If cancelled, the claim deletion is prevented.
 */
public class PreDeleteClaimEvent extends ClaimEvent implements Cancellable
{
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    public PreDeleteClaimEvent(@NotNull Claim claim)
    {
        super(claim);
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

    @Override
    public boolean isCancelled()
    {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled)
    {
        this.cancelled = cancelled;
    }
}
