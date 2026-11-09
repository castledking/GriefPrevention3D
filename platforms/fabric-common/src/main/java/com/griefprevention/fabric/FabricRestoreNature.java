package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restore nature commands and shovel interactions, matching Paper's behavior:
 * <ul>
 *     <li>{@code /restorenature} - switches the modification tool to restoration mode.</li>
 *     <li>{@code /restorenatureaggressive} - switches to aggressive restoration mode (overrides claims, clears ores/trees).</li>
 *     <li>{@code /restorenaturefill [radius]} - switches to fill mode with a radius between 1 and 10 (default 2).</li>
 *     <li>{@code /aclaim restore [nature|aggressive|fill] [radius]} - unified subcommand for restoration.</li>
 * </ul>
 * Right-clicking with the modification tool restores an area around the clicked block back to natural terrain,
 * removing player-placed blocks, floating artifacts, thin walls, filling holes, and replanting surface grass/sand.
 */
final class FabricRestoreNature
{
    private static final int DEFAULT_RESTORE_RADIUS = 5;
    private static final int DEFAULT_FILL_RADIUS = 2;
    private static final int MIN_FILL_RADIUS = 1;
    private static final int MAX_FILL_RADIUS = 10;
    private static final int BLOCK_UPDATE_FLAGS = Block.UPDATE_ALL;

    private final FabricClaimRepository claims;
    private final FabricFakeBlockVisualization visualization;
    private final FabricDenialFeedback feedback;
    private final FabricClaimModes modes;
    private final FabricSettings settings;
    private final Logger logger;

    private final Map<UUID, Integer> fillRadii = new ConcurrentHashMap<>();

    FabricRestoreNature(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricFakeBlockVisualization visualization,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricClaimModes modes,
            @NotNull FabricSettings settings,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.visualization = visualization;
        this.feedback = feedback;
        this.modes = modes;
        this.settings = settings;
        this.logger = logger;
    }

