package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class FabricFakeBlockVisualization
{
    private static final int STEP = 10;
    private static final int DISPLAY_ZONE_RADIUS = 75;
    private static final long VISUALIZATION_TICKS = 20L * 60L;

    private final Map<UUID, ActiveVisualization> activeVisualizations = new HashMap<>();
    private long tick;

    void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(this::expireVisualizations);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer)
            {
                handleBlockBreak((ServerPlayer) player, pos);
            }
        });
        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer)
            {
                resendBrokenVisual((ServerPlayer) player, pos);
            }
        });
    }

    void visualizeClaim(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot selectedClaim,
            @NotNull Collection<ClaimSnapshot> loadedClaims,
            @NotNull BlockPos clicked)
    {
        visualizeTargets(player, level, defineTargets(selectedClaim, loadedClaims), clicked);
    }

    void visualizeInitializeBounds(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull BlockPos clicked)
    {
        visualizeTargets(player, level, List.of(new VisualizationTarget(bounds, VisualizationStyle.INITIALIZE)), clicked);
    }

    void visualizeConflictBounds(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull BlockPos clicked)
    {
        visualizeTargets(player, level, List.of(new VisualizationTarget(bounds, VisualizationStyle.CONFLICT)), clicked);
    }

    void visualizeClaimBounds(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot claim,
            @NotNull ClaimBounds bounds,
            @NotNull BlockPos clicked)
    {
        visualizeTargets(player, level, List.of(new VisualizationTarget(bounds, styleFor(claim))), clicked);
    }

    void clear(@NotNull ServerPlayer player)
    {
        ActiveVisualization active = this.activeVisualizations.remove(player.getUUID());
        if (active == null)
        {
            return;
        }

        restore(player, active);
    }

    private void handleBlockBreak(@NotNull ServerPlayer player, @NotNull BlockPos pos)
    {
        ActiveVisualization active = this.activeVisualizations.get(player.getUUID());
        if (active == null || player.level() != active.level)
        {
            return;
        }

        BlockPos immutablePos = pos.immutable();
        if (!active.fakeBlocks.containsKey(immutablePos))
        {
            return;
        }

        active.fakeBlocks.remove(immutablePos);
        sendBlock(player, immutablePos, active.level.getBlockState(immutablePos));
        if (active.fakeBlocks.isEmpty())
        {
            this.activeVisualizations.remove(player.getUUID());
        }
    }

    private void resendBrokenVisual(@NotNull ServerPlayer player, @NotNull BlockPos pos)
    {
        ActiveVisualization active = this.activeVisualizations.get(player.getUUID());
        if (active == null || player.level() != active.level)
        {
            return;
        }

        BlockState fakeState = active.fakeBlocks.get(pos.immutable());
        if (fakeState != null)
        {
            sendBlock(player, pos, fakeState);
        }
    }

    private void expireVisualizations(@NotNull MinecraftServer server)
    {
        this.tick++;
        this.activeVisualizations.entrySet().removeIf(entry -> {
            ActiveVisualization active = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (active.expiresAt <= this.tick)
            {
                if (player != null)
                {
                    restore(player, active);
                }
                return true;
            }

            if (active.resendAt > 0L && active.resendAt <= this.tick)
            {
                if (player != null)
                {
                    resend(player, active);
                }
                active.resendAt = -1L;
            }
            return false;
        });
    }

    private static void restore(@NotNull ServerPlayer player, @NotNull ActiveVisualization active)
    {
        if (player.level() != active.level)
        {
            return;
        }

        for (BlockPos pos : active.fakeBlocks.keySet())
        {
            if (active.level.isLoaded(pos))
            {
                sendBlock(player, pos, active.level.getBlockState(pos));
            }
        }
    }

    private static void resend(@NotNull ServerPlayer player, @NotNull ActiveVisualization active)
    {
        if (player.level() != active.level)
        {
            return;
        }

        for (Map.Entry<BlockPos, BlockState> entry : active.fakeBlocks.entrySet())
        {
            sendBlock(player, entry.getKey(), entry.getValue());
        }
    }

    private static void collectFakeBlocks(
            @NotNull ServerLevel level,
            @NotNull BlockPos visualizeFrom,
            int height,
            boolean waterTransparent,
            @NotNull VisualizationTarget target,
            @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks)
    {
        ClaimBounds bounds = target.bounds;
        DisplayZone displayZone = target.style.exactPlacement
                ? DisplayZone.for3D(visualizeFrom, bounds, level)
                : DisplayZone.for2D(visualizeFrom, level);

        if (target.style.exactPlacement)
        {
            draw3D(level, bounds, displayZone, target.style, fakeBlocks);
            return;
        }

        draw2D(level, bounds, displayZone, height, waterTransparent, target.style, fakeBlocks);
    }

    private static void draw2D(
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull DisplayZone displayZone,
            int height,
            boolean waterTransparent,
            @NotNull VisualizationStyle style,
            @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks)
    {
        addHorizontal2D(level, displayZone, bounds.minX(), bounds.maxX(), height, bounds.maxZ(), true,
                waterTransparent, style.sideState, fakeBlocks);
        addHorizontal2D(level, displayZone, bounds.minX(), bounds.maxX(), height, bounds.minZ(), true,
                waterTransparent, style.sideState, fakeBlocks);
        addHorizontal2D(level, displayZone, bounds.minZ(), bounds.maxZ(), height, bounds.minX(), false,
                waterTransparent, style.sideState, fakeBlocks);
        addHorizontal2D(level, displayZone, bounds.minZ(), bounds.maxZ(), height, bounds.maxX(), false,
                waterTransparent, style.sideState, fakeBlocks);

        if (bounds.xLength() > 2)
        {
            add2D(level, displayZone, new BlockPos(bounds.minX() + 1, height, bounds.maxZ()),
                    waterTransparent, style.sideState, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.minX() + 1, height, bounds.minZ()),
                    waterTransparent, style.sideState, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX() - 1, height, bounds.maxZ()),
                    waterTransparent, style.sideState, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX() - 1, height, bounds.minZ()),
                    waterTransparent, style.sideState, fakeBlocks);
        }

        if (bounds.zLength() > 2)
        {
            add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.minZ() + 1),
                    waterTransparent, style.sideState, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.minZ() + 1),
                    waterTransparent, style.sideState, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.maxZ() - 1),
                    waterTransparent, style.sideState, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.maxZ() - 1),
                    waterTransparent, style.sideState, fakeBlocks);
        }

        add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.maxZ()),
                waterTransparent, style.cornerState, fakeBlocks);
        add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.maxZ()),
                waterTransparent, style.cornerState, fakeBlocks);
        add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.minZ()),
                waterTransparent, style.cornerState, fakeBlocks);
        add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.minZ()),
                waterTransparent, style.cornerState, fakeBlocks);
    }

    private static void addHorizontal2D(
            @NotNull ServerLevel level,
            @NotNull DisplayZone displayZone,
            int start,
            int end,
            int height,
            int fixed,
            boolean xAxis,
            boolean waterTransparent,
            @NotNull BlockState state,
            @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks)
    {
        int min = Math.max(start + STEP, xAxis ? displayZone.minX : displayZone.minZ);
        int max = Math.min(end - STEP / 2, xAxis ? displayZone.maxX : displayZone.maxZ);
        for (int value = min; value < max; value += STEP)
        {
            BlockPos pos = xAxis ? new BlockPos(value, height, fixed) : new BlockPos(fixed, height, value);
            add2D(level, displayZone, pos, waterTransparent, state, fakeBlocks);
        }
    }

    private static void draw3D(
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull DisplayZone displayZone,
            @NotNull VisualizationStyle style,
            @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks)
    {
        int bottomY = clamp(bounds.minY(), level.getMinY(), level.getMaxY());
        int topY = clamp(bounds.maxY(), level.getMinY(), level.getMaxY());
        draw3DLevel(level, bounds, displayZone, style, fakeBlocks, bottomY, true);
        if (topY != bottomY)
        {
            draw3DLevel(level, bounds, displayZone, style, fakeBlocks, topY, false);
        }
    }

    private static void draw3DLevel(
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull DisplayZone displayZone,
            @NotNull VisualizationStyle style,
            @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks,
            int y,
            boolean bottom)
    {
        add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.maxZ()), style.cornerState, fakeBlocks);
        add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.maxZ()), style.cornerState, fakeBlocks);
        add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.minZ()), style.cornerState, fakeBlocks);
        add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.minZ()), style.cornerState, fakeBlocks);

        if (bounds.xLength() > 2)
        {
            add3D(level, displayZone, new BlockPos(bounds.minX() + 1, y, bounds.maxZ()), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.minX() + 1, y, bounds.minZ()), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX() - 1, y, bounds.maxZ()), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX() - 1, y, bounds.minZ()), style.sideState, fakeBlocks);
        }

        if (bounds.zLength() > 2)
        {
            add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.minZ() + 1), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.minZ() + 1), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.maxZ() - 1), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.maxZ() - 1), style.sideState, fakeBlocks);
        }

        int verticalY = bottom ? y + 1 : y - 1;
        if (verticalY >= level.getMinY() && verticalY <= level.getMaxY())
        {
            add3D(level, displayZone, new BlockPos(bounds.minX(), verticalY, bounds.maxZ()), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), verticalY, bounds.maxZ()), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.minX(), verticalY, bounds.minZ()), style.sideState, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), verticalY, bounds.minZ()), style.sideState, fakeBlocks);
        }
    }

    private static void add2D(
            @NotNull ServerLevel level,
            @NotNull DisplayZone displayZone,
            @NotNull BlockPos requested,
            boolean waterTransparent,
            @NotNull BlockState state,
            @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks)
    {
        if (!displayZone.contains2D(requested))
        {
            return;
        }

        BlockPos visiblePos = getVisibleLocation(level, requested, waterTransparent);
        if (displayZone.contains(visiblePos) && level.isLoaded(visiblePos))
        {
            fakeBlocks.put(visiblePos.immutable(), state);
        }
    }

    private static void add3D(
            @NotNull ServerLevel level,
            @NotNull DisplayZone displayZone,
            @NotNull BlockPos pos,
            @NotNull BlockState state,
            @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks)
    {
        if (displayZone.contains(pos) && level.isLoaded(pos))
        {
            fakeBlocks.put(pos.immutable(), state);
        }
    }

    private static @NotNull BlockPos getVisibleLocation(
            @NotNull ServerLevel level,
            @NotNull BlockPos requested,
            boolean waterTransparent)
    {
        BlockPos block = requested;
        Direction direction = isTransparent(level, block, waterTransparent) ? Direction.DOWN : Direction.UP;

        while (block.getY() >= level.getMinY()
                && block.getY() < level.getMaxY() - 1
                && (!isTransparent(level, block.above(), waterTransparent)
                || isTransparent(level, block, waterTransparent)))
        {
            block = block.relative(direction);
        }

        return block;
    }

    private static boolean isTransparent(
            @NotNull ServerLevel level,
            @NotNull BlockPos pos,
            boolean waterTransparent)
    {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.DIRT_PATH))
        {
            return false;
        }
        if (state.liquid())
        {
            return waterTransparent;
        }
        if (state.isAir())
        {
            return true;
        }

        return state.getCollisionShape(level, pos).isEmpty() || !state.isCollisionShapeFullBlock(level, pos);
    }

    private static @NotNull List<VisualizationTarget> defineTargets(
            @NotNull ClaimSnapshot selectedClaim,
            @NotNull Collection<ClaimSnapshot> loadedClaims)
    {
        ClaimSnapshot root = findVisualizationRoot(selectedClaim, loadedClaims);
        List<VisualizationTarget> targets = new ArrayList<>();
        addTargetWithDescendants(root, loadedClaims, styleFor(root), targets, new HashSet<>());
        return targets;
    }

    private static @NotNull ClaimSnapshot findVisualizationRoot(
            @NotNull ClaimSnapshot selectedClaim,
            @NotNull Collection<ClaimSnapshot> loadedClaims)
    {
        ClaimSnapshot root = selectedClaim;
        while (!root.threeDimensional() && root.parentId() != null)
        {
            ClaimSnapshot parent = findById(loadedClaims, root.parentId());
            if (parent == null || parent.threeDimensional())
            {
                break;
            }
            root = parent;
        }
        return root;
    }

    private static void addTargetWithDescendants(
            @NotNull ClaimSnapshot claim,
            @NotNull Collection<ClaimSnapshot> loadedClaims,
            @NotNull VisualizationStyle style,
            @NotNull List<VisualizationTarget> targets,
            @NotNull Set<Long> seenIds)
    {
        Long id = claim.id();
        if (id != null && !seenIds.add(id))
        {
            return;
        }

        targets.add(new VisualizationTarget(claim, style));
        if (id == null)
        {
            return;
        }

        for (ClaimSnapshot child : loadedClaims)
        {
            if (Objects.equals(id, child.parentId()))
            {
                addTargetWithDescendants(child, loadedClaims, styleFor(child), targets, seenIds);
            }
        }
    }

    private static @Nullable ClaimSnapshot findById(
            @NotNull Collection<ClaimSnapshot> claims,
            @NotNull Long id)
    {
        for (ClaimSnapshot claim : claims)
        {
            if (id.equals(claim.id()))
            {
                return claim;
            }
        }
        return null;
    }

    private static @NotNull VisualizationStyle styleFor(@NotNull ClaimSnapshot claim)
    {
        if (claim.threeDimensional())
        {
            return claim.adminClaim() && !claim.subdivision()
                    ? VisualizationStyle.ADMIN_3D
                    : VisualizationStyle.SUBDIVISION_3D;
        }
        if (claim.subdivision())
        {
            return VisualizationStyle.SUBDIVISION;
        }
        return claim.adminClaim() ? VisualizationStyle.ADMIN : VisualizationStyle.CLAIM;
    }

    private void visualizeTargets(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull List<VisualizationTarget> targets,
            @NotNull BlockPos clicked)
    {
        clear(player);

        LinkedHashMap<BlockPos, BlockState> fakeBlocks = new LinkedHashMap<>();
        BlockPos visualizeFrom = player.blockPosition();
        int height = clamp(clicked.getY(), level.getMinY(), level.getMaxY());
        boolean waterTransparent = level.getBlockState(visualizeFrom).liquid();

        for (VisualizationTarget target : targets)
        {
            collectFakeBlocks(level, visualizeFrom, height, waterTransparent, target, fakeBlocks);
        }

        if (fakeBlocks.isEmpty())
        {
            return;
        }

        for (Map.Entry<BlockPos, BlockState> entry : fakeBlocks.entrySet())
        {
            sendBlock(player, entry.getKey(), entry.getValue());
        }

        this.activeVisualizations.put(
                player.getUUID(),
                new ActiveVisualization(
                        level,
                        new LinkedHashMap<>(fakeBlocks),
                        this.tick + VISUALIZATION_TICKS,
                        this.tick + 1L));
    }

    private static void sendBlock(
            @NotNull ServerPlayer player,
            @NotNull BlockPos pos,
            @NotNull BlockState state)
    {
        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private enum VisualizationStyle
    {
        CLAIM(Blocks.GLOWSTONE.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), false),
        ADMIN(Blocks.GLOWSTONE.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState(), false),
        ADMIN_3D(Blocks.GLOWSTONE.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState(), true),
        SUBDIVISION(Blocks.IRON_BLOCK.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState(), false),
        SUBDIVISION_3D(Blocks.IRON_BLOCK.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState(), true),
        INITIALIZE(Blocks.DIAMOND_BLOCK.defaultBlockState(), Blocks.DIAMOND_BLOCK.defaultBlockState(), false),
        CONFLICT(Blocks.REDSTONE_ORE.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), false);

        private final @NotNull BlockState cornerState;
        private final @NotNull BlockState sideState;
        private final boolean exactPlacement;

        VisualizationStyle(
                @NotNull BlockState cornerState,
                @NotNull BlockState sideState,
                boolean exactPlacement)
        {
            this.cornerState = cornerState;
            this.sideState = sideState;
            this.exactPlacement = exactPlacement;
        }
    }

    private static final class VisualizationTarget
    {
        private final @NotNull ClaimBounds bounds;
        private final @NotNull VisualizationStyle style;

        private VisualizationTarget(@NotNull ClaimSnapshot claim, @NotNull VisualizationStyle style)
        {
            this(claim.bounds(), style);
        }

        private VisualizationTarget(@NotNull ClaimBounds bounds, @NotNull VisualizationStyle style)
        {
            this.bounds = bounds;
            this.style = style;
        }
    }

    private static final class ActiveVisualization
    {
        private final @NotNull ServerLevel level;
        private final @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks;
        private final long expiresAt;
        private long resendAt;

        private ActiveVisualization(
                @NotNull ServerLevel level,
                @NotNull LinkedHashMap<BlockPos, BlockState> fakeBlocks,
                long expiresAt,
                long resendAt)
        {
            this.level = level;
            this.fakeBlocks = fakeBlocks;
            this.expiresAt = expiresAt;
            this.resendAt = resendAt;
        }
    }

    private static final class DisplayZone
    {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private DisplayZone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
        {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private static @NotNull DisplayZone for2D(@NotNull BlockPos center, @NotNull ServerLevel level)
        {
            return new DisplayZone(
                    center.getX() - DISPLAY_ZONE_RADIUS,
                    Math.max(level.getMinY(), center.getY() - DISPLAY_ZONE_RADIUS),
                    center.getZ() - DISPLAY_ZONE_RADIUS,
                    center.getX() + DISPLAY_ZONE_RADIUS,
                    Math.min(level.getMaxY(), center.getY() + DISPLAY_ZONE_RADIUS),
                    center.getZ() + DISPLAY_ZONE_RADIUS);
        }

        private static @NotNull DisplayZone for3D(
                @NotNull BlockPos center,
                @NotNull ClaimBounds bounds,
                @NotNull ServerLevel level)
        {
            return new DisplayZone(
                    center.getX() - DISPLAY_ZONE_RADIUS,
                    Math.max(level.getMinY(), bounds.minY() - 1),
                    center.getZ() - DISPLAY_ZONE_RADIUS,
                    center.getX() + DISPLAY_ZONE_RADIUS,
                    Math.min(level.getMaxY(), bounds.maxY() + 1),
                    center.getZ() + DISPLAY_ZONE_RADIUS);
        }

        private boolean contains(@NotNull BlockPos pos)
        {
            return contains2D(pos) && pos.getY() >= this.minY && pos.getY() <= this.maxY;
        }

        private boolean contains2D(@NotNull BlockPos pos)
        {
            return pos.getX() >= this.minX
                    && pos.getX() <= this.maxX
                    && pos.getZ() >= this.minZ
                    && pos.getZ() <= this.maxZ;
        }
    }
}
