package me.ryanhamshire.GriefPrevention;

import com.griefprevention.protection.ProtectionHelper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class CauldronLevelChangeEventHandler implements Listener {

    @EventHandler
    public void onCauldron(@NotNull CauldronLevelChangeEvent event) {
        // Don't track in worlds where claims are not enabled.
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        Supplier<String> noBuildReason = ProtectionHelper.checkPermission(
                player,
                player.getLocation(),
                ClaimPermission.Build,
                event);
        if (noBuildReason != null) {
            event.setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
        }
    }
}
