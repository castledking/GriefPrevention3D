package com.griefprevention.commands;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.AutoExtendClaimTask;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

final class ClaimCreateCommandAction implements TabExecutor
{
    private final GriefPrevention plugin;

    ClaimCreateCommandAction(@NotNull GriefPrevention plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args)
    {
        return execute(sender, args);
    }

    boolean execute(@NotNull CommandSender sender, @NotNull String[] args)
    {
        if (!(sender instanceof Player))
            return false;
        Player player = (Player) sender;

        World world = player.getWorld();
        if (!plugin.claimsEnabledForWorld(world))
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
            return true;
        }

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

        if (plugin.config_claims_maxClaimsPerPlayer > 0 &&
                !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                playerData.getClaims().size() >= plugin.config_claims_maxClaimsPerPlayer)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
            return true;
        }

        int radius;

        if (args.length > 0)
        {
            if (needsShovel(playerData, player))
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.RadiusRequiresGoldenShovel);
                return true;
            }

            try
            {
                radius = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e)
            {
                return false;
            }

            int minRadius = getClaimMinRadius();
            if (radius < minRadius)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.MinimumRadius, String.valueOf(minRadius));
                return true;
            }
        }
        else if (playerData.getClaims().isEmpty() && plugin.config_claims_automaticClaimsForNewPlayersRadius >= 0)
        {
            radius = plugin.config_claims_automaticClaimsForNewPlayersRadius;
        }
        else
        {
            if (needsShovel(playerData, player))
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
                return true;
            }

            radius = getClaimMinRadius();
        }

        if (radius < 0) radius = 0;

        Location playerLoc = player.getLocation();
        int lesserX;
        int lesserZ;
        int greaterX;
        int greaterZ;
        try
        {
            if (args.length > 0)
            {
                lesserX = Math.subtractExact(playerLoc.getBlockX(), radius);
                lesserZ = Math.subtractExact(playerLoc.getBlockZ(), radius);
                greaterX = Math.addExact(playerLoc.getBlockX(), radius);
                greaterZ = Math.addExact(playerLoc.getBlockZ(), radius);
            }
            else
            {
                ClaimCreateLayout.Bounds bounds = ClaimCreateLayout.resolveDefaultBounds(plugin, playerData, playerLoc);
                lesserX = bounds.lesserX();
                lesserZ = bounds.lesserZ();
                greaterX = bounds.greaterX();
                greaterZ = bounds.greaterZ();
            }
        }
        catch (ArithmeticException e)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(Integer.MAX_VALUE));
            return true;
        }

        Location lesser = new Location(world, lesserX, playerLoc.getY(), lesserZ);
        Location greater = new Location(world, greaterX, GriefPrevention.getWorldMaxY(world), greaterZ);

        UUID ownerId;
        if (playerData.shovelMode == ShovelMode.Admin || playerData.shovelMode == ShovelMode.Admin3D)
        {
            ownerId = null;
        }
        else
        {
            int area;
            try
            {
                int dX = Math.addExact(Math.subtractExact(greater.getBlockX(), lesser.getBlockX()), 1);
                int dZ = Math.addExact(Math.subtractExact(greater.getBlockZ(), lesser.getBlockZ()), 1);
                area = Math.abs(Math.multiplyExact(dX, dZ));
            }
            catch (ArithmeticException e)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(Integer.MAX_VALUE));
                return true;
            }
            int remaining = playerData.getRemainingClaimBlocks();
            if (remaining < area)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks, String.valueOf(area - remaining));
                plugin.dataStore.tryAdvertiseAdminAlternatives(player);
                return true;
            }
            ownerId = player.getUniqueId();
        }

        createClaim(player, playerData, lesser, greater, ownerId);
        return true;
    }

    private void createClaim(
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @NotNull Location lesser,
            @NotNull Location greater,
            @Nullable UUID ownerId)
    {
        World world = player.getWorld();
        int minY;
        int maxY;

        Claim parentClaim = plugin.dataStore.getClaimAt(lesser, true, null);
        if (parentClaim != null)
        {
            if (lesser.getBlockY() == greater.getBlockY())
            {
                minY = parentClaim.getLesserBoundaryCorner().getBlockY();
                maxY = parentClaim.getGreaterBoundaryCorner().getBlockY();
            }
            else
            {
                minY = Math.min(lesser.getBlockY(), greater.getBlockY());
                maxY = Math.max(lesser.getBlockY(), greater.getBlockY());
            }
        }
        else
        {
            minY = lesser.getBlockY() - plugin.config_claims_claimsExtendIntoGroundDistance - 1;
            maxY = world.getHighestBlockYAt(greater) - plugin.config_claims_claimsExtendIntoGroundDistance - 1;
        }
        CreateClaimResult result = plugin.dataStore.createClaim(world,
                lesser.getBlockX(), greater.getBlockX(),
                minY, maxY,
                lesser.getBlockZ(), greater.getBlockZ(),
                ownerId, null, null, player);
        if (!result.succeeded || result.claim == null)
        {
            if (result.claim != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);
                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE);
            }
            else
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
            }
        }
        else
        {
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);

            if (plugin.creativeRulesApply(player.getLocation()))
            {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
            }
            else if (plugin.claimsEnabledForWorld(world))
            {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
            }
            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM);
            playerData.claimResizing = null;
            playerData.lastShovelLocation = null;

            AutoExtendClaimTask.scheduleAsync(result.claim);
        }
    }

    private boolean needsShovel(@NotNull PlayerData playerData, @NotNull Player player)
    {
        if (player.hasPermission("griefprevention.createclaims.toolbypass"))
        {
            return false;
        }
        return playerData.getClaims().size() < 2
                && player.getGameMode() != GameMode.CREATIVE
                && player.getInventory().getItemInMainHand().getType() != plugin.config_claims_modificationTool;
    }

    private int getClaimMinRadius()
    {
        return (int) Math.ceil(Math.sqrt(plugin.config_claims_minArea) / 2);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args)
    {
        if (args.length != 1)
            return List.of();
        return TabCompletions.integer(args, 3, false);
    }
}
