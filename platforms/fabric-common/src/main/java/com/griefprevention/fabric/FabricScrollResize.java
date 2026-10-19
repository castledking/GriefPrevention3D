package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.editor.ScrollResizeGeometry;
import com.griefprevention.claims.editor.ScrollResizeGeometry.Face;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.fabric.mixin.InventoryAccessor;
import com.griefprevention.messages.MessageKey;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code /scrollresize}, as on Paper: while a session runs, the mouse wheel pushes the side of the
 * claim the camera points at away from the player (scroll up) or pulls it closer (scroll down), and
 * the new outline is previewed with the claims around it. Right-click applies it; left-clicking a
 * block or entity, dropping (Q) or swapping hands (F), a number key, putting the claim tool away,
 * dying, changing worlds or a minute without scrolling cancels it.
 *
 * <p>Fabric has no hotbar event, so a mixin on the hotbar packet hands each slot change here; a
 * scroll step keeps the held slot and sends it back to the client, as Bukkit does for a cancelled
 * {@code PlayerItemHeldEvent}. Shaped claims, which Fabric cannot create yet, are left to Paper.
 */
@ApiStatus.Internal
public final class FabricScrollResize
{
    private static final long IDLE_TIMEOUT_MILLIS = 60_000L;
    private static final long SCROLL_BURST_MILLIS = 250L;
    /** A right-click on a block reaches the server twice, as a block use and then an item use. */
    private static final long USE_REPEAT_MILLIS = 150L;
    /** How often the preview follows a player who walks around while it is open. */
    private static final long REDRAW_TICKS = 40L;
    private static final double TARGET_RANGE = 6.0D;
    /** How far around the outline neighboring claims are drawn. */
    private static final int NEARBY_MARGIN = 16;

    private static volatile @Nullable FabricScrollResize active;

    private final FabricClaimRepository claims;
    private final FabricFakeBlockVisualization visualization;
    private final FabricDenialFeedback feedback;
    private final FabricSettings settings;
    private final FabricClaimResizer resizer;
    private final FabricClaimToolHooks tools;
    private final Logger logger;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private long tick;

    FabricScrollResize(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricFakeBlockVisualization visualization,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricSettings settings,
            @NotNull FabricClaimResizer resizer,
            @NotNull FabricClaimToolHooks tools,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.visualization = visualization;
        this.feedback = feedback;
        this.settings = settings;
        this.resizer = resizer;
        this.tools = tools;
        this.logger = logger;
    }

