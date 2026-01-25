package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles knockback events using Bukkit's EntityKnockbackByEntityEvent.
 * This handler is only registered when NOT running on Paper servers.
 * On Paper, the PaperKnockbackHandler is used instead to avoid deprecation warnings.
 */
public class SpigotKnockbackHandler implements Listener {

    private final @NotNull DataStore dataStore;
    private final @NotNull GriefPrevention instance;

    public SpigotKnockbackHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin) {
        this.dataStore = dataStore;
        this.instance = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityKnockbackByEntity(@NotNull EntityKnockbackByEntityEvent event) {
        // Only handle wind charge knockback on players
        if (!(event.getEntity() instanceof Player defender))
            return;

        Entity source = event.getSourceEntity();
        if (source == null)
            return;

        // Check if the source is a wind charge
        String sourceTypeName = source.getType().name();
        
        if (!sourceTypeName.contains("WIND_CHARGE") && !sourceTypeName.equals("BREEZE_WIND_CHARGE")) {
            return;
        }

        // Get the player who threw the wind charge
        Player attacker = null;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }

        // Allow self-knockback (e.g., for movement tricks)
        if (attacker == null || attacker == defender) {
            return;
        }

        // Only protect when PVP rules are enabled for this world
        if (!instance.pvpRulesApply(defender.getWorld())) {
            return;
        }

        // Check if defender is in a PVP-protected claim
        PlayerData defenderData = dataStore.getPlayerData(defender.getUniqueId());
        Claim claim = dataStore.getClaimAt(defender.getLocation(), false, defenderData.lastClaim);
        if (claim != null && instance.claimIsPvPSafeZone(claim)) {
            defenderData.lastClaim = claim;
            event.setCancelled(true);
            GriefPrevention.sendRateLimitedErrorMessage(attacker, Messages.CantFightWhileImmune);
        }
    }
}
