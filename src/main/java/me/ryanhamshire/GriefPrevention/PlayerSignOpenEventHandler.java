package me.ryanhamshire.GriefPrevention;

import com.griefprevention.protection.ProtectionHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSignOpenEvent;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class PlayerSignOpenEventHandler implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    void onPlayerSignOpen(@NotNull PlayerSignOpenEvent event) {
        if (event.getCause() != PlayerSignOpenEvent.Cause.INTERACT
                || event.getSign().getBlock().getType() != event.getSign().getType()) {
            // If the sign is not opened by interaction or the corresponding block is no longer a sign,
            // it is either the initial sign placement or another plugin is at work. Do not interfere.
            return;
        }

        Player player = event.getPlayer();
        Supplier<String> denial = ProtectionHelper.checkPermission(player, event.getSign().getLocation(),
                ClaimPermission.Build, event);

        if (denial == null) {
            return;
        }

        GriefPrevention.sendRateLimitedErrorMessage(player, denial.get());
        event.setCancelled(true);
    }
}
