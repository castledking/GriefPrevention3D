package com.griefprevention.fabric;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.NotNull;

/**
 * NeoForge 1.21.x block-break hook using {@link BlockEvent.BreakEvent}.
 */
final class NeoForgeBlockBreakHook
{
    private NeoForgeBlockBreakHook()
    {
    }

    static void onBlockBreakBefore(@NotNull ModPlatform.BlockBreakBeforeListener listener)
    {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (!(event.getLevel() instanceof Level level))
            {
                return;
            }
            if (!listener.allowBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(),
                    level.getBlockEntity(event.getPos())))
            {
                event.setCanceled(true);
            }
        });
    }

    static void onBlockBreakAfter(@NotNull ModPlatform.BlockBreakListener listener)
    {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (BlockEvent.BreakEvent event) -> {
            if (!(event.getLevel() instanceof Level level))
            {
                return;
            }
            listener.onBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(),
                    level.getBlockEntity(event.getPos()));
        });
    }

    static void onBlockBreakCanceled(@NotNull ModPlatform.BlockBreakListener listener)
    {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (BlockEvent.BreakEvent event) -> {
            if (!event.isCanceled() || !(event.getLevel() instanceof Level level))
            {
                return;
            }
            listener.onBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(),
                    level.getBlockEntity(event.getPos()));
        });
    }
}
