package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

public final class ItemMergeEventHandler implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onItemMerge(ItemMergeEvent event)
    {
        Item item = event.getEntity();
        List<MetadataValue> data = item.getMetadata("GP_ITEMOWNER");
        event.setCancelled(!data.isEmpty());
    }
}
