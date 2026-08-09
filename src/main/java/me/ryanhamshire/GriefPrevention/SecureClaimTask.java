package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.entity.Player;

/** Re-locks claims after the siege winner's looting window. */
final class SecureClaimTask implements Runnable {
    private final SiegeData siege;

    SecureClaimTask(SiegeData siege) {
        this.siege = siege;
    }

    @Override
    public void run() {
        for (Claim claim : siege.claims) {
            claim.doorsOpen = false;
            for (Player player : GriefPrevention.instance.getServer().getOnlinePlayers()) {
                SchedulerUtil.runLaterEntity(GriefPrevention.instance, player, () -> {
                    if (!claim.contains(player.getLocation(), false, false)
                            || claim.checkPermission(player, ClaimPermission.Access, null) == null) return;
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeDoorsLockedEjection);
                    GriefPrevention.instance.ejectPlayer(player);
                }, 1L);
            }
        }
    }
}
