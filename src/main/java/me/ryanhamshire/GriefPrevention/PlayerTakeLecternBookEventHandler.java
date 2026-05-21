package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;

import java.util.function.Supplier;

public final class PlayerTakeLecternBookEventHandler implements Listener {

    private final DataStore dataStore;

    public PlayerTakeLecternBookEventHandler(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    void onTakeBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        Claim claim = this.dataStore.getClaimAt(event.getLectern().getLocation(), false, playerData.lastClaim);
        if (claim != null) {
            playerData.lastClaim = claim;
            Supplier<String> noContainerReason = claim.checkPermission(player, ClaimPermission.Container, event);
            if (noContainerReason != null) {
                event.setCancelled(true);
                player.closeInventory();
                GriefPrevention.sendRateLimitedErrorMessage(player, noContainerReason.get());
            }
        }
    }
}
