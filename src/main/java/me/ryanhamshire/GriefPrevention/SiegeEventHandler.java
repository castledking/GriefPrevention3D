package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Applies siege overrides to the normal claim permission pipeline. */
public final class SiegeEventHandler implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClaimPermissionCheck(ClaimPermissionCheckEvent event) {
        if (event.getRequiredPermission() == ClaimPermission.Manage) return;
        Player player = event.getCheckedPlayer();
        if (player == null) return;

        Claim claim = event.getClaim();
        if (claim.isAdminClaim()) return;

        if (event.getRequiredPermission() == ClaimPermission.Edit) {
            if (claim.siegeData != null) {
                event.setDenialReason(() -> GriefPrevention.instance.dataStore.getMessage(Messages.NoModifyDuringSiege));
            }
            return;
        }

        if (event.getRequiredPermission() == ClaimPermission.Access) {
            if (claim.doorsOpen) event.setDenialReason(null);
            return;
        }

        if (event.getRequiredPermission() == ClaimPermission.Container
                || event.getRequiredPermission() == ClaimPermission.Inventory) {
            GriefPrevention.instance.dataStore.tryExtendSiege(player, claim);
            if (claim.siegeData != null) {
                event.setDenialReason(() -> GriefPrevention.instance.dataStore.getMessage(
                        Messages.NoContainersSiege, claim.siegeData.attacker.getName()));
            }
            return;
        }

        GriefPrevention.instance.dataStore.tryExtendSiege(player, claim);
        if (claim.siegeData == null && !claim.doorsOpen) return;

        Material broken = null;
        if (event.getTriggeringEvent() instanceof BlockBreakEvent) {
            broken = ((BlockBreakEvent) event.getTriggeringEvent()).getBlock().getType();
        } else if (event.getTriggeringEvent() instanceof Claim.CompatBuildBreakEvent) {
            Claim.CompatBuildBreakEvent buildBreak = (Claim.CompatBuildBreakEvent) event.getTriggeringEvent();
            if (buildBreak.isBreak()) broken = buildBreak.getMaterial();
        } else if (event.getTriggeringEvent() instanceof PlayerInteractEvent) {
            PlayerInteractEvent interact = (PlayerInteractEvent) event.getTriggeringEvent();
            if (interact.getAction() == Action.PHYSICAL && interact.getClickedBlock() != null
                    && "TURTLE_EGG".equals(interact.getClickedBlock().getType().name())) {
                broken = interact.getClickedBlock().getType();
            }
        }

        if (broken != null) {
            if (!GriefPrevention.instance.config_siege_blocks.contains(broken)) {
                event.setDenialReason(() -> GriefPrevention.instance.dataStore.getMessage(Messages.NonSiegeMaterial));
            } else if (player.getUniqueId().equals(claim.ownerID)) {
                event.setDenialReason(() -> GriefPrevention.instance.dataStore.getMessage(Messages.NoOwnerBuildUnderSiege));
            } else {
                event.setDenialReason(null);
            }
            return;
        }

        if (claim.siegeData != null) {
            event.setDenialReason(() -> GriefPrevention.instance.dataStore.getMessage(
                    Messages.NoBuildUnderSiege, claim.siegeData.attacker.getName()));
        }
    }
}
