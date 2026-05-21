package me.ryanhamshire.GriefPrevention;

import com.griefprevention.protection.ProtectionHelper;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;

import java.util.function.Supplier;

public final class EntityMountEventHandler implements Listener {

    private final DataStore dataStore;
    private final GriefPrevention instance;

    public EntityMountEventHandler(DataStore dataStore, GriefPrevention instance) {
        this.dataStore = dataStore;
        this.instance = instance;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onMountRequiresAccessTrust(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();

        Entity mount = event.getMount();
        if (!(mount instanceof Animals)) {
            return;
        }

        if (!instance.claimsEnabledForWorld(mount.getWorld())) {
            return;
        }

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.ignoreClaims) {
            return;
        }

        Claim claim = this.dataStore.getClaimAt(mount.getLocation(), false, playerData.lastClaim);
        if (claim == null) {
            return;
        }

        playerData.lastClaim = claim;
        Supplier<String> denial = claim.checkPermission(player, ClaimPermission.Access, event);
        if (denial != null) {
            event.setCancelled(true);
            GriefPrevention.sendRateLimitedErrorMessage(player, denial.get());
        }
    }
}
