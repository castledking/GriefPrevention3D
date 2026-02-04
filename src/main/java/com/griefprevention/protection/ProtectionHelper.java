package com.griefprevention.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.events.PreventBlockBreakEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

/**
 * A utility used to simplify various protection-related checks.
 */
public final class ProtectionHelper
{
    private ProtectionHelper() {}

    /**
     * Check the {@link ClaimPermission} state for a {@link Player} at a particular {@link Location}.
     *
     * <p>This respects ignoring claims, wilderness rules, etc.</p>
     *
     * @param player the person performing the action
     * @param location the affected {@link Location}
     * @param permission the required permission
     * @param trigger the triggering {@link Event}, if any
     * @return the denial message supplier, or {@code null} if the action is not denied
     */
    /**
     * Check the {@link ClaimPermission} state for a {@link Player} at a particular {@link Location}.
     *
     * <p>This respects ignoring claims, wilderness rules, etc.</p>
     *
     * @param player the person performing the action
     * @param location the affected {@link Location}
     * @param permission the required permission
     * @param trigger the triggering {@link Event}, if any
     * @return the denial message supplier, or {@code null} if the action is not denied
     */
    public static @Nullable Supplier<String> checkPermission(
            @NotNull Player player,
            @NotNull Location location,
            @NotNull ClaimPermission permission,
            @Nullable Event trigger)
    {
        World world = location.getWorld();
        if (world == null || !GriefPrevention.instance.claimsEnabledForWorld(world)) return null;

        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());

        // Administrators ignoring claims always have permission.
        if (playerData.ignoreClaims) return null;

        // Get claim at location, respecting 3D boundaries (ignoreHeight = false)
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, playerData.lastClaim);

        // If there is no claim here, use wilderness rules.
        if (claim == null)
        {
            ClaimsMode mode = GriefPrevention.instance.config_claims_worldModes.get(world);
            if (mode == ClaimsMode.Creative || mode == ClaimsMode.SurvivalRequiringClaims)
            {
                // Allow placing chest if it would create an automatic claim.
                if (trigger instanceof BlockPlaceEvent placeEvent
                        && placeEvent.getBlock().getType() == Material.CHEST
                        && playerData.getClaims().isEmpty()
                        && GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius > -1)
                    return null;

                // If claims are required, provide relevant information.
                return () ->
                {
                    String reason = GriefPrevention.instance.dataStore.getMessage(Messages.NoBuildOutsideClaims);
                    if (player.hasPermission("griefprevention.ignoreclaims"))
                        reason += "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                    reason += "  " + GriefPrevention.instance.dataStore.getMessage(Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                    return reason;
                };
            }

            // If claims are not required, then the player has permission.
            return null;
        }

        // Update cached claim.
        playerData.lastClaim = claim;
        // Nested 3D subdivisions (inheritNothing=true): only check the subdivision, never parent.
        if (claim.is3D() && claim.getSubclaimRestrictions()) {
            return claim.checkPermission(player, permission, trigger);
        }

        // First-child 3D subdivisions: if not explicitly denied here, consult parent (ensures inheritance).
        if (claim.is3D() && claim.parent != null && claim.parent.parent == null) {
            if (permission == null || !claim.isPermissionDenied(player.getUniqueId().toString(), permission)) {
                Supplier<String> parentResult = claim.parent.checkPermission(player, permission, trigger);
                if (parentResult == null) return null;
            }
        }

        // Check this claim first. Only fall back to parents if the claim inherits permissions.
        Supplier<String> cancel = claim.checkPermission(player, permission, trigger);
        if (cancel == null)
        {
            return null;
        }

        boolean explicitlyDenied = permission != null && claim.isPermissionDenied(player.getUniqueId().toString(), permission);
        if (explicitlyDenied)
        {
            return cancel;
        }

        boolean isNestedSubdivision = claim.parent != null && claim.parent.parent != null;
        if (isNestedSubdivision)
        {
            return cancel;
        }

        if (claim.getSubclaimRestrictions())
        {
            // inheritNothing=true -> do not consult parents. Respect the subdivision's denial.
            return cancel;
        }

        Claim parent = claim.parent;
        while (parent != null)
        {
            cancel = parent.checkPermission(player, permission, trigger);
            if (cancel == null)
            {
                return null;
            }
            parent = parent.parent;
        }

        return cancel;
    }
}