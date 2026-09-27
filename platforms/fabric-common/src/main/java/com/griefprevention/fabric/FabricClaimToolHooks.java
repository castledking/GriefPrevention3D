package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The investigation tool (a stick by default) and the modification tool (a golden shovel), as on
 * Paper: right-click a block, or anything up to 100 blocks away, to inspect or edit claims.
 */
final class FabricClaimToolHooks
{
    /** How far away a tool can inspect or edit, as with Paper's {@code getTargetBlock(player, 100)}. */
    private static final double TOOL_REACH = 100.0D;
    /** Paper waits this long after a hotbar switch, so scrolling past the shovel stays quiet. */
    private static final long EQUIP_DELAY_TICKS = 15L;
    private static final String VISUALIZE_NEARBY_PERMISSION = "griefprevention.visualizenearbyclaims";
    private static final String SEE_CLAIM_SIZE_PERMISSION = "griefprevention.seeclaimsize";
    private static final String CREATE_CLAIMS_PERMISSION = "griefprevention.createclaims";
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private final FabricClaimRepository claims;
    private final FabricFakeBlockVisualization visualization;
    private final FabricDenialFeedback feedback;
    private final FabricSettings settings;
    private final FabricClaimModes modes;
    private final FabricLastSeenStore lastSeen;
    private final Logger logger;
    private final Map<UUID, ClaimToolSession> sessions = new HashMap<>();
    /** Server tick at which each player last took the modification tool in hand. */
    private final Map<UUID, Long> modificationToolEquippedAt = new HashMap<>();
    private long tick;

    FabricClaimToolHooks(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricFakeBlockVisualization visualization,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricSettings settings,
            @NotNull FabricClaimModes modes,
            @NotNull FabricLastSeenStore lastSeen,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.visualization = visualization;
        this.feedback = feedback;
        this.settings = settings;
        this.modes = modes;
        this.lastSeen = lastSeen;
        this.logger = logger;
    }

