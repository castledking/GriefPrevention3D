package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player starts a claim creation session with the claim tool.
 *
 * <p>If cancelled, the first claim corner is not stored.
 */
public class StartClaimCreationEvent extends PlayerEvent implements Cancellable
{
    private static final HandlerList HANDLERS = new HandlerList();
    private final @NotNull Block clickedBlock;
    private boolean cancelled = false;

    public StartClaimCreationEvent(@NotNull Player player, @NotNull Block clickedBlock)
    {
        super(player);
        this.clickedBlock = clickedBlock;
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
