package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Handles knockback events using Paper's non-deprecated EntityPushedByEntityAttackEvent.
 * This handler is only registered when running on Paper servers.
 */
public class PaperKnockbackHandler implements Listener {

    private final @NotNull DataStore dataStore;
    private final @NotNull GriefPrevention instance;

    public PaperKnockbackHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin) {
        this.dataStore = dataStore;
        this.instance = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityPushedByEntityAttack(@NotNull io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
        handleKnockback(event.getEntity(), event.getPushedBy(), event);
    }

    /**
     * Handle wind charge knockback protection for players in PVP-protected claims.
     */
    private void handleKnockback(@NotNull Entity entity, Entity source, @NotNull io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
        // Only handle knockback on players
        if (!(entity instanceof Player defender))
            return;

        if (source == null)
            return;

        // Check if this is explosion knockback (wind charges use EXPLOSION cause)
        // Paper's getPushedBy() returns the player who threw the wind charge, not the wind charge itself
        String sourceTypeName = source.getType().name();
        String causeName = event.getCause().name();
        
        // Wind charge knockback from players: cause is EXPLOSION and source is the player
        boolean isWindChargeKnockback = causeName.equals("EXPLOSION") && source instanceof Player;
        
        // Also check if source is directly a wind charge (for breeze wind charges)
        if (!isWindChargeKnockback && !sourceTypeName.contains("WIND_CHARGE") && !sourceTypeName.equals("BREEZE_WIND_CHARGE")) {
            return;
        }

        // Get the attacker - for player-thrown wind charges, source IS the player
        Player attacker = null;
        if (source instanceof Player player) {
            attacker = player;
        } else if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
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

    /**
     * Check if the Paper EntityPushedByEntityAttackEvent is available.
     * @return true if running on Paper with the event available
     */
    public static boolean isPaperEventAvailable() {
        try {
            Class.forName("io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
