package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Shows claim boundaries to a single player with client-side fake blocks and, when
 * {@code VisualizationGlow} is on, glowing outlines around them.
 *
 * <p>Each player has at most one visualization. It remembers what it showed (one claim tree, the
 * claims near a point, or raw bounds) so that when a claim it includes is resized, created nearby or
 * abandoned, it is redrawn from the current claims instead of leaving stale markers behind.
 */
final class FabricFakeBlockVisualization
{
    /** Horizontal reach of the sneak-and-stick overview, matching Paper's nearby-claims search. */
    static final int NEARBY_RADIUS = 150;
    /** 3D claims are only shown in the overview when they come this close to the player's height. */
    private static final int NEARBY_VERTICAL_RANGE = 16;
    private static final int STEP = 10;
    private static final int DISPLAY_ZONE_RADIUS = 75;
    private static final long VISUALIZATION_TICKS = 20L * 60L;

    private final FabricClaimRepository claims;
    private final FabricSettings settings;
    private final Map<UUID, ActiveVisualization> activeVisualizations = new HashMap<>();
    private @Nullable MinecraftServer server;
    private long tick;

    FabricFakeBlockVisualization(@NotNull FabricClaimRepository claims, @NotNull FabricSettings settings)
    {
        this.claims = claims;
        this.settings = settings;
    }

