package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** Ends a siege when either participant leaves the fight. */
final class SiegeCheckupTask implements Runnable {
    private final SiegeData siege;

    SiegeCheckupTask(SiegeData siege) {
        this.siege = siege;
    }

    @Override
    public void run() {
        if (siege.ended) return;

        // This first stage runs on the attacker's entity scheduler. Capture only
        // attacker-owned state, then evaluate the defender on their scheduler.
        final Player attacker = siege.attacker;
        final boolean attackerAvailable = attacker.isOnline() && !attacker.isDead();
        final Location attackerLocation = attackerAvailable ? attacker.getLocation().clone() : null;
        siege.checkupTask = SchedulerUtil.runLaterEntity(
                GriefPrevention.instance, siege.defender,
                () -> evaluate(attackerAvailable, attackerLocation), 1L);
    }

    private void evaluate(boolean attackerAvailable, Location attackerLocation) {
        if (siege.ended) return;

        DataStore dataStore = GriefPrevention.instance.dataStore;
        Player defender = siege.defender;
        Player attacker = siege.attacker;
        if (!defender.isOnline() || defender.isDead()) {
            dataStore.endSiege(siege, attacker.getName(), defender.getName(), null);
            return;
        }
        if (!attackerAvailable || attackerLocation == null) {
            dataStore.endSiege(siege, defender.getName(), attacker.getName(), null);
            return;
        }

        Claim defenderClaim = dataStore.getClaimAt(defender.getLocation(), false, null);
        if (defenderClaim != null && !siege.claims.contains(defenderClaim)) {
            Supplier<String> noAccess = defenderClaim.checkPermission(defender, ClaimPermission.Access, null);
            if (defenderClaim.canSiege(defender) && noAccess == null) {
                siege.claims.add(defenderClaim);
                defenderClaim.siegeData = siege;
            }
        }

        Location defenderLocation = defender.getLocation();
        boolean attackerRemains = playerRemains(attackerLocation);
        boolean defenderRemains = playerRemains(defenderLocation);
        if (attackerRemains && defenderRemains) {
            scheduleAnotherCheck();
        } else if (attackerRemains) {
            dataStore.endSiege(siege, attacker.getName(), defender.getName(), null);
        } else if (defenderRemains) {
            dataStore.endSiege(siege, defender.getName(), attacker.getName(), null);
        } else if (attackerLocation.getWorld().equals(defenderLocation.getWorld())
                && attackerLocation.distanceSquared(defenderLocation) < 2500) {
            scheduleAnotherCheck();
        } else {
            dataStore.endSiege(siege, attacker.getName(), defender.getName(), null);
        }
    }

    private boolean playerRemains(Location location) {
        for (Claim claim : siege.claims) {
            if (claim.isNear(location, 25)) return true;
        }
        return false;
    }

    void scheduleAnotherCheck() {
        siege.checkupTask = SchedulerUtil.runLaterEntity(
                GriefPrevention.instance, siege.attacker, this, 20L * 30L);
    }
}
