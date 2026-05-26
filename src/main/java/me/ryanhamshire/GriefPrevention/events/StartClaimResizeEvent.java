package me.ryanhamshire.GriefPrevention.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player starts a claim resize session with the claim tool.
 *
 * <p>If cancelled, the resize corner is not selected.
 */
public class StartClaimResizeEvent extends PlayerEvent implements Cancellable
{
    private static final HandlerList HANDLERS = new HandlerList();
    private final @NotNull Claim claim;
    private final @NotNull Block clickedBlock;
    private boolean cancelled = false;

    public StartClaimResizeEvent(
            @NotNull Player player,
            @NotNull Claim claim,
            @NotNull Block clickedBlock)
    {
        super(player);
        this.claim = claim;
        this.clickedBlock = clickedBlock;
    }

    public final @NotNull Claim getClaim()
    {
        return this.claim;
    }

    public final @NotNull Block getClickedBlock()
    {
        return this.clickedBlock;
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
