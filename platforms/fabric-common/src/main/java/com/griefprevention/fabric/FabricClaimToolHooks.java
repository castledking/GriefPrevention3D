package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.editor.ClaimEditIntent;
import com.griefprevention.claims.editor.ClaimEditIntentType;
import com.griefprevention.claims.editor.ClaimEditPreview;
import com.griefprevention.claims.editor.ClaimEditSource;
import com.griefprevention.claims.editor.ClaimEditTarget;
import com.griefprevention.claims.editor.ClaimEditTargetType;
import com.griefprevention.claims.editor.ClaimEditor;
import com.griefprevention.claims.editor.ClaimEditorMode;
import com.griefprevention.claims.editor.ClaimEditorSession;
import com.griefprevention.claims.editor.ClaimEditorSkeleton;
import com.griefprevention.claims.editor.ClaimExtension;
import com.griefprevention.claims.editor.ShapedPathDraft;
import com.griefprevention.geometry.OrthogonalDirection;
import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    /** How far past the view line a claim must reach before its corners can no longer be aimed at. */
    private static final double CORNER_VIEW_MARGIN = 2.0D;
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
    private final FabricClaimResizer resizer;
    private final Logger logger;
    private final ClaimEditor editor = new ClaimEditorSkeleton();
    private final Map<UUID, ClaimToolSession> sessions = new HashMap<>();
    private final Map<UUID, ClaimEditorSession> editorSessions = new HashMap<>();
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
            @NotNull FabricClaimResizer resizer,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.visualization = visualization;
        this.feedback = feedback;
        this.settings = settings;
        this.modes = modes;
        this.lastSeen = lastSeen;
        this.resizer = resizer;
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
            this.editorSessions.remove(playerId);
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

        BlockPos clicked = hitResult.getBlockPos();
        if (tool == ClaimTool.MODIFICATION)
        {
            BlockPos corner = snapToClaimCorner(serverPlayer, serverLevel, clicked);
            if (corner != null)
            {
                clicked = corner;
            }
        }

        return useTool(tool, serverPlayer, serverLevel, clicked);
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

        // A corner of a claim is often water or air, so the pick can miss it altogether, or land
        // on the seabed behind it. Looking for the corner the player aims at keeps those clicks on
        // the claim, as on Paper.
        if (tool == ClaimTool.MODIFICATION)
        {
            BlockPos corner = snapToClaimCorner(serverPlayer, serverLevel, target);
            if (corner != null)
            {
                target = corner;
            }
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

        if (this.modes.mode(player.getUUID()) == FabricClaimModes.Mode.SHAPED)
        {
            modifyShaped(player, level, clicked);
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
            switch (session.mode)
            {
                case CREATE -> finishCreate(player, level, session, clicked);
                case SUBDIVIDE -> finishSubdivision(player, level, session, clicked);
                default -> finishResize(player, level, session, clicked);
            }
            return;
        }

        ClaimSnapshot claim = this.claims.findClaimAt(level, clicked);
        if (claim == null)
        {
            startCreate(player, level, clicked);
            return;
        }

        // As on Paper, managers may subdivide a claim they cannot reshape.
        boolean subdividing = this.modes.mode(player.getUUID()).subdivides();
        boolean canEdit = canModify(player, claim);
        if (!canEdit && !(subdividing && this.claims.allows(claim, player, ClaimTrustLevel.MANAGE)))
        {
            this.feedback.sendError(player, MessageKey.CREATE_CLAIM_FAIL_OVERLAP_OTHER_PLAYER,
                    this.feedback.ownerName(player, claim));
            this.visualization.visualizeConflictBounds(player, level, claim.bounds(), clicked);
            return;
        }

        // Sneaking at a corner in subdivision mode starts a subdivision there instead of a resize.
        CornerSelection corner = canEdit && !(subdividing && player.isShiftKeyDown()) ? cornerSelection(claim, clicked) : null;
        if (corner != null)
        {
            startResize(player, level, claim, corner, clicked);
            return;
        }
        if (subdividing)
        {
            startSubdivision(player, level, claim, clicked);
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

    /**
     * Draws a shaped claim with the shared orthogonal editor. Right-click corners in order, then
     * click the first corner again to close the outline. Clicking an existing 2D claim boundary
     * starts a reshape instead.
     */
    private void modifyShaped(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull BlockPos clicked)
    {
        if (!this.settings.tools().shaped().allowShapedClaims())
        {
            this.feedback.sendError(player, MessageKey.SHAPED_CLAIMS_DISABLED);
            this.editorSessions.remove(player.getUUID());
            return;
        }

        ClaimEditorSession session = this.editorSessions.get(player.getUUID());
        if (session != null && session.openPath() != null)
        {
            Long targetId = session.activeTarget() == null ? null : session.activeTarget().claimId();
            ClaimSnapshot target = targetId == null ? null : this.claims.claimById(targetId);
            if (targetId != null && (target == null || !target.worldKey().equals(this.claims.worldKey(level))))
            {
                this.editorSessions.remove(player.getUUID());
                this.visualization.clear(player);
                this.feedback.sendError(player, MessageKey.SCROLL_RESIZE_CLAIM_GONE);
                return;
            }
            addShapedCorner(player, level, session, target, clicked);
            return;
        }

        ClaimSnapshot claim = this.claims.findClaimAt(level, clicked);
        if (claim == null)
        {
            if (this.claims.hasReachedClaimCountLimit(player))
            {
                this.visualization.clear(player);
                this.feedback.sendError(player, MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT);
                return;
            }
            session = ClaimEditorSession.idle(player.getUUID())
                    .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.TOOL)
                    .withTarget(new ClaimEditTarget(ClaimEditTargetType.NEW_PARENT_CLAIM, null));
            addShapedCorner(player, level, session, null, clicked);
            return;
        }

        if (!canModify(player, claim))
        {
            this.feedback.sendError(player, MessageKey.NOT_YOUR_CLAIM);
            this.visualization.visualizeConflictBounds(player, level, claim.bounds(), clicked);
            return;
        }
        if (claim.parentId() != null && !this.settings.tools().shaped().allowShapedSubdivisions())
        {
            this.feedback.sendError(player, MessageKey.SHAPED_SUB_CLAIMS_DISABLED);
            this.visualization.visualizeClaim(player, level, claim, clicked);
            return;
        }
        if (claim.threeDimensional())
        {
            this.feedback.sendError(player, MessageKey.SHAPED_CLAIMS_2D_ONLY);
            this.visualization.visualizeClaim(player, level, claim, clicked);
            return;
        }

        ClaimBounds bounds = claim.bounds();
        OrthogonalPolygon polygon = bounds.isShaped()
                ? bounds.polygon()
                : OrthogonalPolygon.fromRectangle(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        ClaimEditTargetType targetType = claim.parentId() == null
                ? ClaimEditTargetType.EXISTING_PARENT_CLAIM
                : ClaimEditTargetType.EXISTING_SUBDIVISION_CLAIM;
        session = ClaimEditorSession.idle(player.getUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.TOOL)
                .withTarget(new ClaimEditTarget(targetType, claim.id()))
                .withPreview(new ClaimEditPreview(
                        polygon, null, Collections.emptyList(), null, Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList()));
        addShapedCorner(player, level, session, claim, clicked);
    }

    private void addShapedCorner(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimEditorSession session,
            @Nullable ClaimSnapshot target,
            @NotNull BlockPos clicked)
    {
        ClaimEditIntent intent = new ClaimEditIntent(
                ClaimEditIntentType.ADD_CORNER,
                ClaimEditSource.TOOL,
                null,
                session.activeTarget() == null ? null : session.activeTarget().claimId(),
                new OrthogonalPoint2i(clicked.getX(), clicked.getZ()),
                null,
                true,
                Collections.emptyList());
        com.griefprevention.claims.editor.ClaimEditResult result = this.editor.apply(session, intent);
        this.editorSessions.put(player.getUUID(), result.session());

        if (!result.success())
        {
            for (String message : result.messages())
            {
                this.feedback.sendText(player, TextMode.ERROR, message);
            }
            visualizeShapedDraft(player, level, result.session(), target, clicked.getY());
            return;
        }

        ShapedPathDraft path = result.session().openPath();
        if (path == null)
        {
            return;
        }
        if (result.preview().polygon() != null && path.closureReady())
        {
            commitShapedEdit(player, level, result.session(), target, result.preview().polygon(), clicked);
            return;
        }

        for (String message : result.messages())
        {
            this.feedback.send(player, TextMode.INSTRUCTION, MessageKey.SHAPED_CLAIM_EDIT_MESSAGE, message);
        }
        visualizeShapedDraft(player, level, result.session(), target, clicked.getY());
    }

    private void commitShapedEdit(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimEditorSession session,
            @Nullable ClaimSnapshot target,
            @NotNull OrthogonalPolygon polygon,
            @NotNull BlockPos clicked)
    {
        ClaimEditTarget editTarget = session.activeTarget();
        if (editTarget == null || editTarget.type() == ClaimEditTargetType.NEW_PARENT_CLAIM)
        {
            if (this.claims.hasReachedClaimCountLimit(player))
            {
                this.feedback.sendError(player, MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT);
                visualizeShapedDraft(player, level, session, null, clicked.getY());
                return;
            }

            com.griefprevention.claims.ShapedClaimSettings.Shortfall shortfall =
                    this.settings.tools().shaped().creationShortfall(
                            polygon,
                            this.settings.tools().minimumWidth(),
                            this.settings.tools().minimumArea());
            if (shortfall != null)
            {
                sendShapedShortfall(player, shortfall);
                visualizeShapedDraft(player, level, session, null, clicked.getY());
                return;
            }

            try
            {
                FabricClaimRepository.CreateClaimResult created = this.claims.createClaim(
                        this.claims.worldKey(level),
                        ClaimBounds.shaped(polygon, level.getMinY(), level.getMaxY()),
                        player.getUUID(),
                        player);
                if (created.hasReachedClaimCountLimit())
                {
                    this.feedback.sendError(player, MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT);
                    return;
                }
                if (created.hasInsufficientClaimBlocks())
                {
                    this.feedback.sendError(player, MessageKey.CREATE_CLAIM_INSUFFICIENT_BLOCKS,
                            String.valueOf(created.blocksNeeded()));
                    visualizeShapedDraft(player, level, session, null, clicked.getY());
                    return;
                }
                ClaimSnapshot claim = created.createdClaim();
                if (claim == null)
                {
                    ClaimSnapshot overlapping = created.overlappingClaim();
                    this.feedback.sendError(player, MessageKey.CREATE_CLAIM_FAIL_OVERLAP_SHORT);
                    this.visualization.visualizeConflictBounds(
                            player, level, overlapping == null ? ClaimBounds.shaped(polygon, level.getMinY(), level.getMaxY())
                                    : overlapping.bounds(), clicked);
                    return;
                }

                this.feedback.send(player, TextMode.SUCCESS, MessageKey.SHAPED_CLAIM_SUCCESS);
                this.editorSessions.put(player.getUUID(), completedShapedSession(session, claim, polygon));
                this.visualization.visualizeClaim(player, level, claim, clicked);
            }
            catch (IOException exception)
            {
                player.sendSystemMessage(Component.literal("Could not save shaped claim: " + exception.getMessage()), true);
                visualizeShapedDraft(player, level, session, null, clicked.getY());
            }
            return;
        }

        Long claimId = editTarget.claimId();
        ClaimSnapshot existing = claimId == null ? target : this.claims.claimById(claimId);
        if (existing == null)
        {
            this.editorSessions.remove(player.getUUID());
            this.feedback.sendError(player, MessageKey.SCROLL_RESIZE_CLAIM_GONE);
            this.visualization.clear(player);
            return;
        }
        if (!canModify(player, existing) || existing.threeDimensional())
        {
            this.feedback.sendError(player, existing.threeDimensional()
                    ? MessageKey.SHAPED_CLAIMS_2D_ONLY : MessageKey.NOT_YOUR_CLAIM);
            visualizeShapedDraft(player, level, session, existing, clicked.getY());
            return;
        }

        if (existing.parentId() == null
                && !existing.adminClaim()
                && !this.claims.hasPermission(player, FabricPermissionDefaults.ADMIN_CLAIMS))
        {
            com.griefprevention.claims.ShapedClaimSettings.Shortfall shortfall =
                    this.settings.tools().shaped().resizeShortfall(
                            polygon,
                            this.settings.tools().minimumWidth(),
                            this.settings.tools().minimumArea(),
                            existing.bounds().area());
            if (shortfall != null)
            {
                sendShapedShortfall(player, shortfall);
                visualizeShapedDraft(player, level, session, existing, clicked.getY());
                return;
            }
        }

        try
        {
            FabricClaimRepository.UpdateClaimResult updated = this.claims.updateClaimBounds(
                    claimId == null ? existing.id() : claimId,
                    ClaimBounds.shaped(polygon, existing.bounds().minY(), existing.bounds().maxY()),
                    player);
            if (updated.isMissing())
            {
                this.editorSessions.remove(player.getUUID());
                this.feedback.sendError(player, MessageKey.SCROLL_RESIZE_CLAIM_GONE);
                this.visualization.clear(player);
                return;
            }
            if (updated.hasInsufficientClaimBlocks())
            {
                this.feedback.sendError(player, MessageKey.RESIZE_NEED_MORE_BLOCKS,
                        String.valueOf(updated.blocksNeeded()));
                visualizeShapedDraft(player, level, session, existing, clicked.getY());
                return;
            }
            if (updated.placementProblem() != null)
            {
                MessageKey message = switch (updated.placementProblem())
                {
                    case OUTSIDE_PARENT -> MessageKey.RESIZE_FAIL_SUBDIVISION_EXCEEDS_PARENT;
                    case OVERLAPS_SIBLING -> MessageKey.CREATE_SUBDIVISION_OVERLAP;
                    case EXCLUDES_SUBDIVISION -> MessageKey.RESIZE_FAIL_SUBDIVISION;
                };
                this.feedback.sendError(player, message);
                ClaimSnapshot conflicting = updated.overlappingClaim();
                this.visualization.visualizeConflictBounds(player, level,
                        conflicting == null ? ClaimBounds.shaped(polygon, existing.bounds().minY(), existing.bounds().maxY())
                                : conflicting.bounds(), clicked);
                return;
            }
            ClaimSnapshot resized = updated.updatedClaim();
            if (resized == null)
            {
                ClaimSnapshot overlapping = updated.overlappingClaim();
                this.feedback.sendError(player, MessageKey.CREATE_CLAIM_FAIL_OVERLAP_SHORT);
                this.visualization.visualizeConflictBounds(player, level,
                        overlapping == null ? ClaimBounds.shaped(polygon, existing.bounds().minY(), existing.bounds().maxY())
                                : overlapping.bounds(), clicked);
                return;
            }

            Integer remaining = updated.remainingBlocks();
            if (remaining == null)
            {
                try
                {
                    remaining = this.claims.claimBlockBalance(player.getUUID()).remaining();
                }
                catch (IOException ignored)
                {
                    // The shape was saved; omitting the optional remaining-balance suffix is safe.
                }
            }
            if (remaining == null)
            {
                this.feedback.send(player, TextMode.SUCCESS, MessageKey.SHAPED_CLAIM_RESIZED);
            }
            else
            {
                this.feedback.send(player, TextMode.SUCCESS, MessageKey.CLAIM_RESIZE_SUCCESS,
                        String.valueOf(remaining));
            }
            this.editorSessions.put(player.getUUID(), completedShapedSession(session, resized, polygon));
            this.visualization.visualizeClaim(player, level, resized, clicked);
        }
        catch (IOException exception)
        {
            player.sendSystemMessage(Component.literal("Could not save shaped claim: " + exception.getMessage()), true);
            visualizeShapedDraft(player, level, session, existing, clicked.getY());
        }
    }

    private void sendShapedShortfall(
            @NotNull ServerPlayer player,
            @NotNull com.griefprevention.claims.ShapedClaimSettings.Shortfall shortfall)
    {
        if (shortfall.rule() == com.griefprevention.claims.ShapedClaimSettings.Shortfall.Rule.TOO_NARROW)
        {
            this.feedback.sendError(player, MessageKey.NEW_CLAIM_TOO_NARROW, String.valueOf(shortfall.minimum()));
        }
        else
        {
            this.feedback.sendError(player, MessageKey.RESIZE_CLAIM_INSUFFICIENT_AREA,
                    String.valueOf(shortfall.minimum()));
        }
    }

    private void visualizeShapedDraft(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimEditorSession session,
            @Nullable ClaimSnapshot target,
            int y)
    {
        List<OrthogonalPoint2i> points = session.openPath() == null
                ? Collections.emptyList()
                : session.openPath().points();
        if (points.isEmpty())
        {
            if (target == null)
            {
                this.visualization.clear(player);
            }
            else
            {
                this.visualization.visualizeClaim(player, level, target, player.blockPosition());
            }
            return;
        }
        Long baseClaimId = session.activeTarget() == null ? null : session.activeTarget().claimId();
        this.visualization.visualizeShapedDraft(player, level, baseClaimId, points, y);
    }

    private static @NotNull ClaimEditorSession completedShapedSession(
            @NotNull ClaimEditorSession session,
            @NotNull ClaimSnapshot claim,
            @NotNull OrthogonalPolygon polygon)
    {
        ClaimEditTargetType targetType = claim.parentId() == null
                ? ClaimEditTargetType.EXISTING_PARENT_CLAIM
                : ClaimEditTargetType.EXISTING_SUBDIVISION_CLAIM;
        return session
                .withTarget(new ClaimEditTarget(targetType, claim.id()))
                .withOpenPath(null)
                .withPreview(new ClaimEditPreview(
                        polygon, session.activeSegment(), Collections.emptyList(), null, Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList()));
    }

    private void startSubdivision(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot claim,
            @NotNull BlockPos clicked)
    {
        boolean threeDimensional = this.modes.mode(player.getUUID()) == FabricClaimModes.Mode.SUBDIVIDE_3D;
        if (threeDimensional && !this.settings.tools().allow3DSubdivisions())
        {
            this.feedback.sendError(player, MessageKey.SUBDIVISIONS_3D_DISABLED);
            return;
        }
        // Paper nests subdivisions only with AllowNestedSubClaims, which Fabric does not offer yet.
        if (claim.parentId() != null || claim.id() == null)
        {
            this.feedback.sendError(player, MessageKey.RESIZE_FAIL_OVERLAP_SUBDIVISION);
            this.visualization.visualizeConflictBounds(player, level, claim.bounds(), clicked);
            return;
        }

        this.sessions.put(player.getUUID(),
                ClaimToolSession.subdivide(this.claims.worldKey(level), claim.id(), clicked, threeDimensional));
        ClaimBounds start = threeDimensional
                ? ClaimBounds.rectangle(clicked.getX(), clicked.getY(), clicked.getZ(), clicked.getX(), clicked.getY(), clicked.getZ())
                : create2DBounds(level, clicked, clicked);
        this.visualization.visualizeInitializeBounds(player, level, start, clicked, threeDimensional);
        this.feedback.send(player, TextMode.INSTRUCTION, MessageKey.SUBDIVISION_START);
    }

    private void finishSubdivision(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimToolSession session,
            @NotNull BlockPos clicked)
    {
        ClaimSnapshot parent = this.claims.claimById(session.claimId);
        if (parent == null)
        {
            this.sessions.remove(player.getUUID());
            this.visualization.clear(player);
            return;
        }
        BlockPos first = session.firstCorner;
        // A 2D subdivision reaches from its parent's floor to the top of the world, as on Paper.
        ClaimBounds bounds = session.threeDimensional
                ? ClaimBounds.rectangle(first.getX(), first.getY(), first.getZ(), clicked.getX(), clicked.getY(), clicked.getZ())
                : ClaimBounds.rectangle(first.getX(), parent.bounds().minY(), first.getZ(),
                        clicked.getX(), level.getMaxY(), clicked.getZ());
        try
        {
            FabricClaimRepository.SubdivisionResult result =
                    this.claims.createSubdivision(session.claimId, bounds, session.threeDimensional, player);
            ClaimSnapshot created = result.createdClaim();
            if (created == null)
            {
                ClaimSnapshot conflicting = result.conflictingClaim();
                if (result.problem() == FabricClaimRepository.PlacementProblem.OVERLAPS_SIBLING && conflicting != null)
                {
                    this.feedback.sendError(player, MessageKey.CREATE_SUBDIVISION_OVERLAP);
                    this.visualization.visualizeConflictBounds(player, level, conflicting.bounds(), clicked);
                }
                else
                {
                    this.feedback.sendError(player, MessageKey.CREATE_CLAIM_FAIL_OVERLAP_REGION);
                    this.visualization.visualizeConflictBounds(player, level, bounds, clicked);
                }
                return;
            }

            this.sessions.remove(player.getUUID());
            this.feedback.send(player, TextMode.SUCCESS, MessageKey.SUBDIVISION_SUCCESS);
            this.visualization.visualizeClaim(player, level, created, clicked);
        }
        catch (IOException e)
        {
            player.sendSystemMessage(Component.literal("Could not save subdivision: " + e.getMessage()), true);
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

        this.sessions.put(player.getUUID(),
                ClaimToolSession.resize(this.claims.worldKey(level), id, claim.bounds(), corner, clicked));
        this.visualization.visualizeClaim(player, level, claim, clicked);
        this.feedback.send(player, TextMode.INSTRUCTION, MessageKey.RESIZE_START);
    }

    private void finishResize(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimToolSession session,
            @NotNull BlockPos clicked)
    {
        if (this.resizer.resize(player, level, session.claimId, resizedBounds(session, clicked), clicked))
        {
            this.sessions.remove(player.getUUID());
        }
    }

    /**
     * The corner a player selected for a resize by right-clicking it, which {@code /scrollresize}
     * limits itself to, as Paper's {@code claimResizing} and {@code lastShovelLocation}.
     */
    @Nullable SelectedCorner selectedCorner(@NotNull ServerPlayer player)
    {
        ClaimToolSession session = this.sessions.get(player.getUUID());
        if (session == null || session.mode != SessionMode.RESIZE
                || !session.worldKey.equals(this.claims.worldKey((ServerLevel) player.level())))
        {
            return null;
        }
        return new SelectedCorner(session.claimId, session.firstCorner);
    }

    /** The nib-selected claim, or the most specific claim at the player's current block. */
    @Nullable ClaimSnapshot selectedOrCurrentClaim(@NotNull ServerPlayer player)
    {
        SelectedCorner selection = selectedCorner(player);
        if (selection != null)
        {
            return this.claims.claimById(selection.claimId());
        }
        ClaimSnapshot current = this.claims.findClaimAt((ServerLevel) player.level(), player.blockPosition());
        if (current != null)
        {
            return current;
        }
        ClaimEditorSession editSession = this.editorSessions.get(player.getUUID());
        Long editClaimId = editSession == null || editSession.openPath() != null
                || editSession.activeTarget() == null ? null : editSession.activeTarget().claimId();
        return editClaimId == null ? null : this.claims.claimById(editClaimId);
    }

    /** Applies the shared {@code /extendclaim} geometry to the nib-selected or current claim. */
    boolean extendClaim(@NotNull ServerPlayer player, int amount)
    {
        ServerLevel level = (ServerLevel) player.level();
        if (!this.settings.tools().claimsEnabled(this.claims.worldKey(level)))
        {
            this.feedback.sendError(player, MessageKey.CLAIMS_DISABLED_WORLD);
            return true;
        }
        if (!player.getAbilities().instabuild
                && !this.settings.isModificationTool(player.getMainHandItem())
                && !this.claims.hasPermission(player, FabricPermissionDefaults.EXTEND_CLAIM_TOOLBYPASS))
        {
            this.feedback.sendError(player, MessageKey.MUST_HOLD_MODIFICATION_TOOL_FOR_THAT);
            return true;
        }

        ClaimSnapshot claim = selectedOrCurrentClaim(player);
        if (claim == null || claim.id() == null)
        {
            this.feedback.sendError(player, MessageKey.STAND_IN_CLAIM_TO_RESIZE);
            return true;
        }
        if (!canModify(player, claim))
        {
            this.feedback.sendError(player, MessageKey.NOT_YOUR_CLAIM);
            return true;
        }

        if (claim.bounds().isShaped())
        {
            return extendShapedClaim(player, level, claim, amount);
        }

        Vec3 view = player.getViewVector(1.0F);
        ClaimExtension.RectangleExtension extension = ClaimExtension.extendRectangle(
                claim.bounds(),
                claim.threeDimensional(),
                view.x,
                view.y,
                view.z,
                amount);
        if (extension.outcome() == ClaimExtension.Outcome.ALREADY_REACHES_SKY)
        {
            this.feedback.send(player, TextMode.INFO, MessageKey.CLAIMS_EXTEND_TO_SKY);
            return true;
        }
        if (extension.outcome() == ClaimExtension.Outcome.EXTENDS_DOWNWARD_BY_ITSELF)
        {
            this.feedback.send(player, TextMode.INFO, MessageKey.CLAIMS_AUTO_EXTEND_DOWNWARD);
            return true;
        }
        if (extension.outcome() == ClaimExtension.Outcome.LIMIT_REACHED)
        {
            this.feedback.sendError(player, MessageKey.CLAIMS_EXTEND_LIMIT);
            return true;
        }

        ClaimBounds updated = extension.bounds();
        if (claim.threeDimensional() && claim.parentId() != null)
        {
            ClaimSnapshot parent = this.claims.claimById(claim.parentId());
            if (parent == null || updated.minY() < parent.bounds().minY()
                    || updated.maxY() > parent.bounds().maxY())
            {
                this.feedback.sendError(player, MessageKey.RESIZE_FAIL_OVERLAP_SUBDIVISION);
                return true;
            }
        }

        this.resizer.resize(player, level, claim.id(), updated, player.blockPosition());
        return true;
    }

    private boolean extendShapedClaim(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot claim,
            int amount)
    {
        OrthogonalPolygon polygon = claim.bounds().polygon();
        Vec3 view = player.getViewVector(1.0F);
        Integer edgeIndex = ClaimExtension.facedSegment(
                polygon, player.getX(), player.getZ(), view.x, view.z);
        OrthogonalDirection direction = null;
        if (edgeIndex != null)
        {
            OrthogonalEdge2i edge = polygon.edges().get(edgeIndex);
            direction = ClaimExtension.expansionDirection(edge, view.x, view.z);
        }
        else
        {
            ClaimEditorSession previous = this.editorSessions.get(player.getUUID());
            if (previous != null
                    && previous.activeTarget() != null
                    && claim.id().equals(previous.activeTarget().claimId())
                    && previous.preview().polygon() != null
                    && previous.preview().polygon().corners().equals(polygon.corners())
                    && previous.activeSegment() != null
                    && previous.activeSegment().edgeIndex() >= 0
                    && previous.activeSegment().edgeIndex() < polygon.edges().size())
            {
                edgeIndex = previous.activeSegment().edgeIndex();
                direction = previous.activeSegment().outwardDirection();
            }
        }
        if (edgeIndex == null)
        {
            this.feedback.sendText(player, TextMode.ERROR,
                    "Stand inside the shaped section you want to expand and face its boundary.");
            return true;
        }

        com.griefprevention.claims.editor.ClaimEditResult result = ClaimExtension.expandSegment(
                player.getUUID(),
                claim.id(),
                claim.parentId() != null,
                polygon,
                edgeIndex,
                direction,
                amount);
        if (!result.success() || result.preview().polygon() == null)
        {
            for (String message : result.messages())
            {
                this.feedback.sendText(player, TextMode.ERROR, message);
            }
            return true;
        }

        ClaimBounds updated = ClaimBounds.shaped(
                result.preview().polygon(), claim.bounds().minY(), claim.bounds().maxY());
        boolean finished = this.resizer.resize(player, level, claim.id(), updated, player.blockPosition());
        if (finished)
        {
            ClaimSnapshot saved = this.claims.claimById(claim.id());
            if (saved != null && saved.bounds().isShaped())
            {
                this.editorSessions.put(player.getUUID(), completedShapedSession(result.session(), saved,
                        saved.bounds().polygon()));
            }
            else
            {
                this.editorSessions.remove(player.getUUID());
            }
        }
        return true;
    }

    /** Drops a half-made claim, subdivision or resize, as starting {@code /scrollresize} does on Paper. */
    void clearSelection(@NotNull ServerPlayer player)
    {
        this.sessions.remove(player.getUUID());
        this.editorSessions.remove(player.getUUID());
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
                    this.editorSessions.remove(playerId);
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
        this.editorSessions.remove(player.getUUID());
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

    /**
     * The extra raytrace Paper plays for the modification tool. A claim's corner is often water,
     * air or a gap, so a click on a faraway corner comes back as no block at all, or as the seabed
     * behind it: the click lands skewed away from the claim and a new claim starts instead of the
     * corner being selected. Looking along the view for the corner the player aims at keeps the
     * click on it, as on Paper. Half-finished selections are left alone, so the second corner of a
     * selection stays where the player clicked.
     *
     * @return the corner block the player aims at, or null when the click stays where it is
     */
    private @Nullable BlockPos snapToClaimCorner(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @Nullable BlockPos clicked)
    {
        ClaimEditorSession editorSession = this.editorSessions.get(player.getUUID());
        if (this.sessions.containsKey(player.getUUID())
                || editorSession != null && editorSession.openPath() != null)
        {
            return null;
        }

        Vec3 eye = player.getEyePosition();
        // Block interactions are resolved against a hit shape, while the helper selects block
        // centers, so one extra block keeps normal vanilla targets authoritative at the edge.
        double vanillaReach = player.blockInteractionRange() + 1.0D;
        // A vanilla block click is authoritative at normal reach. Letting the longer helper
        // raycast replace it can select a 3D subdivision corner, then the companion item-use
        // event can treat the same click's ordinary block target as the resize's second corner.
        if (clicked != null)
        {
            Vec3 clickedCenter = Vec3.atCenterOf(clicked);
            if (eye.distanceToSqr(clickedCenter) <= vanillaReach * vanillaReach)
            {
                return null;
            }
        }

        Vec3 view = player.getViewVector(1.0F);
        Vec3 far = eye.add(view.scale(TOOL_REACH));
        String worldKey = this.claims.worldKey(level);
        ClaimBounds nearView = ClaimBounds.rectangle(
                (int) Math.floor(Math.min(eye.x, far.x) - CORNER_VIEW_MARGIN),
                (int) Math.floor(Math.min(eye.y, far.y) - CORNER_VIEW_MARGIN),
                (int) Math.floor(Math.min(eye.z, far.z) - CORNER_VIEW_MARGIN),
                (int) Math.ceil(Math.max(eye.x, far.x) + CORNER_VIEW_MARGIN),
                (int) Math.ceil(Math.max(eye.y, far.y) + CORNER_VIEW_MARGIN),
                (int) Math.ceil(Math.max(eye.z, far.z) + CORNER_VIEW_MARGIN));
        // Only the claims the view line could touch, widened past the aim a corner forgives.
        List<ClaimSnapshot> candidates = new ArrayList<>(this.claims.candidates(worldKey, nearView));

        CornerRaycast.CornerHit corner = CornerRaycast.closestCorner(
                candidates,
                worldKey,
                eye,
                view,
                TOOL_REACH,
                level.getMinY(),
                level.getMaxY());
        // The helper exists for far-away corners only. A corner in vanilla reach can otherwise
        // start a resize on an air click, then the nearby block click arrives as the second corner
        // and applies the resize before the player intends it.
        if (corner == null
                || CornerRaycast.withinVanillaReach(eye, corner, vanillaReach)
                || !CornerRaycast.beatsClickedBlock(eye, view, clicked, corner.distanceAlongView()))
        {
            return null;
        }

        return corner.pos();
    }

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

    /** A 3D claim's clicked corner moves its height too; any other claim keeps its heights, as on Paper. */
    private static @NotNull ClaimBounds resizedBounds(
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
            y1 = original.minY();
            y2 = original.maxY();
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

    /** A claim and the corner of it the player clicked to resize it. */
    record SelectedCorner(long claimId, @NotNull BlockPos corner)
    {
    }

    private enum ClaimTool
    {
        INVESTIGATION,
        MODIFICATION
    }

    private enum SessionMode
    {
        CREATE,
        RESIZE,
        SUBDIVIDE
    }

    private static final class ClaimToolSession
    {
        private final @NotNull SessionMode mode;
        private final @NotNull String worldKey;
        private final @NotNull BlockPos firstCorner;
        private final long claimId;
        private final @NotNull ClaimBounds originalBounds;
        private final @NotNull CornerSelection cornerSelection;
        /** For a subdivision: whether it is height-limited. */
        private final boolean threeDimensional;

        private ClaimToolSession(
                @NotNull SessionMode mode,
                @NotNull String worldKey,
                @NotNull BlockPos firstCorner,
                long claimId,
                @NotNull ClaimBounds originalBounds,
                @NotNull CornerSelection cornerSelection,
                boolean threeDimensional)
        {
            this.mode = mode;
            this.worldKey = worldKey;
            this.firstCorner = firstCorner;
            this.claimId = claimId;
            this.originalBounds = originalBounds;
            this.cornerSelection = cornerSelection;
            this.threeDimensional = threeDimensional;
        }

        /** @param parentId the claim the subdivision goes in */
        private static @NotNull ClaimToolSession subdivide(
                @NotNull String worldKey,
                long parentId,
                @NotNull BlockPos firstCorner,
                boolean threeDimensional)
        {
            return new ClaimToolSession(
                    SessionMode.SUBDIVIDE,
                    worldKey,
                    firstCorner.immutable(),
                    parentId,
                    ClaimBounds.rectangle(0, 0, 0, 0, 0, 0),
                    new CornerSelection(true, true, null),
                    threeDimensional);
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
                    new CornerSelection(true, true, null),
                    false);
        }

        /** @param selectedCorner the corner block that was clicked to start the resize */
        private static @NotNull ClaimToolSession resize(
                @NotNull String worldKey,
                long claimId,
                @NotNull ClaimBounds originalBounds,
                @NotNull CornerSelection cornerSelection,
                @NotNull BlockPos selectedCorner)
        {
            return new ClaimToolSession(
                    SessionMode.RESIZE,
                    worldKey,
                    selectedCorner.immutable(),
                    claimId,
                    originalBounds,
                    cornerSelection,
                    false);
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
