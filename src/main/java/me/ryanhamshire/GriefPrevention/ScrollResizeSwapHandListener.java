package me.ryanhamshire.GriefPrevention;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cancels a {@code /scrollresize} session on the swap-hands key. Kept separate from {@link ScrollResizeHandler}
 * because {@link PlayerSwapHandItemsEvent} doesn't exist before 1.9; it is only registered when the event does.
 */
public class ScrollResizeSwapHandListener implements Listener
{
    private final @NotNull ScrollResizeHandler handler;

    public ScrollResizeSwapHandListener(@NotNull ScrollResizeHandler handler)
    {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(@NotNull PlayerSwapHandItemsEvent event)
    {
        if (this.handler.cancelFromKey(event.getPlayer())) event.setCancelled(true);
    }
}
