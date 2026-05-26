package me.ryanhamshire.GriefPrevention.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player starts a subdivision creation session with the claim tool.
 */
public class StartSubdivideClaimCreationEvent extends StartClaimCreationEvent
{
    private static final HandlerList HANDLERS = new HandlerList();
    private final @NotNull Claim parent;

    public StartSubdivideClaimCreationEvent(
            @NotNull Player player,
            @NotNull Block clickedBlock,
            @NotNull Claim parent)
    {
        super(player, clickedBlock);
        this.parent = parent;
    }

    public final @NotNull Claim getParent()
    {
        return this.parent;
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
