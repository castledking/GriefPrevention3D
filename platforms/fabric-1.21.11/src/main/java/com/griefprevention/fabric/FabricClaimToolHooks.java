package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class FabricClaimToolHooks
{
    private static final int MIN_CLAIM_SIDE = 5;

    private final FabricClaimRepository claims;
    private final FabricFakeBlockVisualization visualization;
    private final Map<UUID, ClaimToolSession> sessions = new HashMap<>();

    FabricClaimToolHooks(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricFakeBlockVisualization visualization)
    {
        this.claims = claims;
        this.visualization = visualization;
    }

    void register()
    {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
                handleToolUse(player, level, hand, hitResult));
    }

    private @NotNull InteractionResult handleToolUse(
            @NotNull net.minecraft.world.entity.player.Player player,
            @NotNull Level level,
            @NotNull InteractionHand hand,
            @NotNull BlockHitResult hitResult)
    {
        ItemStack stack = player.getItemInHand(hand);
        ClaimTool tool = claimTool(stack);
        if (tool == null)
        {
            return InteractionResult.PASS;
        }

        if (level.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer))
        {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        BlockPos clicked = hitResult.getBlockPos();

        if (tool == ClaimTool.MODIFICATION)
        {
            return handleModificationTool(serverPlayer, serverLevel, clicked);
        }

        ClaimSnapshot claim = this.claims.findClaimAt(serverLevel, clicked);
        if (claim == null)
        {
            this.visualization.clear(serverPlayer);
            serverPlayer.displayClientMessage(Component.literal("No GriefPrevention3D Fabric claim here."), true);
            return InteractionResult.SUCCESS;
        }

        sendClaimMessage(serverPlayer, claim, tool);
        this.visualization.visualizeClaim(serverPlayer, serverLevel, claim, this.claims.snapshots(), clicked);
        return InteractionResult.SUCCESS;
    }

    private @NotNull InteractionResult handleModificationTool(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull BlockPos clicked)
    {
        ClaimToolSession session = this.sessions.get(player.getUUID());
        if (session != null)
        {
            if (!session.worldKey.equals(this.claims.worldKey(level)))
            {
                this.sessions.remove(player.getUUID());
                this.visualization.clear(player);
                player.displayClientMessage(Component.literal("Claim tool selection cleared after changing worlds."), true);
                return InteractionResult.SUCCESS;
            }

            if (session.mode == SessionMode.CREATE)
            {
                finishCreate(player, level, session, clicked);
            }
            else
            {
                finishResize(player, level, session, clicked);
            }
            return InteractionResult.SUCCESS;
        }

        ClaimSnapshot claim = this.claims.findClaimAt(level, clicked);
        if (claim == null)
        {
            startCreate(player, level, clicked);
            return InteractionResult.SUCCESS;
        }

        CornerSelection corner = cornerSelection(claim, clicked);
        if (corner != null && canModify(player, claim))
        {
            startResize(player, level, claim, corner, clicked);
            return InteractionResult.SUCCESS;
        }

        if (corner != null)
        {
            player.displayClientMessage(Component.literal("You do not own this claim."), true);
        }
        sendClaimMessage(player, claim, ClaimTool.MODIFICATION);
        this.visualization.visualizeClaim(player, level, claim, this.claims.snapshots(), clicked);
        return InteractionResult.SUCCESS;
    }

    private void startCreate(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull BlockPos clicked)
    {
        ClaimToolSession session = ClaimToolSession.create(this.claims.worldKey(level), clicked);
        this.sessions.put(player.getUUID(), session);
        ClaimBounds preview = create2DBounds(level, clicked, clicked);
        this.visualization.visualizeInitializeBounds(player, level, preview, clicked);
        player.displayClientMessage(Component.literal("Claim corner set. Right-click the opposite corner with your shovel."), false);
    }

    private void finishCreate(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimToolSession session,
            @NotNull BlockPos clicked)
    {
        ClaimBounds bounds = create2DBounds(level, session.firstCorner, clicked);
        if (!isLargeEnough(bounds))
        {
            this.visualization.visualizeInitializeBounds(player, level, bounds, clicked);
            player.displayClientMessage(Component.literal("Claim is too small. Claims must be at least "
                    + MIN_CLAIM_SIDE
                    + "x"
                    + MIN_CLAIM_SIDE
                    + "."), true);
            return;
        }

        try
        {
            FabricClaimRepository.CreateClaimResult result = this.claims.createClaim(
                    level,
                    session.firstCorner,
                    clicked,
                    player.getUUID());
            if (!result.created())
            {
                this.visualization.visualizeConflictBounds(player, level, bounds, clicked);
                ClaimSnapshot overlap = result.overlappingClaim();
                player.displayClientMessage(Component.literal("Cannot create claim; overlaps claim #"
                        + (overlap == null ? "unknown" : overlap.id())
                        + "."), true);
                return;
            }

            this.sessions.remove(player.getUUID());
            ClaimSnapshot created = result.createdClaim();
            if (created != null)
            {
                this.visualization.visualizeClaim(player, level, created, this.claims.snapshots(), clicked);
                player.displayClientMessage(Component.literal("Created claim #" + created.id() + "."), false);
            }
        }
        catch (IOException e)
        {
            player.displayClientMessage(Component.literal("Could not save claim: " + e.getMessage()), true);
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
            player.displayClientMessage(Component.literal("Cannot resize a claim without an id."), true);
            return;
        }

        ClaimToolSession session = ClaimToolSession.resize(this.claims.worldKey(level), id, claim.bounds(), corner);
        this.sessions.put(player.getUUID(), session);
        this.visualization.visualizeClaim(player, level, claim, this.claims.snapshots(), clicked);
        player.displayClientMessage(Component.literal("Resizing claim. Use your shovel again at the new corner location."), false);
    }

    private void finishResize(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimToolSession session,
            @NotNull BlockPos clicked)
    {
        ClaimBounds bounds = resizedBounds(level, session, clicked);
        if (!isLargeEnough(bounds))
        {
            this.visualization.visualizeInitializeBounds(player, level, bounds, clicked);
            player.displayClientMessage(Component.literal("Claim is too small. Claims must be at least "
                    + MIN_CLAIM_SIDE
                    + "x"
                    + MIN_CLAIM_SIDE
                    + "."), true);
            return;
        }

        try
        {
            FabricClaimRepository.UpdateClaimResult result = this.claims.updateClaimBounds(session.claimId, bounds);
            if (result.isMissing())
            {
                this.sessions.remove(player.getUUID());
                this.visualization.clear(player);
                player.displayClientMessage(Component.literal("That claim no longer exists."), true);
                return;
            }
            if (!result.updated())
            {
                this.visualization.visualizeConflictBounds(player, level, bounds, clicked);
                ClaimSnapshot overlap = result.overlappingClaim();
                player.displayClientMessage(Component.literal("Cannot resize claim; overlaps claim #"
                        + (overlap == null ? "unknown" : overlap.id())
                        + "."), true);
                return;
            }

            this.sessions.remove(player.getUUID());
            ClaimSnapshot updated = result.updatedClaim();
            if (updated != null)
            {
                this.visualization.visualizeClaim(player, level, updated, this.claims.snapshots(), clicked);
                player.displayClientMessage(Component.literal("Resized claim #" + updated.id() + "."), false);
            }
        }
        catch (IOException e)
        {
            player.displayClientMessage(Component.literal("Could not save resized claim: " + e.getMessage()), true);
        }
    }

    private static ClaimTool claimTool(@NotNull ItemStack stack)
    {
        if (stack.is(Items.STICK))
        {
            return ClaimTool.INVESTIGATION;
        }
        if (stack.is(Items.GOLDEN_SHOVEL))
        {
            return ClaimTool.MODIFICATION;
        }
        return null;
    }

    private static void sendClaimMessage(
            @NotNull ServerPlayer player,
            @NotNull ClaimSnapshot claim,
            @NotNull ClaimTool tool)
    {
        ClaimBounds bounds = claim.bounds();
        String owner = claim.ownerId() == null ? "admin" : claim.ownerId().toString();
        String prefix = tool == ClaimTool.MODIFICATION ? "Selected claim #" : "Claim #";
        player.displayClientMessage(Component.literal(prefix
                + claim.id()
                + " owner="
                + owner
                + " blocks="
                + bounds.area()
                + " y="
                + bounds.minY()
                + ".."
                + bounds.maxY()), false);
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
        int y1 = original.minY();
        int y2 = original.maxY();
        if (corner.hasYSelection())
        {
            y1 = corner.minY ? clicked.getY() : original.minY();
            y2 = corner.minY ? original.maxY() : clicked.getY();
        }
        else
        {
            y1 = level.getMinY();
            y2 = level.getMaxY();
        }

        return ClaimBounds.rectangle(x1, y1, z1, x2, y2, z2);
    }

    private static boolean isLargeEnough(@NotNull ClaimBounds bounds)
    {
        return bounds.xLength() >= MIN_CLAIM_SIDE && bounds.zLength() >= MIN_CLAIM_SIDE;
    }

    private static boolean canModify(@NotNull ServerPlayer player, @NotNull ClaimSnapshot claim)
    {
        return claim.ownerId() != null && claim.ownerId().equals(player.getUUID());
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
