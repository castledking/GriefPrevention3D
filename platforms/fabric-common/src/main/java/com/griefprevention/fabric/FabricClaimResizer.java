package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Gives a claim a new outline with the golden shovel's checks and messages, for the shovel and for
 * {@code /scrollresize} alike, as Paper's {@code resizeClaimWithChecks} serves both.
 */
final class FabricClaimResizer
{
    private final FabricClaimRepository claims;
    private final FabricFakeBlockVisualization visualization;
    private final FabricDenialFeedback feedback;
    private final FabricSettings settings;

    FabricClaimResizer(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricFakeBlockVisualization visualization,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricSettings settings)
    {
        this.claims = claims;
        this.visualization = visualization;
        this.feedback = feedback;
        this.settings = settings;
    }

    /**
     * @param shownAt the block whose height the outline is drawn at
     * @return true when the resize is over, because the claim took the new outline or no longer
     *         exists; false when it was refused and the player may try another outline
     */
    boolean resize(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            long claimId,
            @NotNull ClaimBounds bounds,
            @NotNull BlockPos shownAt)
    {
        // The minimum size is for claims; subdivisions may be any size, as on Paper.
        ClaimSnapshot resizing = this.claims.claimById(claimId);
        boolean subdivision = resizing != null && resizing.parentId() != null;
        int minimumWidth = this.settings.tools().minimumWidth();
        if (!subdivision && (bounds.xLength() < minimumWidth || bounds.zLength() < minimumWidth))
        {
            this.visualization.visualizeInitializeBounds(player, level, bounds, shownAt);
            this.feedback.sendError(player, MessageKey.RESIZE_CLAIM_TOO_NARROW, String.valueOf(minimumWidth));
            return false;
        }
        int minimumArea = this.settings.tools().minimumArea();
        if (!subdivision && bounds.area() < minimumArea)
        {
            this.visualization.visualizeInitializeBounds(player, level, bounds, shownAt);
            this.feedback.sendError(player, MessageKey.RESIZE_CLAIM_INSUFFICIENT_AREA, String.valueOf(minimumArea));
            return false;
        }

        try
        {
            FabricClaimRepository.UpdateClaimResult result = this.claims.updateClaimBounds(claimId, bounds, player);
            if (result.isMissing())
            {
                this.visualization.clear(player);
                return true;
            }
            if (result.hasInsufficientClaimBlocks())
            {
                this.visualization.visualizeInitializeBounds(player, level, bounds, shownAt);
                this.feedback.sendError(
                        player,
                        MessageKey.RESIZE_NEED_MORE_BLOCKS,
                        String.valueOf(result.blocksNeeded()));
                return false;
            }
            FabricClaimRepository.PlacementProblem problem = result.placementProblem();
            if (problem != null)
            {
                ClaimSnapshot conflicting = result.overlappingClaim();
                this.visualization.visualizeConflictBounds(
                        player, level, conflicting == null ? bounds : conflicting.bounds(), shownAt);
                this.feedback.sendError(player, switch (problem)
                {
                    case OUTSIDE_PARENT -> MessageKey.RESIZE_FAIL_SUBDIVISION_EXCEEDS_PARENT;
                    case OVERLAPS_SIBLING -> MessageKey.RESIZE_FAIL_OVERLAP_SUBDIVISION;
                    case EXCLUDES_SUBDIVISION -> MessageKey.RESIZE_FAIL_SUBDIVISION;
                });
                return false;
            }
            ClaimSnapshot updated = result.updatedClaim();
            if (updated == null)
            {
                ClaimSnapshot overlapping = result.overlappingClaim();
                this.visualization.visualizeConflictBounds(
                        player, level, overlapping == null ? bounds : overlapping.bounds(), shownAt);
                this.feedback.sendError(player, MessageKey.RESIZE_FAIL_OVERLAP);
                return false;
            }

            reportResized(player, level, updated, result.remainingBlocks(), shownAt);
            return true;
        }
        catch (IOException e)
        {
            player.sendSystemMessage(Component.literal("Could not save resized claim: " + e.getMessage()), true);
            return false;
        }
    }

    private void reportResized(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot updated,
            @Nullable Integer remaining,
            @NotNull BlockPos shownAt)
    {
        // An administrative claim costs nothing, so report the resizer's own balance, as Paper does.
        if (remaining == null)
        {
            try
            {
                remaining = this.claims.claimBlockBalance(player.getUUID()).remaining();
            }
            catch (IOException e)
            {
                player.sendSystemMessage(Component.literal("Could not read your claim blocks: " + e.getMessage()), true);
            }
        }
        if (remaining != null)
        {
            this.feedback.send(player, TextMode.SUCCESS, MessageKey.CLAIM_RESIZE_SUCCESS, String.valueOf(remaining));
        }
        this.visualization.visualizeClaim(player, level, updated, shownAt);
    }
}
