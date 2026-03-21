/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import com.griefprevention.api.ClaimToolContext;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.events.ClaimInspectionEvent;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Dispatches claim tool interactions to addon handlers and then GP's built-in logic.
 */
final class ClaimToolDispatcher
{
    private final @NotNull DataStore dataStore;
    private final @NotNull GriefPrevention instance;

    ClaimToolDispatcher(@NotNull DataStore dataStore, @NotNull GriefPrevention instance)
    {
        this.dataStore = dataStore;
        this.instance = instance;
    }

    boolean handle(@NotNull PlayerInteractEvent event, @Nullable Block clickedBlock, @NotNull Material clickedBlockType)
    {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)
        {
            return false;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND)
        {
            return false;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = instance.getItemInHand(player, hand);
        Material materialInHand = itemInHand.getType();
        if (materialInHand != instance.config_claims_investigationTool
                && materialInHand != instance.config_claims_modificationTool)
        {
            return false;
        }

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        ClaimToolContext context = new ClaimToolContext(
                instance,
                dataStore,
                player,
                playerData,
                event,
                hand,
                itemInHand,
                clickedBlock,
                clickedBlockType);

        try
        {
            if (instance.getClaimToolHandlerRegistry().dispatch(context))
            {
                return true;
            }
        }
        catch (Exception exception)
        {
            GriefPrevention.AddLogEntry(
                    "ClaimToolHandler dispatch failed: " + exception.getMessage(),
                    CustomLogEntryTypes.Debug,
                    true);
            instance.getLogger().log(Level.WARNING, "ClaimToolHandler dispatch failed.", exception);
        }

        if (materialInHand == instance.config_claims_investigationTool)
        {
            return handleInvestigationTool(event, player, playerData, clickedBlock, clickedBlockType);
        }

        return handleModificationTool(event, player, playerData, clickedBlock, clickedBlockType);
    }

