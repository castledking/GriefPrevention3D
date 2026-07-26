package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidTriggerEvent;

public final class RaidTriggerEventHandler implements Listener {

    private final DataStore dataStore;
    private final GriefPrevention instance;

    public RaidTriggerEventHandler(DataStore dataStore, GriefPrevention instance) {
        this.dataStore = dataStore;
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTriggerRaid(RaidTriggerEvent event) {
        if (!instance.config_claims_raidTriggersRequireBuildTrust)
            return;

        Player player = event.getPlayer();
        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());

        // The raid centers on the village, not the player; checking only the player's
        // location lets them step outside the claim after gaining Raid Omen to bypass
        // the protection (GriefPrevention#2622).
        Claim claim = this.dataStore.getClaimAt(event.getRaid().getLocation(), false, playerData.lastClaim);
        if (claim == null)
            claim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
        if (claim == null)
            return;

        playerData.lastClaim = claim;
        if (claim.checkPermission(player, ClaimPermission.Build, event) == null)
            return;

        event.setCancelled(true);
    }
}
