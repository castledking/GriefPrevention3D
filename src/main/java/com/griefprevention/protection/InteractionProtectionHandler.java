package com.griefprevention.protection;

import com.griefprevention.compat.BlockDataCompat;
import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Event handler for special interaction protection cases.
 */
public class InteractionProtectionHandler implements Listener
{

    /**
     * Special case to handle End portal frame interactions before the portal is created,
     * ensuring build permission checks happen prior to vanilla portal creation logic.
     *
     * @param event the player interaction event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEndPortalFrameInteract(@NotNull PlayerInteractEvent event)
    {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check for END_PORTAL_FRAME (1.13+) or ENDER_PORTAL_FRAME (1.8.8-1.12)
        Material blockType = block.getType();
        boolean isEndPortalFrame = false;
        try {
            isEndPortalFrame = blockType == Material.END_PORTAL_FRAME;
        } catch (NoSuchFieldError e) {
            // 1.8.8: use ENDER_PORTAL_FRAME
            try {
                isEndPortalFrame = blockType == Material.valueOf("ENDER_PORTAL_FRAME");
            } catch (IllegalArgumentException e2) {
                return;
            }
        }
        if (!isEndPortalFrame) return;

        ItemStack item = event.getItem();
        // ENDER_EYE was named EYE_OF_ENDER before 1.13
        if (item == null) return;
        if (!CompatUtil.isMaterial(item.getType(), "ENDER_EYE")
                && !CompatUtil.isMaterial(item.getType(), "EYE_OF_ENDER")) return;

        // Null covers modded block data implementations and any state we can't read
        Boolean hasEye = BlockDataCompat.endPortalFrameHasEye(block);
        if (hasEye == null || hasEye) return;

        Player player = event.getPlayer();
        Supplier<String> noBuildReason = ProtectionHelper.checkPermission(player, block.getLocation(), ClaimPermission.Build, event);
        if (noBuildReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
        }
    }

}