    /** Registers before the claim tool and visualizations, so a running session sees each click first. */
    void register()
    {
        active = this;
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                this.sessions.remove(handler.getPlayer().getUUID()));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> cancelOnAttack(player));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> cancelOnAttack(player));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> confirmOnUse(player, hand));
        UseItemCallback.EVENT.register((player, level, hand) -> confirmOnUse(player, hand));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> confirmOnUse(player, hand));
    }

    // ---------------------------------------------------------------------------------------------
    // Hotbar and key packets, from ServerGamePacketListenerImplMixin
    // ---------------------------------------------------------------------------------------------

    /**
     * A hotbar slot change. One step either way is a scroll and moves the outline; a jump to another
     * slot comes from a number key, which ends the session and changes the slot as usual.
     *
     * @return true to drop the packet
     */
    public static boolean onHotbarSlot(@NotNull ServerPlayer player, int newSlot)
    {
        FabricScrollResize resize = active;
        return resize != null && onServerThread(player) && resize.hotbarSlot(player, newSlot);
    }

    /**
     * Dropping the held item (Q) or swapping hands (F) cancels the session instead.
     *
     * @return true to drop the packet
     */
    public static boolean onPlayerAction(
            @NotNull ServerPlayer player,
            @NotNull ServerboundPlayerActionPacket.Action action)
    {
        FabricScrollResize resize = active;
        if (resize == null || !onServerThread(player)
                || (action != ServerboundPlayerActionPacket.Action.DROP_ITEM
                && action != ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                && action != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND))
        {
            return false;
        }
        Session session = resize.sessions.get(player.getUUID());
        if (session == null)
        {
            return false;
        }
        resize.cancel(player, session, MessageKey.SCROLL_RESIZE_CANCELLED);
        // The client already took the dropped item out of its hand; put it back.
        player.inventoryMenu.sendAllDataToRemote();
        return true;
    }

    /** Packets are first seen on the network thread; vanilla hands them to the server thread after. */
    private static boolean onServerThread(@NotNull ServerPlayer player)
    {
        MinecraftServer server = player.level().getServer();
        return server != null && server.isSameThread();
    }

    private boolean hotbarSlot(@NotNull ServerPlayer player, int newSlot)
    {
        Session session = this.sessions.get(player.getUUID());
        if (session == null)
        {
            return false;
        }

        int heldSlot = ((InventoryAccessor) player.getInventory()).griefPrevention$getSelected();
        if (newSlot == heldSlot)
        {
            // The client taking the slot sent back after a step, not a scroll. Paper never sees this
            // packet, as it fires no PlayerItemHeldEvent for an unchanged slot; read as a scroll, it
            // undid every step, or moved a second side of a 3D claim.
            // Keep clientSlot: while a wheel burst is in flight the client can already have sent
            // another slot relative to the previous client request, before accepting this reset.
            return true;
        }
        long now = System.currentTimeMillis();
        // Once a burst of scrolling ends, the slot sent back has reached the client.
        if (now - session.lastScrollMillis > SCROLL_BURST_MILLIS)
        {
            session.clientSlot = heldSlot;
        }

        int step = ScrollResizeGeometry.scrollStep(heldSlot, newSlot, session.clientSlot);
        if (step == 0)
        {
            cancel(player, session, MessageKey.SCROLL_RESIZE_CANCELLED);
            return false;
        }

        player.connection.send(new ClientboundSetHeldSlotPacket(heldSlot));
        session.clientSlot = newSlot;
        session.lastScrollMillis = now;
        session.lastActivityMillis = now;
        applyStep(player, session, step);
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Starting, confirming and cancelling
    // ---------------------------------------------------------------------------------------------

    /** {@code /scrollresize} and {@code /claim scrollresize}. */
    boolean start(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.SCROLL_RESIZE))
        {
            return true;
        }
        if (args.length > 0)
        {
            return false;
        }
        if (!this.settings.isModificationTool(player.getMainHandItem()))
        {
            sender.sendError(MessageKey.SCROLL_RESIZE_NEED_TOOL);
            return true;
        }

        ServerLevel level = (ServerLevel) player.level();
        this.sessions.remove(player.getUUID());

        // A claim selected by right-clicking one of its corners limits the session to the sides there.
        FabricClaimToolHooks.SelectedCorner selection = this.tools.selectedCorner(player);
        ClaimSnapshot selected = selection == null ? null : this.claims.claimById(selection.claimId());
        ClaimSnapshot standingIn = this.claims.findClaimAt(level, player.blockPosition());
        // The subdivision the player looks at wins over a leftover selection of the claim around it
        // and over where they stand, so claims too small to stand in are easy to target.
        ClaimSnapshot lookingAt = claimAtTarget(player, level);
        ClaimSnapshot claim;
        BlockPos corner = null;
        if (lookingAt != null && depth(lookingAt) > depth(selected) && depth(lookingAt) > depth(standingIn))
        {
            claim = lookingAt;
        }
        else if (selected != null)
        {
            claim = selected;
            corner = selection.corner();
        }
        else
        {
            claim = standingIn;
        }

        if (claim == null || claim.id() == null)
        {
            sender.sendError(MessageKey.SCROLL_RESIZE_NO_CLAIM);
            return true;
        }
        if (!this.claims.allows(claim, player, ClaimTrustLevel.EDIT))
        {
            sender.sendError(MessageKey.NOT_YOUR_CLAIM);
            return true;
        }
        if (claim.bounds().isShaped())
        {
            sender.sendText(TextMode.ERROR, "Shaped claims can't be scroll-resized on Fabric yet.");
            return true;
        }

        this.tools.clearSelection(player);
        Session session = new Session(claim, level, corner, remainingClaimBlocks(player, claim));
        session.clientSlot = ((InventoryAccessor) player.getInventory()).griefPrevention$getSelected();
        this.sessions.put(player.getUUID(), session);
        sender.send(TextMode.INSTRUCTION, session.cornerSelection
                ? MessageKey.SCROLL_RESIZE_START_CORNER
                : MessageKey.SCROLL_RESIZE_START);
        return true;
    }

    private @NotNull InteractionResult cancelOnAttack(@NotNull Player player)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.PASS;
        }
        Session session = this.sessions.get(serverPlayer.getUUID());
        if (session == null)
        {
            return InteractionResult.PASS;
        }
        cancel(serverPlayer, session, MessageKey.SCROLL_RESIZE_CANCELLED);
        return InteractionResult.FAIL;
    }

    private @NotNull InteractionResult confirmOnUse(@NotNull Player player, @NotNull InteractionHand hand)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.PASS;
        }
        Session session = this.sessions.get(serverPlayer.getUUID());
        if (session == null)
        {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND)
        {
            confirm(serverPlayer, session);
        }
        return InteractionResult.SUCCESS;
    }

    private void confirm(@NotNull ServerPlayer player, @NotNull Session session)
    {
        long now = System.currentTimeMillis();
        if (now - session.lastUseMillis < USE_REPEAT_MILLIS)
        {
            return;
        }
        session.lastUseMillis = now;
        session.lastActivityMillis = now;

        UUID playerId = player.getUUID();
        ClaimSnapshot claim = this.claims.claimById(session.claimId);
        if (claim == null)
        {
            this.sessions.remove(playerId);
            this.feedback.sendError(player, MessageKey.SCROLL_RESIZE_CLAIM_GONE);
            this.visualization.clear(player);
            return;
        }
        if (!session.hasChanges())
        {
            this.sessions.remove(playerId);
            this.feedback.send(player, TextMode.INFO, MessageKey.SCROLL_RESIZE_UNCHANGED);
            restoreOutline(player, session);
            return;
        }

        // A known conflict keeps the session open, so the player can scroll back instead of starting over.
        ClaimBounds preview = session.preview();
        session.conflict = conflict(session, preview);
        if (session.conflict != null)
        {
            this.feedback.sendText(player, TextMode.ERROR, session.conflict);
            return;
        }

        this.sessions.remove(playerId);
        if (!this.claims.allows(claim, player, ClaimTrustLevel.EDIT))
        {
            this.feedback.sendError(player, MessageKey.NOT_YOUR_CLAIM);
            restoreOutline(player, session);
            return;
        }
        // Rectangles and 2D/3D subdivisions go through the same checks as the golden shovel.
        this.resizer.resize(player, session.level, session.claimId, preview, eyeBlock(player));
    }

    private void cancel(@NotNull ServerPlayer player, @NotNull Session session, @Nullable MessageKey message)
    {
        if (!this.sessions.remove(player.getUUID(), session))
        {
            return;
        }
        restoreOutline(player, session);
        if (message != null)
        {
            this.feedback.send(player, TextMode.INFO, message);
        }
    }

    private void restoreOutline(@NotNull ServerPlayer player, @NotNull Session session)
    {
        ClaimSnapshot claim = this.claims.claimById(session.claimId);
        if (claim != null && player.level() == session.level)
        {
            this.visualization.visualizeClaim(player, session.level, claim, eyeBlock(player));
        }
        else
        {
            this.visualization.clear(player);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Moving a side and drawing the preview
    // ---------------------------------------------------------------------------------------------

    private void applyStep(@NotNull ServerPlayer player, @NotNull Session session, int scroll)
    {
        // A steep vertical aim selects the top/bottom boundary the player intends; otherwise the
        // side the line of sight hits wins, with a fallback when it misses or hits a disallowed side.
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Face face = ScrollResizeGeometry.targetFace(
                eye.x, eye.y, eye.z, look.x, look.y, look.z,
                session.bounds, session.threeDimensional, session.allowedFaces);
        int[] next = null;
        if (face != null)
        {
            double facing = ScrollResizeGeometry.facing(face, look.x, look.y, look.z);
            next = ScrollResizeGeometry.step(session.bounds, face, ScrollResizeGeometry.directedStep(scroll, facing));
        }
        if (next == null)
        {
            this.feedback.sendActionBar(player, TextMode.ERROR, this.feedback.format(MessageKey.SCROLL_RESIZE_LIMIT));
            return;
        }
        session.bounds = next;
        session.redraw = true;
    }

    private void tick(@NotNull MinecraftServer server)
    {
        this.tick++;
        if (this.sessions.isEmpty())
        {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(this.sessions.entrySet()))
        {
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null)
            {
                this.sessions.remove(entry.getKey());
                continue;
            }
            if (!player.isAlive() || player.level() != session.level
                    || !this.settings.isModificationTool(player.getMainHandItem()))
            {
                cancel(player, session, MessageKey.SCROLL_RESIZE_CANCELLED);
                continue;
            }
            if (now - session.lastActivityMillis >= IDLE_TIMEOUT_MILLIS)
            {
                cancel(player, session, MessageKey.SCROLL_RESIZE_TIMED_OUT);
                continue;
            }
            // Several scroll steps can arrive in one tick: draw once, and follow a player who walks around.
            if (session.redraw || (this.tick - session.drawnAtTick >= REDRAW_TICKS
                    && !player.blockPosition().equals(session.drawnFrom)))
            {
                render(player, session);
            }
        }
    }

    private void render(@NotNull ServerPlayer player, @NotNull Session session)
    {
        session.redraw = false;
        session.drawnAtTick = this.tick;
        session.drawnFrom = player.blockPosition();

        ClaimSnapshot claim = this.claims.claimById(session.claimId);
        if (claim == null)
        {
            this.sessions.remove(player.getUUID());
            this.feedback.sendError(player, MessageKey.SCROLL_RESIZE_CLAIM_GONE);
            this.visualization.clear(player);
            return;
        }

        ClaimBounds preview = session.preview();
        session.conflict = conflict(session, preview);
        this.visualization.visualizeResizePreview(
                player, session.level, claim, preview, session.conflict != null, around(claim, preview));
        sendSize(player, session, preview);
    }

    private void sendSize(@NotNull ServerPlayer player, @NotNull Session session, @NotNull ClaimBounds preview)
    {
        String width = String.valueOf(preview.xLength());
        String length = String.valueOf(preview.zLength());
        String size = session.threeDimensional
                ? this.feedback.format(MessageKey.SCROLL_RESIZE_SIZE_3D, width, length, String.valueOf(preview.yHeight()))
                : this.feedback.format(MessageKey.SCROLL_RESIZE_SIZE, width, length);
        if (session.conflict != null)
        {
            this.feedback.sendActionBar(player, TextMode.ERROR, size + " - " + session.conflict);
            return;
        }
        Integer remaining = session.remainingAfter(preview);
        if (remaining != null)
        {
            size = this.feedback.format(MessageKey.SCROLL_RESIZE_BLOCKS_LEFT, size, String.valueOf(remaining));
        }
        this.feedback.sendActionBar(player, TextMode.INSTRUCTION, size);
    }

    /** @return why the outline cannot be applied, or null when it can */
    private @Nullable String conflict(@NotNull Session session, @NotNull ClaimBounds preview)
    {
        FabricClaimRepository.UpdateClaimResult refused = this.claims.checkPlacement(session.claimId, preview);
        if (refused != null)
        {
            return this.feedback.format(refused.isMissing()
                    ? MessageKey.SCROLL_RESIZE_CLAIM_GONE
                    : MessageKey.SCROLL_RESIZE_CONFLICT);
        }
        Integer remaining = session.remainingAfter(preview);
        if (remaining != null && remaining < 0)
        {
            return this.feedback.format(MessageKey.SCROLL_RESIZE_NOT_ENOUGH_BLOCKS, String.valueOf(-remaining));
        }
        return null;
    }

    /** The claims the preview is drawn with: parent and siblings, subdivisions, and claims nearby. */
    private @NotNull List<ClaimSnapshot> around(@NotNull ClaimSnapshot claim, @NotNull ClaimBounds preview)
    {
        Map<Long, ClaimSnapshot> shown = new LinkedHashMap<>();
        Long parentId = claim.parentId();
        if (parentId != null)
        {
            ClaimSnapshot parent = this.claims.claimById(parentId);
            if (parent != null)
            {
                shown.put(parentId, parent);
            }
            for (ClaimSnapshot sibling : this.claims.subdivisionsOf(parentId))
            {
                shown.put(sibling.id(), sibling);
            }
        }
        for (ClaimSnapshot child : this.claims.subdivisionsOf(Objects.requireNonNull(claim.id())))
        {
            shown.put(child.id(), child);
        }

        long topLevelId = topLevelId(claim);
        ClaimBounds area = ClaimBounds.rectangle(
                preview.minX() - NEARBY_MARGIN, preview.minY(), preview.minZ() - NEARBY_MARGIN,
                preview.maxX() + NEARBY_MARGIN, preview.maxY(), preview.maxZ() + NEARBY_MARGIN);
        for (ClaimSnapshot nearby : this.claims.candidates(claim.worldKey(), area))
        {
            if (nearby.parentId() == null && nearby.id() != null && nearby.id() != topLevelId)
            {
                shown.put(nearby.id(), nearby);
            }
        }
        shown.remove(claim.id());
        return new ArrayList<>(shown.values());
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private @Nullable ClaimSnapshot claimAtTarget(@NotNull ServerPlayer player, @NotNull ServerLevel level)
    {
        HitResult hit = player.pick(TARGET_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK)
        {
            return null;
        }
        return this.claims.findClaimAt(level, blockHit.getBlockPos());
    }

    /** The claim blocks the owner of a top-level claim has left, or null when the claim costs the player nothing. */
    private @Nullable Integer remainingClaimBlocks(@NotNull ServerPlayer player, @NotNull ClaimSnapshot claim)
    {
        UUID ownerId = claim.ownerId();
        if (claim.parentId() != null || ownerId == null || !ownerId.equals(player.getUUID()))
        {
            return null;
        }
        try
        {
            return this.claims.claimBlockBalance(ownerId).remaining();
        }
        catch (IOException exception)
        {
            this.logger.warn("Could not read the claim-block balance of {}.", ownerId, exception);
            return null;
        }
    }

    private int depth(@Nullable ClaimSnapshot claim)
    {
        int depth = -1;
        for (ClaimSnapshot cursor = claim; cursor != null && depth < 16; )
        {
            depth++;
            Long parentId = cursor.parentId();
            cursor = parentId == null ? null : this.claims.claimById(parentId);
        }
        return depth;
    }

    private long topLevelId(@NotNull ClaimSnapshot claim)
    {
        ClaimSnapshot topLevel = claim;
        for (int depth = 0; topLevel.parentId() != null && depth < 16; depth++)
        {
            ClaimSnapshot parent = this.claims.claimById(topLevel.parentId());
            if (parent == null)
            {
                break;
            }
            topLevel = parent;
        }
        return Objects.requireNonNull(topLevel.id());
    }

    private static @NotNull BlockPos eyeBlock(@NotNull ServerPlayer player)
    {
        return BlockPos.containing(player.getEyePosition());
    }

    private static final class Session
    {
        private final long claimId;
        private final @NotNull ServerLevel level;
        private final boolean threeDimensional;
        private final int @NotNull [] originalBounds;
        private int @NotNull [] bounds;
        private final @NotNull EnumSet<Face> allowedFaces;
        private final boolean cornerSelection;
        private final int originalArea;
        /** The owner's claim blocks left before the resize, for a top-level claim of their own. */
        private final @Nullable Integer remainingBefore;
        private int clientSlot;
        private long lastScrollMillis;
        private long lastActivityMillis = System.currentTimeMillis();
        private long lastUseMillis;
        private boolean redraw = true;
        private long drawnAtTick;
        private @Nullable BlockPos drawnFrom;
        private @Nullable String conflict;

        private Session(
                @NotNull ClaimSnapshot claim,
                @NotNull ServerLevel level,
                @Nullable BlockPos corner,
                @Nullable Integer remainingBefore)
        {
            this.claimId = Objects.requireNonNull(claim.id());
            this.level = level;
            this.threeDimensional = claim.threeDimensional();
            ClaimBounds bounds = claim.bounds();
            this.originalBounds = new int[] {
                    bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
            };
            this.bounds = this.originalBounds.clone();
            EnumSet<Face> all = ScrollResizeGeometry.allFaces(this.threeDimensional);
            this.allowedFaces = corner == null
                    ? all
                    : ScrollResizeGeometry.cornerFaces(
                            this.bounds, corner.getX(), corner.getY(), corner.getZ(), this.threeDimensional);
            this.cornerSelection = this.allowedFaces.size() < all.size();
            this.originalArea = bounds.area();
            this.remainingBefore = remainingBefore;
        }

        private @NotNull ClaimBounds preview()
        {
            return ClaimBounds.rectangle(
                    this.bounds[ScrollResizeGeometry.MIN_X],
                    this.bounds[ScrollResizeGeometry.MIN_Y],
                    this.bounds[ScrollResizeGeometry.MIN_Z],
                    this.bounds[ScrollResizeGeometry.MAX_X],
                    this.bounds[ScrollResizeGeometry.MAX_Y],
                    this.bounds[ScrollResizeGeometry.MAX_Z]);
        }

        private @Nullable Integer remainingAfter(@NotNull ClaimBounds preview)
        {
            return this.remainingBefore == null ? null : this.remainingBefore + this.originalArea - preview.area();
        }

        private boolean hasChanges()
        {
            return !Arrays.equals(this.bounds, this.originalBounds);
        }
    }
}