    void register()
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                clear(handler.getPlayer().getUUID()));
    }

    void clear(@NotNull UUID playerId)
    {
        this.fillRadii.remove(playerId);
        FabricClaimModes.Mode current = this.modes.mode(playerId);
        if (current.isRestoreNature())
        {
            this.modes.set(playerId, FabricClaimModes.Mode.BASIC);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------

    boolean restoreCommand(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }

        if (args.length > 0)
        {
            String mode = args[0].toLowerCase(Locale.ROOT);
            switch (mode)
            {
                case "aggressive":
                    return restoreAggressive(sender, tail(args));
                case "fill":
                    return restoreFill(sender, tail(args));
                default:
                    return restoreNature(sender, tail(args));
            }
        }

        return restoreNature(sender, args);
    }

    boolean restoreNature(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.RESTORE_NATURE))
        {
            return true;
        }

        this.modes.set(player.getUUID(), FabricClaimModes.Mode.RESTORE_NATURE);
        sender.send(TextMode.SUCCESS, MessageKey.RESTORE_NATURE_ACTIVATE);
        return true;
    }

    boolean restoreAggressive(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.RESTORE_NATURE_AGGRESSIVE))
        {
            return true;
        }

        this.modes.set(player.getUUID(), FabricClaimModes.Mode.RESTORE_NATURE_AGGRESSIVE);
        sender.send(TextMode.SUCCESS, MessageKey.RESTORE_NATURE_AGGRESSIVE_ACTIVATE);
        return true;
    }

    boolean restoreFill(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.RESTORE_NATURE_AGGRESSIVE))
        {
            return true;
        }

        int radius = DEFAULT_FILL_RADIUS;
        if (args.length > 0)
        {
            try
            {
                radius = Math.max(MIN_FILL_RADIUS, Math.min(MAX_FILL_RADIUS, Integer.parseInt(args[0])));
            }
            catch (NumberFormatException ignored)
            {
                radius = DEFAULT_FILL_RADIUS;
            }
        }

        this.fillRadii.put(player.getUUID(), radius);
        this.modes.set(player.getUUID(), FabricClaimModes.Mode.RESTORE_NATURE_FILL);
        sender.send(TextMode.SUCCESS, MessageKey.FILL_MODE_ACTIVE, String.valueOf(radius));
        return true;
    }

    private static String[] tail(String[] args)
    {
        return args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    }

    // ---------------------------------------------------------------------------------------------
    // Shovel Click Interaction
    // ---------------------------------------------------------------------------------------------

    void restoreAt(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull BlockPos clicked,
            @NotNull FabricClaimModes.Mode mode)
    {
        String requiredPermission = (mode == FabricClaimModes.Mode.RESTORE_NATURE_AGGRESSIVE
                || mode == FabricClaimModes.Mode.RESTORE_NATURE_FILL)
                ? FabricPermissionDefaults.RESTORE_NATURE_AGGRESSIVE
                : FabricPermissionDefaults.RESTORE_NATURE;

        if (!this.claims.hasPermission(player, requiredPermission))
        {
            this.feedback.sendRateLimitedError(player, MessageKey.NO_BUILD_PERMISSION, "an administrator");
            return;
        }

        int radius = DEFAULT_RESTORE_RADIUS;
        if (mode == FabricClaimModes.Mode.RESTORE_NATURE_FILL)
        {
            radius = this.fillRadii.getOrDefault(player.getUUID(), DEFAULT_FILL_RADIUS);
            radius = Math.max(MIN_FILL_RADIUS, Math.min(MAX_FILL_RADIUS, radius));
        }

        boolean aggressive = (mode == FabricClaimModes.Mode.RESTORE_NATURE_AGGRESSIVE);

        int centerX = clicked.getX();
        int centerZ = clicked.getZ();
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY;
        int sizeZ = maxZ - minZ + 1;

        this.feedback.sendText(player, TextMode.INFO, "Restoring nature in the selected area...");

        BlockState[][][] snapshots = new BlockState[sizeX][sizeY][sizeZ];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sizeX; x++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int y = 0; y < sizeY; y++)
                {
                    cursor.set(minX + x, minY + y, minZ + z);
                    snapshots[x][y][z] = level.getBlockState(cursor);
                }
            }
        }

        String biomeName = level.getBiome(clicked).unwrapKey().map(k -> k.identifier().getPath()).orElse("").toUpperCase(Locale.ROOT);

        boolean isNether = level.dimension() == Level.NETHER;
        boolean isEnd = level.dimension() == Level.END;

        CompletableFuture.runAsync(() -> {
            processSnapshots(snapshots, sizeX, sizeY, sizeZ, isNether, isEnd, biomeName, aggressive);
        }).thenRun(() -> {
            level.getServer().execute(() -> {
                applyChanges(player, level, clicked, snapshots, minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeY, sizeZ, aggressive);
            });
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Terrain Processing (Async)
    // ---------------------------------------------------------------------------------------------

    private static void processSnapshots(
            BlockState[][][] snapshots,
            int sizeX,
            int sizeY,
            int sizeZ,
            boolean isNether,
            boolean isEnd,
            String biomeName,
            boolean aggressive)
    {
        removeHanging(snapshots, sizeX, sizeY, sizeZ, aggressive);
        removePlayerBlocks(snapshots, sizeX, sizeY, sizeZ, isNether, aggressive);
        removeWalls(snapshots, sizeX, sizeY, sizeZ);

        BlockState fillBlock = getFillBlock(isNether, isEnd);
        fillHoles(snapshots, sizeX, sizeY, sizeZ, fillBlock);

        BlockState surfaceBlock = getSurfaceBlock(isNether, isEnd, biomeName);
        BlockState underBlock = getUnderSurfaceBlock(isNether, isEnd, biomeName);
        coverSurface(snapshots, sizeX, sizeY, sizeZ, surfaceBlock, underBlock);

        removeHanging(snapshots, sizeX, sizeY, sizeZ, aggressive);
    }

    private static void removeHanging(BlockState[][][] snapshots, int sizeX, int sizeY, int sizeZ, boolean aggressive)
    {
        for (int x = 1; x < sizeX - 1; x++)
        {
            for (int z = 1; z < sizeZ - 1; z++)
            {
                for (int y = 1; y < sizeY; y++)
                {
                    BlockState state = snapshots[x][y][z];
                    BlockState below = snapshots[x][y - 1][z];
                    if (state != null && below != null && below.isAir() && isNotAllowedToHang(state, aggressive))
                    {
                        snapshots[x][y][z] = Blocks.AIR.defaultBlockState();
                    }
                }
            }
        }
    }

    private static void removePlayerBlocks(
            BlockState[][][] snapshots,
            int sizeX,
            int sizeY,
            int sizeZ,
            boolean isNether,
            boolean aggressive)
    {
        for (int x = 1; x < sizeX - 1; x++)
        {
            for (int z = 1; z < sizeZ - 1; z++)
            {
                for (int y = 0; y < sizeY; y++)
                {
                    BlockState state = snapshots[x][y][z];
                    if (state != null && isPlayerBlock(state, isNether, aggressive))
                    {
                        snapshots[x][y][z] = Blocks.AIR.defaultBlockState();
                    }
                }
            }
        }
    }

    private static void removeWalls(BlockState[][][] snapshots, int sizeX, int sizeY, int sizeZ)
    {
        for (int x = 1; x < sizeX - 1; x++)
        {
            for (int z = 1; z < sizeZ - 1; z++)
            {
                for (int y = 0; y < sizeY - 1; y++)
                {
                    BlockState state = snapshots[x][y][z];
                    if (state == null || !isSolid(state))
                    {
                        continue;
                    }

                    // Check X-axis walls
                    BlockState left = snapshots[x - 1][y][z];
                    BlockState right = snapshots[x + 1][y][z];
                    if (left != null && right != null && left.isAir() && right.isAir())
                    {
                        snapshots[x][y][z] = Blocks.AIR.defaultBlockState();
                        continue;
                    }

                    // Check Z-axis walls
                    BlockState front = snapshots[x][y][z - 1];
                    BlockState back = snapshots[x][y][z + 1];
                    if (front != null && back != null && front.isAir() && back.isAir())
                    {
                        snapshots[x][y][z] = Blocks.AIR.defaultBlockState();
                    }
                }
            }
        }
    }

    private static void fillHoles(BlockState[][][] snapshots, int sizeX, int sizeY, int sizeZ, BlockState fillBlock)
    {
        for (int x = 1; x < sizeX - 1; x++)
        {
            for (int z = 1; z < sizeZ - 1; z++)
            {
                int surfaceY = -1;
                for (int y = sizeY - 1; y >= 0; y--)
                {
                    BlockState state = snapshots[x][y][z];
                    if (state != null && !state.isAir() && !state.liquid() && !state.is(BlockTags.LEAVES))
                    {
                        surfaceY = y;
                        break;
                    }
                }

                if (surfaceY <= 0)
                {
                    continue;
                }

                for (int y = 0; y < surfaceY; y++)
                {
                    BlockState state = snapshots[x][y][z];
                    if (state != null && state.isAir())
                    {
                        int solidNeighbors = 0;
                        if (x > 0 && isSolid(snapshots[x - 1][y][z])) solidNeighbors++;
                        if (x < sizeX - 1 && isSolid(snapshots[x + 1][y][z])) solidNeighbors++;
                        if (z > 0 && isSolid(snapshots[x][y][z - 1])) solidNeighbors++;
                        if (z < sizeZ - 1 && isSolid(snapshots[x][y][z + 1])) solidNeighbors++;

                        if (solidNeighbors >= 3)
                        {
                            snapshots[x][y][z] = fillBlock;
                        }
                    }
                }
            }
        }
    }

    private static void coverSurface(
            BlockState[][][] snapshots,
            int sizeX,
            int sizeY,
            int sizeZ,
            BlockState surfaceBlock,
            BlockState underBlock)
    {
        for (int x = 1; x < sizeX - 1; x++)
        {
            for (int z = 1; z < sizeZ - 1; z++)
            {
                for (int y = sizeY - 2; y >= 0; y--)
                {
                    BlockState state = snapshots[x][y][z];
                    BlockState above = snapshots[x][y + 1][z];

                    if (state != null && above != null && isSolid(state) && above.isAir())
                    {
                        if (isDirtOrStone(state))
                        {
                            snapshots[x][y][z] = surfaceBlock;

                            if (y > 0)
                            {
                                BlockState below = snapshots[x][y - 1][z];
                                if (below != null && isStone(below))
                                {
                                    snapshots[x][y - 1][z] = underBlock;
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Terrain Application (Main Thread)
    // ---------------------------------------------------------------------------------------------

    private void applyChanges(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull BlockPos clicked,
            BlockState[][][] snapshots,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            boolean aggressive)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 1; x < sizeX - 1; x++)
        {
            for (int z = 1; z < sizeZ - 1; z++)
            {
                for (int y = 0; y < sizeY; y++)
                {
                    BlockState newState = snapshots[x][y][z];
                    if (newState == null)
                    {
                        continue;
                    }

                    pos.set(minX + x, minY + y, minZ + z);
                    BlockState current = level.getBlockState(pos);

                    if (newState != current)
                    {
                        if (!aggressive && this.claims.findClaimAt(level, pos) != null)
                        {
                            continue;
                        }

                        level.setBlock(pos, newState, BLOCK_UPDATE_FLAGS);
                    }
                }
            }
        }

        cleanupEntities(level, minX + 1, minY, minZ + 1, maxX - 1, maxY, maxZ - 1, aggressive);

        ClaimBounds visualBounds = ClaimBounds.rectangle(minX + 1, minY, minZ + 1, maxX - 1, maxY, maxZ - 1);
        this.visualization.visualizeRestoreArea(player, level, visualBounds, clicked);
        this.feedback.send(player, TextMode.SUCCESS, MessageKey.RESTORE_NATURE_ACTIVATE);
    }

    private void cleanupEntities(
            @NotNull ServerLevel level,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            boolean aggressive)
    {
        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY, maxZ + 1);
        for (Entity entity : level.getEntities((Entity) null, box, e -> true))
        {
            if (entity instanceof Player || entity instanceof Animal)
            {
                BlockPos feet = entity.blockPosition();
                if (isInBounds(feet, minX, maxX, minZ, maxZ))
                {
                    if (!aggressive && this.claims.findClaimAt(level, feet) != null)
                    {
                        continue;
                    }
                    if (!level.getBlockState(feet).isAir())
                    {
                        level.setBlock(feet, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
                    }
                    BlockPos head = feet.above();
                    if (!level.getBlockState(head).isAir())
                    {
                        level.setBlock(head, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
                    }
                }
            }
            else if (entity instanceof HangingEntity || entity instanceof ItemEntity)
            {
                BlockPos pos = entity.blockPosition();
                if (isInBounds(pos, minX, maxX, minZ, maxZ))
                {
                    if (aggressive || this.claims.findClaimAt(level, pos) == null)
                    {
                        entity.discard();
                    }
                }
            }
        }
    }

    private static boolean isInBounds(BlockPos pos, int minX, int maxX, int minZ, int maxZ)
    {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    // ---------------------------------------------------------------------------------------------
    // Block Classification
    // ---------------------------------------------------------------------------------------------

    private static boolean isSolid(@Nullable BlockState state)
    {
        return state != null && !state.isAir() && !state.liquid() && state.isSolid();
    }

    private static boolean isDirtOrStone(@NotNull BlockState state)
    {
        return state.is(Blocks.DIRT)
                || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DEEPSLATE);
    }

    private static boolean isStone(@NotNull BlockState state)
    {
        return state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.DEEPSLATE);
    }

    private static boolean isNotAllowedToHang(@NotNull BlockState state, boolean aggressive)
    {
        if (state.is(Blocks.DIRT)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(BlockTags.LOGS))
        {
            return true;
        }

        if (aggressive)
        {
            return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE);
        }

        return false;
    }

    private static boolean isPlayerBlock(@NotNull BlockState state, boolean isNether, boolean aggressive)
    {
        Block block = state.getBlock();

        // Common structural and functional blocks
        if (block instanceof SignBlock
                || block instanceof WallSignBlock
                || block instanceof CeilingHangingSignBlock
                || block instanceof WallHangingSignBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof WallBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof BaseRailBlock
                || block instanceof SlabBlock
                || block instanceof StairBlock
                || block instanceof BedBlock
                || block instanceof CarpetBlock
                || block instanceof WoolCarpetBlock
                || block instanceof IronBarsBlock
                || block instanceof ButtonBlock
                || block instanceof PressurePlateBlock
                || block instanceof LeverBlock
                || block instanceof AnvilBlock
                || block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof CraftingTableBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock
                || block instanceof HopperBlock
                || block instanceof ObserverBlock
                || block instanceof NoteBlock
                || block instanceof JukeboxBlock
                || block instanceof PistonBaseBlock
                || block instanceof PistonHeadBlock
                || block instanceof MovingPistonBlock
                || block instanceof EnchantingTableBlock
                || block instanceof BrewingStandBlock
                || block instanceof CauldronBlock
                || block instanceof EnderChestBlock
                || block instanceof BeaconBlock
                || block instanceof WebBlock
                || block instanceof SpawnerBlock
                || block instanceof TorchBlock
                || block instanceof WallTorchBlock
                || block instanceof LadderBlock
                || block instanceof TntBlock)
        {
            return true;
        }

        if (state.is(Blocks.FARMLAND)
                || state.is(BlockTags.IMPERMEABLE)
                || state.is(BlockTags.WOOL)
                || state.is(BlockTags.PLANKS)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.CHISELED_STONE_BRICKS)
                || state.is(Blocks.BRICKS)
                || state.is(Blocks.MUD_BRICKS)
                || state.is(Blocks.NETHER_BRICKS)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.JACK_O_LANTERN)
                || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.BOOKSHELF)
                || state.is(Blocks.CHISELED_BOOKSHELF))
        {
            return true;
        }

        if (!isNether && (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)))
        {
            return true;
        }

        if (isNether && state.is(Blocks.GLOWSTONE))
        {
            return true;
        }

        if (aggressive)
        {
            if (state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.LOGS)
                    || state.is(Blocks.BEDROCK)
                    || state.is(Blocks.MELON)
                    || state.is(Blocks.MELON_STEM)
                    || state.is(Blocks.ATTACHED_MELON_STEM)
                    || state.is(Blocks.PUMPKIN)
                    || state.is(Blocks.PUMPKIN_STEM)
                    || state.is(Blocks.ATTACHED_PUMPKIN_STEM)
                    || state.is(Blocks.COAL_ORE)
                    || state.is(Blocks.DEEPSLATE_COAL_ORE)
                    || state.is(Blocks.IRON_ORE)
                    || state.is(Blocks.DEEPSLATE_IRON_ORE)
                    || state.is(Blocks.COPPER_ORE)
                    || state.is(Blocks.DEEPSLATE_COPPER_ORE)
                    || state.is(Blocks.GOLD_ORE)
                    || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                    || state.is(Blocks.REDSTONE_ORE)
                    || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
                    || state.is(Blocks.EMERALD_ORE)
                    || state.is(Blocks.DEEPSLATE_EMERALD_ORE)
                    || state.is(Blocks.LAPIS_ORE)
                    || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
                    || state.is(Blocks.DIAMOND_ORE)
                    || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                    || state.is(Blocks.NETHER_GOLD_ORE)
                    || state.is(Blocks.NETHER_QUARTZ_ORE))
            {
                return true;
            }
        }

        return false;
    }

    private static BlockState getFillBlock(boolean isNether, boolean isEnd)
    {
        if (isNether)
        {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (isEnd)
        {
            return Blocks.END_STONE.defaultBlockState();
        }
        return Blocks.DIRT.defaultBlockState();
    }

    private static BlockState getSurfaceBlock(boolean isNether, boolean isEnd, String biomeName)
    {
        if (isNether)
        {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (isEnd)
        {
            return Blocks.END_STONE.defaultBlockState();
        }

        if (biomeName.contains("DESERT") || biomeName.contains("BEACH"))
        {
            return Blocks.SAND.defaultBlockState();
        }
        if (biomeName.contains("BADLANDS") || biomeName.contains("MESA"))
        {
            return Blocks.RED_SAND.defaultBlockState();
        }
        if (biomeName.contains("MUSHROOM"))
        {
            return Blocks.MYCELIUM.defaultBlockState();
        }

        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    private static BlockState getUnderSurfaceBlock(boolean isNether, boolean isEnd, String biomeName)
    {
        if (isNether)
        {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (isEnd)
        {
            return Blocks.END_STONE.defaultBlockState();
        }

        if (biomeName.contains("DESERT") || biomeName.contains("BEACH"))
        {
            return Blocks.SAND.defaultBlockState();
        }
        if (biomeName.contains("BADLANDS") || biomeName.contains("MESA"))
        {
            return Blocks.RED_SAND.defaultBlockState();
        }

        return Blocks.DIRT.defaultBlockState();
    }
}
