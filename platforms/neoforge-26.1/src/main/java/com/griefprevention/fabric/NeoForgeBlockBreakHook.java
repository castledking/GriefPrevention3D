package com.griefprevention.fabric;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jetbrains.annotations.NotNull;

/**
 * NeoForge 26.x block-break hook using {@link BreakBlockEvent}.
 */
final class NeoForgeBlockBreakHook
{
    private NeoForgeBlockBreakHook()
    {
    }

    static void onBlockBreakBefore(@NotNull ModPlatform.BlockBreakBeforeListener listener)
    {
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
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
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (BreakBlockEvent event) -> {
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
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (BreakBlockEvent event) -> {
            if (!event.isCanceled() || !(event.getLevel() instanceof Level level))
            {
                return;
            }
            listener.onBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(),
                    level.getBlockEntity(event.getPos()));
        });
    }
}