    void register()
    {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
                onUseBlock(player, level, hand, hitResult));
        UseItemCallback.EVENT.register(this::onUseItem);
        ServerTickEvents.END_SERVER_TICK.register(this::trackModificationTool);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUUID();
            this.sessions.remove(playerId);
            this.modificationToolEquippedAt.remove(playerId);
        });
    }

    private @NotNull InteractionResult onUseBlock(
            @NotNull Player player,
            @NotNull Level level,
            @NotNull InteractionHand hand,
            @NotNull BlockHitResult hitResult)
    {
        ClaimTool tool = claimTool(player, hand);
        if (tool == null)
        {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
        {
            // Claim the click client-side too, so the golden shovel never predicts a dirt path.
            return InteractionResult.SUCCESS;
        }

        return useTool(tool, serverPlayer, serverLevel, hitResult.getBlockPos());
    }

    /**
     * Right-clicking air. A vanilla client also sends this after every right-click on a block it
     * could reach, which the block hook already handled, so only targets beyond reach count here.
     */
    private @NotNull InteractionResult onUseItem(
            @NotNull Player player,
            @NotNull Level level,
            @NotNull InteractionHand hand)
    {
        ClaimTool tool = claimTool(player, hand);
        if (tool == null)
        {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.PASS;
        }

        HitResult hit = serverPlayer.pick(TOOL_REACH, 1.0F, false);
        BlockPos target = null;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK)
        {
            double reach = serverPlayer.blockInteractionRange() + 1.0D;
            if (blockHit.getLocation().distanceToSqr(serverPlayer.getEyePosition()) <= reach * reach)
            {
                return InteractionResult.PASS;
            }
            target = blockHit.getBlockPos();
        }

        if (target == null && !(tool == ClaimTool.INVESTIGATION && serverPlayer.isShiftKeyDown()))
        {
            if (tool == ClaimTool.INVESTIGATION)
            {
                this.feedback.sendRateLimitedError(serverPlayer, MessageKey.TOO_FAR_AWAY);
                this.visualization.clear(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        return useTool(tool, serverPlayer, serverLevel, target);
    }

    private @NotNull InteractionResult useTool(
            @NotNull ClaimTool tool,
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @Nullable BlockPos target)
    {
        if (tool == ClaimTool.INVESTIGATION)
        {
            inspect(player, level, target);
        }
        else if (target != null)
        {
            modify(player, level, target);
        }
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------
    // Investigation tool
    // ---------------------------------------------------------------------------------------------

    private void inspect(@NotNull ServerPlayer player, @NotNull ServerLevel level, @Nullable BlockPos target)
    {
        if (!this.settings.tools().claimsEnabled(this.claims.worldKey(level)))
        {
            return;
        }

        if (player.isShiftKeyDown() && this.claims.hasPermission(player, VISUALIZE_NEARBY_PERMISSION, true))
        {
            int found = this.visualization.visualizeNearbyClaims(player, level);
            this.feedback.send(player, TextMode.INFO, MessageKey.SHOW_NEARBY_CLAIMS, String.valueOf(found));
            return;
        }
        if (target == null)
        {
            return;
        }

        // Inspecting abandons a half-finished resize, which also clears a stuck conflict display.
        ClaimToolSession session = this.sessions.get(player.getUUID());
        if (session != null && session.mode == SessionMode.RESIZE)
        {
            this.sessions.remove(player.getUUID());
        }

        ClaimSnapshot claim = this.claims.findClaimAt(level, target);
        if (claim == null)
        {
            this.feedback.send(player, TextMode.INFO, MessageKey.BLOCK_NOT_CLAIMED);
            this.visualization.clear(player);
            return;
        }

        this.feedback.send(player, TextMode.INFO, MessageKey.BLOCK_CLAIMED, this.feedback.ownerName(player, claim));
        this.visualization.visualizeClaim(player, level, claim, target);
        if (this.claims.hasPermission(player, SEE_CLAIM_SIZE_PERMISSION, FabricClaimRepository.isOperator(player)))
        {
            ClaimBounds bounds = claim.bounds();
            this.feedback.sendText(player, TextMode.INFO,
                    "  " + bounds.xLength() + "x" + bounds.zLength() + "=" + bounds.area());
        }
        showOwnerInactivity(player, claim);
    }

    /**
     * Tells staff how long ago a claim's owner was last online, as Paper does for holders of
     * {@code griefprevention.deleteclaims} or {@code griefprevention.seeinactivity}. Owners who have
     * not been seen since the store began are left out rather than shown as decades away.
     */
    private void showOwnerInactivity(@NotNull ServerPlayer player, @NotNull ClaimSnapshot claim)
    {
        UUID ownerId = this.claims.effectiveOwnerId(claim);
        if (ownerId == null
                || !(this.claims.hasPermission(player, FabricPermissionDefaults.DELETE_CLAIMS)
                || this.claims.hasPermission(player, FabricPermissionDefaults.SEE_INACTIVITY)))
        {
            return;
        }

        long now = System.currentTimeMillis();
        MinecraftServer server = player.level().getServer();
        Long seen = server != null && server.getPlayerList().getPlayer(ownerId) != null
                ? Long.valueOf(now)
                : this.lastSeen.lastSeen(ownerId);
        if (seen != null)
        {
            long daysElapsed = Math.max(0L, now - seen) / MILLIS_PER_DAY;
            this.feedback.send(player, TextMode.INFO, MessageKey.PLAYER_OFFLINE_TIME, String.valueOf(daysElapsed));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Modification tool
    // ---------------------------------------------------------------------------------------------

    private void modify(@NotNull ServerPlayer player, @NotNull ServerLevel level, @NotNull BlockPos clicked)
    {
        if (!this.settings.tools().claimsEnabled(this.claims.worldKey(level)))
        {
            this.feedback.sendError(player, MessageKey.CLAIMS_DISABLED_WORLD);
            return;
        }
        if (!this.claims.hasPermission(player, CREATE_CLAIMS_PERMISSION, true))
        {
            this.feedback.sendRateLimitedError(player, MessageKey.NO_CREATE_CLAIM_PERMISSION);
            return;
        }

        ClaimToolSession session = this.sessions.get(player.getUUID());
        if (session != null && !session.worldKey.equals(this.claims.worldKey(level)))
        {
            // A corner set in another world cannot pair with this one; start over from here.
            this.sessions.remove(player.getUUID());
            session = null;
        }
        if (session != null)
        {
            if (session.mode == SessionMode.CREATE)
            {
                finishCreate(player, level, session, clicked);
            }
            else
            {
                finishResize(player, level, session, clicked);
            }
            return;
        }

        ClaimSnapshot claim = this.claims.findClaimAt(level, clicked);
        if (claim == null)
        {
            startCreate(player, level, clicked);
            return;
        }

        if (!canModify(player, claim))
        {
            this.feedback.sendError(player, MessageKey.CREATE_CLAIM_FAIL_OVERLAP_OTHER_PLAYER,
                    this.feedback.ownerName(player, claim));
            this.visualization.visualizeConflictBounds(player, level, claim.bounds(), clicked);
            return;
        }

        CornerSelection corner = cornerSelection(claim, clicked);
        if (corner != null)
        {
            startResize(player, level, claim, corner, clicked);
            return;
        }

        this.feedback.sendError(player, MessageKey.CREATE_CLAIM_FAIL_OVERLAP);
        this.visualization.visualizeConflictBounds(player, level, claim.bounds(), clicked);
    }

    private void startCreate(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull BlockPos clicked)
    {
        // Administrative claims are free and count toward nobody's limit.
        if (!this.modes.createsAdminClaims(player, this.claims) && this.claims.hasReachedClaimCountLimit(player))
        {
            this.visualization.clear(player);
            this.feedback.sendError(player, MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT);
            return;
        }

        this.sessions.put(player.getUUID(), ClaimToolSession.create(this.claims.worldKey(level), clicked));
        this.visualization.visualizeInitializeBounds(player, level, create2DBounds(level, clicked, clicked), clicked);
        this.feedback.send(player, TextMode.INSTRUCTION, MessageKey.CLAIM_START);
    }

    private void finishCreate(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimToolSession session,
            @NotNull BlockPos clicked)
    {
        ClaimBounds bounds = create2DBounds(level, session.firstCorner, clicked);
        int minimumWidth = this.settings.tools().minimumWidth();
        if (bounds.xLength() < minimumWidth || bounds.zLength() < minimumWidth)
        {
            // Paper stays quiet for a 1-wide selection: it is the second event of a double click.
            if (bounds.xLength() != 1 && bounds.zLength() != 1)
            {
                this.feedback.sendError(player, MessageKey.NEW_CLAIM_TOO_NARROW, String.valueOf(minimumWidth));
            }
            return;
        }
        int minimumArea = this.settings.tools().minimumArea();
        if (bounds.area() < minimumArea)
        {
            this.feedback.sendError(player, MessageKey.RESIZE_CLAIM_INSUFFICIENT_AREA, String.valueOf(minimumArea));
            return;
        }

        try
        {
            boolean adminClaim = this.modes.createsAdminClaims(player, this.claims);
            FabricClaimRepository.CreateClaimResult result = this.claims.createClaim(
                    level,
                    session.firstCorner,
                    clicked,
                    adminClaim ? null : player.getUUID(),
                    player);
            if (result.hasReachedClaimCountLimit())
            {
                this.sessions.remove(player.getUUID());
                this.visualization.clear(player);
                this.feedback.sendError(player, MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT);
                return;
            }
            if (result.hasInsufficientClaimBlocks())
            {
                this.visualization.visualizeInitializeBounds(player, level, bounds, clicked);
                this.feedback.sendError(
                        player,
                        MessageKey.CREATE_CLAIM_INSUFFICIENT_BLOCKS,
                        String.valueOf(result.blocksNeeded()));
                return;
            }
            ClaimSnapshot created = result.createdClaim();
            if (created == null)
            {
                ClaimSnapshot overlapping = result.overlappingClaim();
                this.visualization.visualizeConflictBounds(
                        player, level, overlapping == null ? bounds : overlapping.bounds(), clicked);
                this.feedback.sendError(player, MessageKey.CREATE_CLAIM_FAIL_OVERLAP_SHORT);
                return;
            }

            this.sessions.remove(player.getUUID());
            this.feedback.send(player, TextMode.SUCCESS, MessageKey.CREATE_CLAIM_SUCCESS);
            this.visualization.visualizeClaim(player, level, created, clicked);
        }
        catch (IOException e)
        {
            player.sendSystemMessage(Component.literal("Could not save claim: " + e.getMessage()), true);
        }
    }

    private void startResize(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot claim,
            @NotNull CornerSelection corner,
            @NotNull BlockPos clicked)
    {
        Long id = claim.id();
        if (id == null)
        {
            return;
        }

        this.sessions.put(player.getUUID(), ClaimToolSession.resize(this.claims.worldKey(level), id, claim.bounds(), corner));
        this.visualization.visualizeClaim(player, level, claim, clicked);
        this.feedback.send(player, TextMode.INSTRUCTION, MessageKey.RESIZE_START);
    }

    private void finishResize(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimToolSession session,
            @NotNull BlockPos clicked)
    {
        ClaimBounds bounds = resizedBounds(level, session, clicked);
        int minimumWidth = this.settings.tools().minimumWidth();
        if (bounds.xLength() < minimumWidth || bounds.zLength() < minimumWidth)
        {
            this.visualization.visualizeInitializeBounds(player, level, bounds, clicked);
            this.feedback.sendError(player, MessageKey.RESIZE_CLAIM_TOO_NARROW, String.valueOf(minimumWidth));
            return;
        }
        int minimumArea = this.settings.tools().minimumArea();
        if (bounds.area() < minimumArea)
        {
            this.visualization.visualizeInitializeBounds(player, level, bounds, clicked);
            this.feedback.sendError(player, MessageKey.RESIZE_CLAIM_INSUFFICIENT_AREA, String.valueOf(minimumArea));
            return;
        }

        try
        {
            FabricClaimRepository.UpdateClaimResult result = this.claims.updateClaimBounds(session.claimId, bounds, player);
            if (result.isMissing())
            {
                this.sessions.remove(player.getUUID());
                this.visualization.clear(player);
                return;
            }
            if (result.hasInsufficientClaimBlocks())
            {
                this.visualization.visualizeInitializeBounds(player, level, bounds, clicked);
                this.feedback.sendError(
                        player,
                        MessageKey.RESIZE_NEED_MORE_BLOCKS,
                        String.valueOf(result.blocksNeeded()));
                return;
            }
            ClaimSnapshot updated = result.updatedClaim();
            if (updated == null)
            {
                ClaimSnapshot overlapping = result.overlappingClaim();
                this.visualization.visualizeConflictBounds(
                        player, level, overlapping == null ? bounds : overlapping.bounds(), clicked);
                this.feedback.sendError(player, MessageKey.RESIZE_FAIL_OVERLAP);
                return;
            }

            this.sessions.remove(player.getUUID());
            // An administrative claim costs nothing, so report the resizer's own balance, as Paper does.
            Integer remaining = result.remainingBlocks();
            if (remaining == null)
            {
                remaining = this.claims.claimBlockBalance(player.getUUID()).remaining();
            }
            this.feedback.send(player, TextMode.SUCCESS, MessageKey.CLAIM_RESIZE_SUCCESS, String.valueOf(remaining));
            this.visualization.visualizeClaim(player, level, updated, clicked);
        }
        catch (IOException e)
        {
            player.sendSystemMessage(Component.literal("Could not save resized claim: " + e.getMessage()), true);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Taking the modification tool in hand
    // ---------------------------------------------------------------------------------------------

    /**
     * Watches every player's main hand. Taking the shovel out tells the player their remaining
     * claim blocks and shows the claim they stand in; putting it away cancels a half-set claim,
     * which is what the claim-start instructions promise.
     */
    private void trackModificationTool(@NotNull MinecraftServer server)
    {
        this.tick++;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            UUID playerId = player.getUUID();
            Long equippedAt = this.modificationToolEquippedAt.get(playerId);
            if (!this.settings.isModificationTool(player.getMainHandItem()))
            {
                if (equippedAt != null)
                {
                    this.modificationToolEquippedAt.remove(playerId);
                    this.sessions.remove(playerId);
                }
                continue;
            }

            if (equippedAt == null)
            {
                this.modificationToolEquippedAt.put(playerId, this.tick);
            }
            else if (this.tick - equippedAt == EQUIP_DELAY_TICKS)
            {
                onModificationToolEquipped(player);
            }
        }
    }

    private void onModificationToolEquipped(@NotNull ServerPlayer player)
    {
        this.sessions.remove(player.getUUID());
        try
        {
            int remaining = this.claims.claimBlockBalance(player.getUUID()).remaining();
            this.feedback.send(player, TextMode.INSTRUCTION, MessageKey.REMAINING_BLOCKS, String.valueOf(remaining));
        }
        catch (IOException exception)
        {
            this.logger.warn("Could not read the claim-block balance of {}.", player.getUUID(), exception);
        }

        ServerLevel level = (ServerLevel) player.level();
        ClaimSnapshot standingIn = this.claims.findClaimAt(level, player.blockPosition());
        if (standingIn != null && canModify(player, standingIn))
        {
            this.visualization.visualizeClaim(player, level, standingIn, player.blockPosition());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Paper only answers the main hand, so a tool in the off hand never double-fires. */
    private @Nullable ClaimTool claimTool(@NotNull Player player, @NotNull InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return null;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (this.settings.isInvestigationTool(stack))
        {
            return ClaimTool.INVESTIGATION;
        }
        if (this.settings.isModificationTool(stack))
        {
            return ClaimTool.MODIFICATION;
        }
        return null;
    }

    private static @NotNull ClaimBounds create2DBounds(
            @NotNull ServerLevel level,
            @NotNull BlockPos first,
            @NotNull BlockPos second)
    {
        return ClaimBounds.rectangle(
                first.getX(),
                level.getMinY(),
                first.getZ(),
                second.getX(),
                level.getMaxY(),
                second.getZ());
    }

    private static @NotNull ClaimBounds resizedBounds(
            @NotNull ServerLevel level,
            @NotNull ClaimToolSession session,
            @NotNull BlockPos clicked)
    {
        ClaimBounds original = session.originalBounds;
        CornerSelection corner = session.cornerSelection;
        int x1 = corner.minX ? clicked.getX() : original.minX();
        int x2 = corner.minX ? original.maxX() : clicked.getX();
        int z1 = corner.minZ ? clicked.getZ() : original.minZ();
        int z2 = corner.minZ ? original.maxZ() : clicked.getZ();
        int y1;
        int y2;
        if (corner.hasYSelection())
        {
            y1 = corner.minY() ? clicked.getY() : original.minY();
            y2 = corner.minY() ? original.maxY() : clicked.getY();
        }
        else
        {
            y1 = level.getMinY();
            y2 = level.getMaxY();
        }

        return ClaimBounds.rectangle(x1, y1, z1, x2, y2, z2);
    }

    /** The owner, or staff with {@code griefprevention.adminclaims} for an administrative claim. */
    private boolean canModify(@NotNull ServerPlayer player, @NotNull ClaimSnapshot claim)
    {
        return this.claims.allows(claim, player, ClaimTrustLevel.EDIT);
    }

    private static @Nullable CornerSelection cornerSelection(
            @NotNull ClaimSnapshot claim,
            @NotNull BlockPos clicked)
    {
        ClaimBounds bounds = claim.bounds();
        Boolean minX = matchMinMax(clicked.getX(), bounds.minX(), bounds.maxX());
        Boolean minZ = matchMinMax(clicked.getZ(), bounds.minZ(), bounds.maxZ());
        if (minX == null || minZ == null)
        {
            return null;
        }

        if (!claim.threeDimensional())
        {
            return new CornerSelection(minX, minZ, null);
        }

        Boolean minY = matchMinMax(clicked.getY(), bounds.minY(), bounds.maxY());
        return minY == null ? null : new CornerSelection(minX, minZ, minY);
    }

    private static @Nullable Boolean matchMinMax(int value, int min, int max)
    {
        if (value == min)
        {
            return true;
        }
        if (value == max)
        {
            return false;
        }
        return null;
    }

    private enum ClaimTool
    {
        INVESTIGATION,
        MODIFICATION
    }

    private enum SessionMode
    {
        CREATE,
        RESIZE
    }

    private static final class ClaimToolSession
    {
        private final @NotNull SessionMode mode;
        private final @NotNull String worldKey;
        private final @NotNull BlockPos firstCorner;
        private final long claimId;
        private final @NotNull ClaimBounds originalBounds;
        private final @NotNull CornerSelection cornerSelection;

        private ClaimToolSession(
                @NotNull SessionMode mode,
                @NotNull String worldKey,
                @NotNull BlockPos firstCorner,
                long claimId,
                @NotNull ClaimBounds originalBounds,
                @NotNull CornerSelection cornerSelection)
        {
            this.mode = mode;
            this.worldKey = worldKey;
            this.firstCorner = firstCorner;
            this.claimId = claimId;
            this.originalBounds = originalBounds;
            this.cornerSelection = cornerSelection;
        }

        private static @NotNull ClaimToolSession create(
                @NotNull String worldKey,
                @NotNull BlockPos firstCorner)
        {
            return new ClaimToolSession(
                    SessionMode.CREATE,
                    worldKey,
                    firstCorner.immutable(),
                    -1L,
                    ClaimBounds.rectangle(0, 0, 0, 0, 0, 0),
                    new CornerSelection(true, true, null));
        }

        private static @NotNull ClaimToolSession resize(
                @NotNull String worldKey,
                long claimId,
                @NotNull ClaimBounds originalBounds,
                @NotNull CornerSelection cornerSelection)
        {
            return new ClaimToolSession(
                    SessionMode.RESIZE,
                    worldKey,
                    BlockPos.ZERO,
                    claimId,
                    originalBounds,
                    cornerSelection);
        }
    }

    private static final class CornerSelection
    {
        private final boolean minX;
        private final boolean minZ;
        private final @Nullable Boolean minY;

        private CornerSelection(boolean minX, boolean minZ, @Nullable Boolean minY)
        {
            this.minX = minX;
            this.minZ = minZ;
            this.minY = minY;
        }

        private boolean hasYSelection()
        {
            return this.minY != null;
        }

        private boolean minY()
        {
            return Boolean.TRUE.equals(this.minY);
        }
    }
}