    void register()
    {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> this.server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            this.server = null;
            this.activeVisualizations.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(this::expireVisualizations);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, disconnected) ->
                this.activeVisualizations.remove(handler.getPlayer().getUUID()));
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

        ClaimCreatedCallback.EVENT.register((claim, player) -> refreshWhere(active -> overviewCovers(active, claim)));
        ClaimModifiedCallback.EVENT.register((previous, updated, player) -> refreshWhere(active ->
                active.claimIds.contains(updated.id()) || overviewCovers(active, updated)));
        ClaimDeletedCallback.EVENT.register((claim, player) -> refreshWhere(active ->
                active.claimIds.contains(claim.id())));
    }

    /**
     * Shows a claim and every subdivision inside it. A 2D subdivision is shown with its whole parent
     * claim; a 3D claim is shown on its own, so layered 3D claims stay readable.
     */
    void visualizeClaim(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot claim,
            @NotNull BlockPos clicked)
    {
        Long id = claim.id();
        Request request = id == null
                ? new BoundsRequest(List.of(new VisualizationTarget(claim.bounds(), styleFor(claim), null)))
                : new ClaimRequest(id);
        render(player, level, request, player.blockPosition(), clicked.getY());
    }

    /**
     * Shows every top-level claim within {@link #NEARBY_RADIUS} blocks.
     *
     * @return how many claims were found
     */
    int visualizeNearbyClaims(@NotNull ServerPlayer player, @NotNull ServerLevel level)
    {
        BlockPos center = player.blockPosition();
        NearbyRequest request = new NearbyRequest(this.claims.worldKey(level), center);
        int found = request.claims(this.claims).size();
        render(player, level, request, center, player.getBlockY() + 1);
        return found;
    }

    void visualizeInitializeBounds(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull BlockPos clicked)
    {
        render(player, level, new BoundsRequest(List.of(
                new VisualizationTarget(bounds, VisualizationStyle.INITIALIZE, null))), player.blockPosition(), clicked.getY());
    }

    void visualizeConflictBounds(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull BlockPos clicked)
    {
        render(player, level, new BoundsRequest(List.of(
                new VisualizationTarget(bounds, VisualizationStyle.CONFLICT, null))), player.blockPosition(), clicked.getY());
    }

    void clear(@NotNull ServerPlayer player)
    {
        ActiveVisualization active = this.activeVisualizations.remove(player.getUUID());
        if (active != null)
        {
            restore(player, active);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Refreshing after claim changes
    // ---------------------------------------------------------------------------------------------

    private static boolean overviewCovers(@NotNull ActiveVisualization active, @NotNull ClaimSnapshot claim)
    {
        return active.request instanceof NearbyRequest nearby && nearby.covers(claim);
    }

    /** Redraws, from the current claims, every visualization a claim change made stale. */
    private void refreshWhere(@NotNull java.util.function.Predicate<ActiveVisualization> affected)
    {
        MinecraftServer current = this.server;
        if (current == null)
        {
            return;
        }
        for (Map.Entry<UUID, ActiveVisualization> entry : new ArrayList<>(this.activeVisualizations.entrySet()))
        {
            ActiveVisualization active = entry.getValue();
            if (!affected.test(active))
            {
                continue;
            }
            ServerPlayer player = current.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != active.level)
            {
                this.activeVisualizations.remove(entry.getKey());
                continue;
            }
            render(player, active.level, active.request, active.visualizeFrom, active.height);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    private void render(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull Request request,
            @NotNull BlockPos visualizeFrom,
            int requestedHeight)
    {
        clear(player);

        List<VisualizationTarget> targets = request.targets(this.claims);
        if (targets.isEmpty())
        {
            return;
        }

        int height = clamp(requestedHeight, level.getMinY(), level.getMaxY());
        boolean waterTransparent = level.getBlockState(visualizeFrom).liquid();
        LinkedHashMap<BlockPos, FakeBlock> fakeBlocks = new LinkedHashMap<>();
        Set<Long> claimIds = new LinkedHashSet<>();
        for (VisualizationTarget target : targets)
        {
            if (target.claimId != null)
            {
                claimIds.add(target.claimId);
            }
            collectFakeBlocks(level, visualizeFrom, height, waterTransparent, target, fakeBlocks);
        }
        if (fakeBlocks.isEmpty())
        {
            return;
        }

        Map<BlockPos, Integer> glowIds = new HashMap<>();
        boolean glow = this.settings.tools().visualizationGlow();
        for (Map.Entry<BlockPos, FakeBlock> entry : fakeBlocks.entrySet())
        {
            sendBlock(player, entry.getKey(), entry.getValue().state);
            if (glow)
            {
                FakeBlock block = entry.getValue();
                glowIds.put(entry.getKey(), FabricGlowingDisplays.spawn(player, level, entry.getKey(), block.state, block.glowColor));
            }
        }

        this.activeVisualizations.put(
                player.getUUID(),
                new ActiveVisualization(
                        level,
                        request,
                        visualizeFrom.immutable(),
                        height,
                        claimIds,
                        fakeBlocks,
                        glowIds,
                        this.tick + VISUALIZATION_TICKS,
                        this.tick + 1L));
    }

    private void handleBlockBreak(@NotNull ServerPlayer player, @NotNull BlockPos pos)
    {
        ActiveVisualization active = this.activeVisualizations.get(player.getUUID());
        if (active == null || player.level() != active.level)
        {
            return;
        }

        BlockPos immutablePos = pos.immutable();
        if (active.fakeBlocks.remove(immutablePos) == null)
        {
            return;
        }

        sendBlock(player, immutablePos, active.level.getBlockState(immutablePos));
        Integer glowId = active.glowIds.remove(immutablePos);
        if (glowId != null)
        {
            FabricGlowingDisplays.remove(player, glowId);
        }
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

        FakeBlock fake = active.fakeBlocks.get(pos.immutable());
        if (fake != null)
        {
            sendBlock(player, pos, fake.state);
        }
    }

    private void expireVisualizations(@NotNull MinecraftServer server)
    {
        this.tick++;
        Iterator<Map.Entry<UUID, ActiveVisualization>> iterator = this.activeVisualizations.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<UUID, ActiveVisualization> entry = iterator.next();
            ActiveVisualization active = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (active.expiresAt <= this.tick)
            {
                if (player != null)
                {
                    restore(player, active);
                }
                iterator.remove();
                continue;
            }

            // The client overwrites the clicked block with the real one right after the tool use
            // that started this visualization, so every fake block is sent once more a tick later.
            if (active.resendAt > 0L && active.resendAt <= this.tick)
            {
                if (player != null)
                {
                    resend(player, active);
                }
                active.resendAt = -1L;
            }
        }
    }

    private static void restore(@NotNull ServerPlayer player, @NotNull ActiveVisualization active)
    {
        if (!active.glowIds.isEmpty())
        {
            FabricGlowingDisplays.remove(player, active.glowIds.values().stream().mapToInt(Integer::intValue).toArray());
        }
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

        for (Map.Entry<BlockPos, FakeBlock> entry : active.fakeBlocks.entrySet())
        {
            sendBlock(player, entry.getKey(), entry.getValue().state);
        }
    }

    private static void collectFakeBlocks(
            @NotNull ServerLevel level,
            @NotNull BlockPos visualizeFrom,
            int height,
            boolean waterTransparent,
            @NotNull VisualizationTarget target,
            @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks)
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
            @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks)
    {
        FakeBlock side = style.side();
        FakeBlock corner = style.corner();
        addHorizontal2D(level, displayZone, bounds.minX(), bounds.maxX(), height, bounds.maxZ(), true,
                waterTransparent, side, fakeBlocks);
        addHorizontal2D(level, displayZone, bounds.minX(), bounds.maxX(), height, bounds.minZ(), true,
                waterTransparent, side, fakeBlocks);
        addHorizontal2D(level, displayZone, bounds.minZ(), bounds.maxZ(), height, bounds.minX(), false,
                waterTransparent, side, fakeBlocks);
        addHorizontal2D(level, displayZone, bounds.minZ(), bounds.maxZ(), height, bounds.maxX(), false,
                waterTransparent, side, fakeBlocks);

        if (bounds.xLength() > 2)
        {
            add2D(level, displayZone, new BlockPos(bounds.minX() + 1, height, bounds.maxZ()),
                    waterTransparent, side, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.minX() + 1, height, bounds.minZ()),
                    waterTransparent, side, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX() - 1, height, bounds.maxZ()),
                    waterTransparent, side, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX() - 1, height, bounds.minZ()),
                    waterTransparent, side, fakeBlocks);
        }

        if (bounds.zLength() > 2)
        {
            add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.minZ() + 1),
                    waterTransparent, side, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.minZ() + 1),
                    waterTransparent, side, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.maxZ() - 1),
                    waterTransparent, side, fakeBlocks);
            add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.maxZ() - 1),
                    waterTransparent, side, fakeBlocks);
        }

        add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.maxZ()),
                waterTransparent, corner, fakeBlocks);
        add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.maxZ()),
                waterTransparent, corner, fakeBlocks);
        add2D(level, displayZone, new BlockPos(bounds.minX(), height, bounds.minZ()),
                waterTransparent, corner, fakeBlocks);
        add2D(level, displayZone, new BlockPos(bounds.maxX(), height, bounds.minZ()),
                waterTransparent, corner, fakeBlocks);
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
            @NotNull FakeBlock block,
            @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks)
    {
        int min = Math.max(start + STEP, xAxis ? displayZone.minX : displayZone.minZ);
        int max = Math.min(end - STEP / 2, xAxis ? displayZone.maxX : displayZone.maxZ);
        for (int value = min; value < max; value += STEP)
        {
            BlockPos pos = xAxis ? new BlockPos(value, height, fixed) : new BlockPos(fixed, height, value);
            add2D(level, displayZone, pos, waterTransparent, block, fakeBlocks);
        }
    }

    private static void draw3D(
            @NotNull ServerLevel level,
            @NotNull ClaimBounds bounds,
            @NotNull DisplayZone displayZone,
            @NotNull VisualizationStyle style,
            @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks)
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
            @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks,
            int y,
            boolean bottom)
    {
        FakeBlock side = style.side();
        FakeBlock corner = style.corner();
        add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.maxZ()), corner, fakeBlocks);
        add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.maxZ()), corner, fakeBlocks);
        add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.minZ()), corner, fakeBlocks);
        add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.minZ()), corner, fakeBlocks);

        if (bounds.xLength() > 2)
        {
            add3D(level, displayZone, new BlockPos(bounds.minX() + 1, y, bounds.maxZ()), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.minX() + 1, y, bounds.minZ()), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX() - 1, y, bounds.maxZ()), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX() - 1, y, bounds.minZ()), side, fakeBlocks);
        }

        if (bounds.zLength() > 2)
        {
            add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.minZ() + 1), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.minZ() + 1), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.minX(), y, bounds.maxZ() - 1), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), y, bounds.maxZ() - 1), side, fakeBlocks);
        }

        int verticalY = bottom ? y + 1 : y - 1;
        if (verticalY >= level.getMinY() && verticalY <= level.getMaxY())
        {
            add3D(level, displayZone, new BlockPos(bounds.minX(), verticalY, bounds.maxZ()), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), verticalY, bounds.maxZ()), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.minX(), verticalY, bounds.minZ()), side, fakeBlocks);
            add3D(level, displayZone, new BlockPos(bounds.maxX(), verticalY, bounds.minZ()), side, fakeBlocks);
        }
    }

    private static void add2D(
            @NotNull ServerLevel level,
            @NotNull DisplayZone displayZone,
            @NotNull BlockPos requested,
            boolean waterTransparent,
            @NotNull FakeBlock block,
            @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks)
    {
        if (!displayZone.contains2D(requested) || !level.isLoaded(requested))
        {
            return;
        }

        BlockPos visiblePos = getVisibleLocation(level, requested, waterTransparent);
        if (displayZone.contains(visiblePos) && level.isLoaded(visiblePos))
        {
            fakeBlocks.put(visiblePos.immutable(), block);
        }
    }

    private static void add3D(
            @NotNull ServerLevel level,
            @NotNull DisplayZone displayZone,
            @NotNull BlockPos pos,
            @NotNull FakeBlock block,
            @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks)
    {
        if (displayZone.contains(pos) && level.isLoaded(pos))
        {
            fakeBlocks.put(pos.immutable(), block);
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

    // ---------------------------------------------------------------------------------------------
    // What to show
    // ---------------------------------------------------------------------------------------------

    private static @NotNull List<VisualizationTarget> claimTree(
            @NotNull ClaimSnapshot selectedClaim,
            @NotNull FabricClaimRepository claims)
    {
        Collection<ClaimSnapshot> loadedClaims = claims.snapshots();
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

        targets.add(new VisualizationTarget(claim.bounds(), style, id));
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

    /** How a nearby-claims overview draws a top-level claim, as Paper's does. */
    private static @NotNull VisualizationStyle nearbyStyleFor(@NotNull ClaimSnapshot claim)
    {
        if (claim.adminClaim())
        {
            return claim.threeDimensional() ? VisualizationStyle.ADMIN_3D : VisualizationStyle.ADMIN;
        }
        return claim.threeDimensional() ? VisualizationStyle.SUBDIVISION_3D : VisualizationStyle.CLAIM;
    }

    private static void sendBlock(
            @NotNull ServerPlayer player,
            @NotNull BlockPos pos,
            @NotNull BlockState state)
    {
        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }

    /**
     * Looks blocks up by id rather than through {@link Blocks} fields: the 26.x adapter is one binary
     * for several releases, and 26.2 replaced fields such as {@code WHITE_WOOL} with color collections.
     */
    private static @NotNull BlockState block(@NotNull String id)
    {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------------------------------------
    // Types
    // ---------------------------------------------------------------------------------------------

    private interface Request
    {
        @NotNull List<VisualizationTarget> targets(@NotNull FabricClaimRepository claims);
    }

    /** One claim and its subdivisions, re-read from the repository whenever it is redrawn. */
    private record ClaimRequest(long claimId) implements Request
    {
        @Override
        public @NotNull List<VisualizationTarget> targets(@NotNull FabricClaimRepository claims)
        {
            ClaimSnapshot claim = claims.claimById(this.claimId);
            return claim == null ? Collections.emptyList() : claimTree(claim, claims);
        }
    }

    /** Every top-level claim around a point. */
    private record NearbyRequest(@NotNull String worldKey, @NotNull BlockPos center) implements Request
    {
        @NotNull List<ClaimSnapshot> claims(@NotNull FabricClaimRepository repository)
        {
            List<ClaimSnapshot> result = new ArrayList<>();
            for (ClaimSnapshot claim : repository.candidates(this.worldKey, area()))
            {
                if (claim.parentId() != null)
                {
                    continue;
                }
                if (claim.threeDimensional()
                        && (claim.bounds().minY() > this.center.getY() + NEARBY_VERTICAL_RANGE
                        || claim.bounds().maxY() < this.center.getY() - NEARBY_VERTICAL_RANGE))
                {
                    continue;
                }
                result.add(claim);
            }
            return result;
        }

        @Override
        public @NotNull List<VisualizationTarget> targets(@NotNull FabricClaimRepository repository)
        {
            List<VisualizationTarget> targets = new ArrayList<>();
            for (ClaimSnapshot claim : claims(repository))
            {
                targets.add(new VisualizationTarget(claim.bounds(), nearbyStyleFor(claim), claim.id()));
            }
            return targets;
        }

        boolean covers(@NotNull ClaimSnapshot claim)
        {
            return this.worldKey.equals(claim.worldKey()) && claim.bounds().intersects(area(), true);
        }

        private @NotNull ClaimBounds area()
        {
            return ClaimBounds.rectangle(
                    this.center.getX() - NEARBY_RADIUS, this.center.getY(), this.center.getZ() - NEARBY_RADIUS,
                    this.center.getX() + NEARBY_RADIUS, this.center.getY(), this.center.getZ() + NEARBY_RADIUS);
        }
    }

    /** Fixed bounds that belong to no stored claim, such as a selection or a conflict. */
    private record BoundsRequest(@NotNull List<VisualizationTarget> targets) implements Request
    {
        @Override
        public @NotNull List<VisualizationTarget> targets(@NotNull FabricClaimRepository claims)
        {
            return this.targets;
        }
    }

    private record FakeBlock(@NotNull BlockState state, int glowColor)
    {
    }

    private enum VisualizationStyle
    {
        CLAIM("glowstone", GlowColor.YELLOW, "gold_block", GlowColor.YELLOW, false),
        ADMIN("glowstone", GlowColor.ORANGE, "pumpkin", GlowColor.ORANGE, false),
        ADMIN_3D("glowstone", GlowColor.ORANGE, "pumpkin", GlowColor.ORANGE, true),
        SUBDIVISION("iron_block", GlowColor.WHITE, "white_wool", GlowColor.WHITE, false),
        SUBDIVISION_3D("iron_block", GlowColor.WHITE, "white_wool", GlowColor.WHITE, true),
        INITIALIZE("diamond_block", GlowColor.AQUA, "diamond_block", GlowColor.AQUA, false),
        CONFLICT("redstone_ore", GlowColor.RED, "netherrack", GlowColor.RED, false);

        private final @NotNull String cornerBlock;
        private final int cornerGlow;
        private final @NotNull String sideBlock;
        private final int sideGlow;
        private final boolean exactPlacement;
        private @Nullable FakeBlock corner;
        private @Nullable FakeBlock side;

        VisualizationStyle(
                @NotNull String cornerBlock,
                int cornerGlow,
                @NotNull String sideBlock,
                int sideGlow,
                boolean exactPlacement)
        {
            this.cornerBlock = cornerBlock;
            this.cornerGlow = cornerGlow;
            this.sideBlock = sideBlock;
            this.sideGlow = sideGlow;
            this.exactPlacement = exactPlacement;
        }

        // Resolved on first draw, once registries are certain to be populated.
        @NotNull FakeBlock corner()
        {
            if (this.corner == null)
            {
                this.corner = new FakeBlock(block(this.cornerBlock), this.cornerGlow);
            }
            return this.corner;
        }

        @NotNull FakeBlock side()
        {
            if (this.side == null)
            {
                this.side = new FakeBlock(block(this.sideBlock), this.sideGlow);
            }
            return this.side;
        }
    }

    /** Outline colors Paper's glowing visualization uses for each boundary type. */
    private static final class GlowColor
    {
        private static final int YELLOW = 0xFFFF00;
        private static final int ORANGE = 0xFFA500;
        private static final int WHITE = 0xFFFFFF;
        private static final int AQUA = 0x00FFFF;
        private static final int RED = 0xFF0000;

        private GlowColor()
        {
        }
    }

    private static final class VisualizationTarget
    {
        private final @NotNull ClaimBounds bounds;
        private final @NotNull VisualizationStyle style;
        private final @Nullable Long claimId;

        private VisualizationTarget(
                @NotNull ClaimBounds bounds,
                @NotNull VisualizationStyle style,
                @Nullable Long claimId)
        {
            this.bounds = bounds;
            this.style = style;
            this.claimId = claimId;
        }
    }

    private static final class ActiveVisualization
    {
        private final @NotNull ServerLevel level;
        private final @NotNull Request request;
        private final @NotNull BlockPos visualizeFrom;
        private final int height;
        private final @NotNull Set<Long> claimIds;
        private final @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks;
        private final @NotNull Map<BlockPos, Integer> glowIds;
        private final long expiresAt;
        private long resendAt;

        private ActiveVisualization(
                @NotNull ServerLevel level,
                @NotNull Request request,
                @NotNull BlockPos visualizeFrom,
                int height,
                @NotNull Set<Long> claimIds,
                @NotNull LinkedHashMap<BlockPos, FakeBlock> fakeBlocks,
                @NotNull Map<BlockPos, Integer> glowIds,
                long expiresAt,
                long resendAt)
        {
            this.level = level;
            this.request = request;
            this.visualizeFrom = visualizeFrom;
            this.height = height;
            this.claimIds = claimIds;
            this.fakeBlocks = fakeBlocks;
            this.glowIds = glowIds;
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
