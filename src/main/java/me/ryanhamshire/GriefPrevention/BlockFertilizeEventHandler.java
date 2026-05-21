package me.ryanhamshire.GriefPrevention;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.jetbrains.annotations.NotNull;

public final class BlockFertilizeEventHandler implements Listener {

    private final BlockEventHandler blockEventHandler;

    public BlockFertilizeEventHandler(BlockEventHandler blockEventHandler) {
        this.blockEventHandler = blockEventHandler;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onBlockFertilize(@NotNull BlockFertilizeEvent event) {
        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        // Trees are handled by the StructureGrowEvent handler.
        if (BlockEventHandler.isSapling(event.getBlock().getType())) return;

        this.blockEventHandler.onBlockFertilize(event);
    }
}
