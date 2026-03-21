package com.griefprevention.addon.gp3d;

import com.griefprevention.api.ClaimToolContext;
import com.griefprevention.api.ClaimToolHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

final class StackedSubdivisionToolHandler implements ClaimToolHandler
{
    private final GP3DAddon plugin;

    StackedSubdivisionToolHandler(@NotNull GP3DAddon plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public int getPriority()
    {
        return 1000;
    }

    @Override
    public boolean handle(@NotNull ClaimToolContext context)
    {
        UUID playerId = context.getPlayer().getUniqueId();
        if (!plugin.isModeActive(playerId))
        {
            return false;
        }

        // Only take over golden-shovel interactions. Let the investigation tool and all other
        // interactions continue through GP's built-in handlers.
        if (context.getItemType() != context.getPlugin().config_claims_modificationTool)
        {
            return false;
        }

        if (context.getAction() != Action.RIGHT_CLICK_BLOCK && context.getAction() != Action.RIGHT_CLICK_AIR)
        {
            return false;
        }

        if (!context.getPlayer().hasPermission("griefprevention.createclaims"))
        {
            GriefPrevention.sendMessage(context.getPlayer(), TextMode.Err, Messages.NoCreateClaimPermission);
            return true;
        }

        if (context.getPlayer().isSneaking())
        {
            plugin.clearSelection(playerId);
            GriefPrevention.sendMessage(context.getPlayer(), TextMode.Info, "Stacked subdivision selection cleared.");
            return true;
        }

        Block clickedBlock = resolveTargetBlock(context);
        if (clickedBlock == null || clickedBlock.getType().isAir())
        {
            GriefPrevention.sendMessage(context.getPlayer(), TextMode.Err, Messages.TooFarAway);
            return true;
        }

        PlayerData playerData = context.getPlayerData();
        if (playerData.claimResizing != null && playerData.claimResizing.inDataStore)
        {
            // Let built-in flow finish claim resizing while 3D mode is active.
            return false;
        }

        Claim clickedClaim = context.getDataStore().getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
        if (clickedClaim != null && isCorner(clickedClaim, clickedBlock))
        {
            // Corner clicks should keep upstream resizing behavior, even in 3D mode.
            plugin.clearSelection(playerId);
            return false;
        }

        context.getEvent().setCancelled(true);

        if (clickedClaim == null)
        {
            GriefPrevention.sendMessage(context.getPlayer(), TextMode.Err, "Stand inside a top-level claim to create a 3D subdivision.");
            return true;
        }

        if (clickedClaim.parent != null)
        {
            GriefPrevention.sendMessage(context.getPlayer(), TextMode.Err, "This addon only creates stacked subdivisions directly under a top-level claim.");
            plugin.visualizeClaim(context.getPlayer(), clickedClaim.parent);
            return true;
        }

        Supplier<String> denial = clickedClaim.checkPermission(context.getPlayer(), ClaimPermission.Edit, context.getEvent());
        if (denial != null)
        {
            GriefPrevention.sendMessage(context.getPlayer(), TextMode.Err, denial.get());
            return true;
        }

        GP3DAddon.SelectionSession session = plugin.getOrCreateSession(playerId);
        if (session.firstCorner() == null || session.parent() == null || !session.parent().inDataStore)
        {
            plugin.startSelection(context.getPlayer(), clickedClaim, clickedBlock.getLocation());
            return true;
        }

        if (!session.parent().getID().equals(clickedClaim.getID())
                || !session.firstCorner().getWorld().equals(clickedBlock.getWorld()))
        {
            plugin.startSelection(context.getPlayer(), clickedClaim, clickedBlock.getLocation());
            GriefPrevention.sendMessage(context.getPlayer(), TextMode.Info, "Selection restarted in a different claim.");
            return true;
        }

        return completeSelection(context, session.parent(), session.firstCorner(), clickedBlock.getLocation());
    }

    private boolean completeSelection(
            @NotNull ClaimToolContext context,
            @NotNull Claim parent,
            @NotNull Location firstCorner,
            @NotNull Location secondCorner)
    {
        int lesserY = Math.min(firstCorner.getBlockY(), secondCorner.getBlockY());
        int greaterY = Math.max(firstCorner.getBlockY(), secondCorner.getBlockY());

        CreateClaimResult result = context.getDataStore().createClaim(
                firstCorner.getWorld(),
                firstCorner.getBlockX(),
                secondCorner.getBlockX(),
                lesserY,
                greaterY,
                firstCorner.getBlockZ(),
                secondCorner.getBlockZ(),
                parent.getOwnerID(),
                parent,
                null,
                context.getPlayer(),
                false,
                claim -> claim.setGeometryKey(StackedSubdivisionGeometry.KEY));

        plugin.clearSelection(context.getPlayer().getUniqueId());

        if (!result.succeeded || result.claim == null)
        {
            if (result.claim != null)
            {
                GriefPrevention.sendMessage(context.getPlayer(), TextMode.Err, Messages.CreateClaimFailOverlapShort);
                plugin.visualizeClaim(context.getPlayer(), result.claim);
            }
            else
            {
                GriefPrevention.sendMessage(context.getPlayer(), TextMode.Err, Messages.CreateClaimFailOverlapRegion);
            }

            return true;
        }

        context.getPlayerData().lastClaim = result.claim;
        GriefPrevention.sendMessage(context.getPlayer(), TextMode.Success, "Created a 3D subdivision with exact Y bounds.");
        plugin.visualizeClaim(context.getPlayer(), result.claim);
        return true;
    }

    private static boolean isCorner(@NotNull Claim claim, @NotNull Block block)
    {
        int x = block.getX();
        int z = block.getZ();
        return (x == claim.getLesserBoundaryCorner().getBlockX()
                || x == claim.getGreaterBoundaryCorner().getBlockX())
                && (z == claim.getLesserBoundaryCorner().getBlockZ()
                || z == claim.getGreaterBoundaryCorner().getBlockZ());
    }

    private static @Nullable Block resolveTargetBlock(@NotNull ClaimToolContext context)
    {
        Block clickedBlock = context.getClickedBlock();
        if (clickedBlock != null && !context.getClickedBlockType().isAir())
        {
            return clickedBlock;
        }

        try
        {
            return getTargetBlock(context.getPlayer(), 100);
        }
        catch (IllegalStateException ignored)
        {
            return clickedBlock;
        }
    }

    private static @NotNull Block getTargetBlock(@NotNull org.bukkit.entity.Player player, int maxDistance) throws IllegalStateException
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