    private boolean handleInvestigationTool(
            @NotNull PlayerInteractEvent event,
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @Nullable Block clickedBlock,
            @NotNull Material clickedBlockType)
    {
        if (!instance.claimsEnabledForWorld(player.getWorld()))
        {
            return true;
        }

        if (player.getCooldown(instance.config_claims_investigationTool) > 0)
        {
            return true;
        }
        player.setCooldown(instance.config_claims_investigationTool, 1);

        if (player.isSneaking() && player.hasPermission("griefprevention.visualizenearbyclaims"))
        {
            Set<Claim> claims = this.dataStore.getNearbyClaims(player.getLocation());
            ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, null, claims, true);
            Bukkit.getPluginManager().callEvent(inspectionEvent);
            if (inspectionEvent.isCancelled())
            {
                return true;
            }

            BoundaryVisualization.visualizeNearbyClaims(
                    player,
                    inspectionEvent.getClaims(),
                    player.getEyeLocation().getBlockY());
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.ShowNearbyClaims, String.valueOf(claims.size()));
            return true;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR)
        {
            clickedBlock = getTargetBlock(player, 100);
            clickedBlockType = clickedBlock.getType();
        }

        if (clickedBlock == null)
        {
            return true;
        }

        if (clickedBlockType == Material.AIR)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.TooFarAway);
            playerData.setVisibleBoundaries(null);
            return true;
        }

        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
        if (claim == null)
        {
            ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, clickedBlock, null);
            Bukkit.getPluginManager().callEvent(inspectionEvent);
            if (inspectionEvent.isCancelled())
            {
                return true;
            }

            GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockNotClaimed);
            playerData.setVisibleBoundaries(null);
            return true;
        }

        ClaimInspectionEvent inspectionEvent = new ClaimInspectionEvent(player, clickedBlock, claim);
        Bukkit.getPluginManager().callEvent(inspectionEvent);
        if (inspectionEvent.isCancelled())
        {
            return true;
        }

        playerData.lastClaim = claim;
        GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockClaimed, claim.getOwnerName());
        BoundaryVisualization.visualizeClaim(player, claim, VisualizationType.CLAIM);

        if (player.hasPermission("griefprevention.seeclaimsize"))
        {
            GriefPrevention.sendMessage(player, TextMode.Info, "  " + claim.getWidth() + "x" + claim.getHeight() + "=" + claim.getArea());
        }

        if (!claim.isAdminClaim() && (player.hasPermission("griefprevention.deleteclaims")
                || player.hasPermission("griefprevention.seeinactivity")))
        {
            if (claim.parent != null)
            {
                claim = claim.parent;
            }

            Date lastLogin = new Date(Bukkit.getOfflinePlayer(claim.ownerID).getLastPlayed());
            Date now = new Date();
            long daysElapsed = (now.getTime() - lastLogin.getTime()) / (1000 * 60 * 60 * 24);
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.PlayerOfflineTime, String.valueOf(daysElapsed));

            if (instance.getServer().getPlayer(claim.ownerID) == null)
            {
                this.dataStore.clearCachedPlayerData(claim.ownerID);
            }
        }

        return true;
    }

    private boolean handleModificationTool(
            @NotNull PlayerInteractEvent event,
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @Nullable Block clickedBlock,
            @NotNull Material clickedBlockType)
    {
        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_AIR)
        {
            clickedBlock = getTargetBlock(player, 100);
            clickedBlockType = clickedBlock.getType();
        }

        if (clickedBlock == null)
        {
            return true;
        }

        if (clickedBlockType == Material.AIR)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.TooFarAway);
            return true;
        }

        if (!player.hasPermission("griefprevention.createclaims"))
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoCreateClaimPermission);
            return true;
        }

        if (playerData.claimResizing != null && playerData.claimResizing.inDataStore)
        {
            if (clickedBlock.getLocation().equals(playerData.lastShovelLocation))
            {
                return true;
            }

            int newx1;
            int newx2;
            int newz1;
            int newz2;
            int newy1;
            int newy2;
            Claim resizingClaim = playerData.claimResizing;
            if (playerData.lastShovelLocation.getBlockX() == playerData.claimResizing.getLesserBoundaryCorner().getBlockX())
            {
                newx1 = clickedBlock.getX();
                newx2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockX();
            }
            else
            {
                newx1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockX();
                newx2 = clickedBlock.getX();
            }

            if (playerData.lastShovelLocation.getBlockZ() == playerData.claimResizing.getLesserBoundaryCorner().getBlockZ())
            {
                newz1 = clickedBlock.getZ();
                newz2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockZ();
            }
            else
            {
                newz1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockZ();
                newz2 = clickedBlock.getZ();
            }

            if (instance.getClaimGeometryRegistry().getDefaultGeometry().getKey().equals(resizingClaim.getGeometryKey()))
            {
                newy1 = resizingClaim.getLesserBoundaryCorner().getBlockY();
                newy2 = clickedBlock.getY() - instance.config_claims_claimsExtendIntoGroundDistance;
            }
            else
            {
                int currentMinY = resizingClaim.getLesserBoundaryCorner().getBlockY();
                int currentMaxY = resizingClaim.getGreaterBoundaryCorner().getBlockY();

                // Move whichever vertical boundary is closest to the corner the player initially selected.
                int startY = playerData.lastShovelLocation.getBlockY();
                if (Math.abs(startY - currentMinY) <= Math.abs(startY - currentMaxY))
                {
                    newy1 = clickedBlock.getY();
                    newy2 = currentMaxY;
                }
                else
                {
                    newy1 = currentMinY;
                    newy2 = clickedBlock.getY();
                }
            }

            this.dataStore.resizeClaimWithChecks(player, playerData, newx1, newx2, newy1, newy2, newz1, newz2);
            return true;
        }

        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), true, playerData.lastClaim);
        if (claim != null)
        {
            Supplier<String> noEditReason = claim.checkPermission(
                    player,
                    ClaimPermission.Edit,
                    event,
                    () -> instance.dataStore.getMessage(Messages.CreateClaimFailOverlapOtherPlayer, claim.getOwnerName()));
            if (noEditReason == null)
            {
                boolean clickedCorner = (clickedBlock.getX() == claim.getLesserBoundaryCorner().getBlockX()
                        || clickedBlock.getX() == claim.getGreaterBoundaryCorner().getBlockX())
                        && (clickedBlock.getZ() == claim.getLesserBoundaryCorner().getBlockZ()
                                || clickedBlock.getZ() == claim.getGreaterBoundaryCorner().getBlockZ());
                if (clickedCorner)
                {
                    playerData.claimResizing = claim;
                    playerData.lastShovelLocation = clickedBlock.getLocation();
                    GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ResizeStart);
                }
                else if (playerData.shovelMode == ShovelMode.Subdivide)
                {
                    if (playerData.lastShovelLocation == null)
                    {
                        if (claim.parent != null)
                        {
                            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeFailOverlapSubdivision);
                        }
                        else
                        {
                            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionStart);
                            playerData.lastShovelLocation = clickedBlock.getLocation();
                            playerData.claimSubdividing = claim;
                        }
                    }
                    else
                    {
                        if (!playerData.lastShovelLocation.getWorld().equals(clickedBlock.getWorld()))
                        {
                            playerData.lastShovelLocation = null;
                            return handleModificationTool(event, player, playerData, clickedBlock, clickedBlockType);
                        }

                        CreateClaimResult result = this.dataStore.createClaim(
                                player.getWorld(),
                                playerData.lastShovelLocation.getBlockX(), clickedBlock.getX(),
                                playerData.lastShovelLocation.getBlockY() - instance.config_claims_claimsExtendIntoGroundDistance,
                                clickedBlock.getY() - instance.config_claims_claimsExtendIntoGroundDistance,
                                playerData.lastShovelLocation.getBlockZ(), clickedBlock.getZ(),
                                null,
                                playerData.claimSubdividing,
                                null,
                                player);

                        if (!result.succeeded || result.claim == null)
                        {
                            if (result.claim != null)
                            {
                                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateSubdivisionOverlap);
                                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE, clickedBlock);
                            }
                            else
                            {
                                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
                            }

                            return true;
                        }

                        GriefPrevention.sendMessage(player, TextMode.Success, Messages.SubdivisionSuccess);
                        BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM, clickedBlock);
                        playerData.lastShovelLocation = null;
                        playerData.claimSubdividing = null;
                    }
                }
                else
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlap);
                    BoundaryVisualization.visualizeClaim(player, claim, VisualizationType.CLAIM, clickedBlock);
                }
            }
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Err, noEditReason.get());
                BoundaryVisualization.visualizeClaim(player, claim, VisualizationType.CONFLICT_ZONE, clickedBlock);
            }

            return true;
        }

        Location lastShovelLocation = playerData.lastShovelLocation;
        if (lastShovelLocation == null)
        {
            if (!instance.claimsEnabledForWorld(player.getWorld()))
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
                return true;
            }

            if (instance.config_claims_maxClaimsPerPlayer > 0
                    && !player.hasPermission("griefprevention.overrideclaimcountlimit")
                    && playerData.getClaims().size() >= instance.config_claims_maxClaimsPerPlayer)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
                return true;
            }

            playerData.lastShovelLocation = clickedBlock.getLocation();
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimStart);
            BoundaryVisualization.visualizeArea(player, new BoundingBox(clickedBlock), VisualizationType.INITIALIZE_ZONE);
            return true;
        }

        if (!lastShovelLocation.getWorld().equals(clickedBlock.getWorld()))
        {
            playerData.lastShovelLocation = null;
            return handleModificationTool(event, player, playerData, clickedBlock, clickedBlockType);
        }

        if (playerData.inPvpCombat())
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoClaimDuringPvP);
            return true;
        }

        int newClaimWidth = Math.abs(playerData.lastShovelLocation.getBlockX() - clickedBlock.getX()) + 1;
        int newClaimHeight = Math.abs(playerData.lastShovelLocation.getBlockZ() - clickedBlock.getZ()) + 1;

        if (playerData.shovelMode != ShovelMode.Admin)
        {
            if (newClaimWidth < instance.config_claims_minWidth || newClaimHeight < instance.config_claims_minWidth)
            {
                if (newClaimWidth != 1 && newClaimHeight != 1)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.NewClaimTooNarrow, String.valueOf(instance.config_claims_minWidth));
                }
                return true;
            }

            int newArea = newClaimWidth * newClaimHeight;
            if (newArea < instance.config_claims_minArea)
            {
                if (newArea != 1)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.ResizeClaimInsufficientArea, String.valueOf(instance.config_claims_minArea));
                }
                return true;
            }
        }

        UUID playerID = player.getUniqueId();
        if (playerData.shovelMode != ShovelMode.Admin)
        {
            int newClaimArea = newClaimWidth * newClaimHeight;
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            if (newClaimArea > remainingBlocks)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(newClaimArea - remainingBlocks));
                instance.dataStore.tryAdvertiseAdminAlternatives(player);
                return true;
            }
        }
        else
        {
            playerID = null;
        }

        CreateClaimResult result = this.dataStore.createClaim(
                player.getWorld(),
                lastShovelLocation.getBlockX(), clickedBlock.getX(),
                lastShovelLocation.getBlockY() - instance.config_claims_claimsExtendIntoGroundDistance,
                clickedBlock.getY() - instance.config_claims_claimsExtendIntoGroundDistance,
                lastShovelLocation.getBlockZ(), clickedBlock.getZ(),
                playerID,
                null,
                null,
                player);

        if (!result.succeeded || result.claim == null)
        {
            if (result.claim != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);
                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE, clickedBlock);
            }
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
            }

            return true;
        }

        GriefPrevention.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);
        BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM, clickedBlock);
        playerData.lastShovelLocation = null;

        if (!player.hasPermission("griefprevention.adminclaims") && result.claim.getArea() >= 1000)
        {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.BecomeMayor, 200L);
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SubdivisionVideo2, 201L, DataStore.SUBDIVISION_VIDEO_URL);
        }

        AutoExtendClaimTask.scheduleAsync(result.claim);
        return true;
    }

    private static @NotNull Block getTargetBlock(@NotNull Player player, int maxDistance) throws IllegalStateException
    {
        Location eye = player.getEyeLocation();
        Material eyeMaterial = eye.getBlock().getType();
        boolean passThroughWater = eyeMaterial == Material.WATER;
        BlockIterator iterator = new BlockIterator(player.getLocation(), player.getEyeHeight(), maxDistance);
        Block result = player.getLocation().getBlock().getRelative(BlockFace.UP);
        while (iterator.hasNext())
        {
            result = iterator.next();
            Material type = result.getType();
            if (!Tag.REPLACEABLE.isTagged(type) || (!passThroughWater && type == Material.WATER))
            {
                return result;
            }
        }

        return result;
    }
}
