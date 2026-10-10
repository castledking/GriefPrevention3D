package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.messages.MessageKey;
import com.griefprevention.protection.BlockUseKind;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.GlowInkSacItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.InkSacItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Player-driven protection: breaking, placing, using blocks, items and entities, and melee attacks.
 * Environmental grief (fire, fluids, pistons, explosions, mobs) is handled by the mixins behind
 * {@link FabricWorldProtection}.
 */
final class FabricProtectionHooks
{
    /** By id: the tag and its BlockTags field only exist from 1.21.9; earlier releases have no statues. */
    private static final TagKey<Block> COPPER_GOLEM_STATUES =
            TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("copper_golem_statues"));

    private final FabricClaimRepository claims;
    private final FabricDenialFeedback feedback;
    private final FabricSettings settings;

    FabricProtectionHooks(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricSettings settings)
    {
        this.claims = claims;
        this.feedback = feedback;
        this.settings = settings;
    }

    void register()
    {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                canUseClaim(level, player, pos, ClaimTrustLevel.BUILD));

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
                handleBlockUse(player, level, hand, hitResult));

        // Hitting a dragon egg teleports it, which Paper treats as building.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                !(level.getBlockState(pos).getBlock() instanceof DragonEggBlock)
                        || canUseClaim(level, player, pos, ClaimTrustLevel.BUILD)
                        ? InteractionResult.PASS
                        : InteractionResult.FAIL);

        UseItemCallback.EVENT.register(this::handleItemUse);

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
                level.isClientSide() || FabricWorldProtection.mayAttack(entity, level, player)
                        ? InteractionResult.PASS
                        : InteractionResult.FAIL);

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
                handleEntityUse(player, level, hand, entity));
    }

    private @NotNull InteractionResult handleBlockUse(
            @NotNull Player player,
            @NotNull Level level,
            @NotNull InteractionHand hand,
            @NotNull BlockHitResult hitResult)
    {
        BlockPos clickedPos = hitResult.getBlockPos();
        ItemStack stack = player.getItemInHand(hand);
        BlockState clickedState = level.getBlockState(clickedPos);
        Block clicked = clickedState.getBlock();
        boolean reshapes = reshapesWorld(stack)
                && !isSwitchable(clicked)
                && clickedState.getMenuProvider(level, clickedPos) == null;
        if (requiresBuildTrust(stack) || reshapes)
        {
            BlockPos targetPos = clickedPos.relative(hitResult.getDirection());
            boolean allowed = canUseClaim(level, player, clickedPos, ClaimTrustLevel.BUILD)
                    && canUseClaim(level, player, targetPos, ClaimTrustLevel.BUILD);
            if (allowed)
            {
                warnAboutUnclaimedPiston(player, level, stack, targetPos);
            }
            return allowed ? InteractionResult.PASS : InteractionResult.FAIL;
        }

        BlockEntity blockEntity = level.getBlockEntity(clickedPos);
        ClaimTrustLevel requiredTrust;
        if (blockEntity instanceof SignBlockEntity sign)
        {
            // An unwaxed sign opens its editor, and editing is building on Paper. A waxed sign can
            // only run the click commands its author gave it, which visitors must still reach.
            if (sign.isWaxed())
            {
                return InteractionResult.PASS;
            }
            requiredTrust = ClaimTrustLevel.BUILD;
        }
        else
        {
            requiredTrust = this.settings.blockUse().requiredTrust(useKind(clickedState, blockEntity, stack));
            if (requiredTrust == null)
            {
                return InteractionResult.PASS;
            }
        }
        return canUseClaim(level, player, clickedPos, requiredTrust)
                ? InteractionResult.PASS
                : InteractionResult.FAIL;
    }

    /**
     * Using an item on the world rather than on a clicked block: emptying or filling a bucket,
     * setting a boat or lily pad on water. The client only reports the item use, so the target is
     * found the way the item itself finds it, by looking along the player's view.
     */
    private @NotNull InteractionResult handleItemUse(
            @NotNull Player player,
            @NotNull Level level,
            @NotNull InteractionHand hand)
    {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        ClipContext.Fluid fluidMode;
        if (item instanceof BucketItem || item instanceof SolidBucketItem)
        {
            // An empty bucket picks up the source it looks at; a full one ignores fluids.
            fluidMode = item instanceof BucketItem && isEmptyBucket(stack) ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        }
        else if (item instanceof PlaceOnWaterBlockItem || item instanceof SpawnEggItem)
        {
            fluidMode = ClipContext.Fluid.SOURCE_ONLY;
        }
        else if (item instanceof BoatItem)
        {
            fluidMode = ClipContext.Fluid.ANY;
        }
        else
        {
            return InteractionResult.PASS;
        }

        Vec3 eye = serverPlayer.getEyePosition();
        Vec3 end = eye.add(serverPlayer.getViewVector(1.0F).scale(serverPlayer.blockInteractionRange()));
        BlockHitResult hit = serverLevel.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluidMode, serverPlayer));
        if (hit.getType() != HitResult.Type.BLOCK)
        {
            return InteractionResult.PASS;
        }

        BlockPos hitPos = hit.getBlockPos();
        if (item instanceof SpawnEggItem && serverLevel.getFluidState(hitPos).isEmpty())
        {
            // Used in the air, a spawn egg only acts on a liquid it looks at.
            return InteractionResult.PASS;
        }
        BlockPos adjacentPos = hitPos.relative(hit.getDirection());
        if (canUseClaim(level, player, hitPos, ClaimTrustLevel.BUILD)
                && canUseClaim(level, player, adjacentPos, ClaimTrustLevel.BUILD))
        {
            return InteractionResult.PASS;
        }

        // The client already predicted the result; put the blocks and the held item back.
        serverPlayer.connection.send(new ClientboundBlockUpdatePacket(serverLevel, hitPos));
        serverPlayer.connection.send(new ClientboundBlockUpdatePacket(serverLevel, adjacentPos));
        serverPlayer.containerMenu.sendAllDataToRemote();
        return InteractionResult.FAIL;
    }

    private @NotNull InteractionResult handleEntityUse(
            @NotNull Player player,
            @NotNull Level level,
            @NotNull InteractionHand hand,
            @NotNull Entity entity)
    {
        // Frames and stands change what is on display; Paper treats that as building.
        ClaimTrustLevel requiredTrust = requiresBuildTrust(player.getItemInHand(hand))
                || entity instanceof ItemFrame
                || entity instanceof ArmorStand
                ? ClaimTrustLevel.BUILD
                : ClaimTrustLevel.CONTAINER;
        return canUseClaim(level, player, entity.blockPosition(), requiredTrust)
                ? InteractionResult.PASS
                : InteractionResult.FAIL;
    }

    /**
     * Paper warns whoever places a piston outside every claim while pistons only work inside
     * claims, since it will never move anything there.
     */
    private void warnAboutUnclaimedPiston(
            @NotNull Player player,
            @NotNull Level level,
            @NotNull ItemStack stack,
            @NotNull BlockPos placedAt)
    {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)
                || !(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof PistonBaseBlock)
                || !FabricWorldProtection.pistonsNeedClaims(serverLevel)
                || this.claims.findClaimAt(serverLevel, placedAt) != null)
        {
            return;
        }
        this.feedback.send(serverPlayer, FabricDenialFeedback.TextMode.WARNING, MessageKey.NO_PISTONS_OUTSIDE_CLAIMS);
    }

    private boolean canUseClaim(
            @NotNull Level level,
            @NotNull Player player,
            @NotNull BlockPos pos,
            @NotNull ClaimTrustLevel levelRequired)
    {
        if (level.isClientSide() || !(level instanceof ServerLevel))
        {
            return true;
        }

        ClaimSnapshot claim = this.claims.findClaimAt((ServerLevel) level, pos);
        if (claim == null)
        {
            return true;
        }

        if (this.claims.allows(claim, player, levelRequired))
        {
            return true;
        }

        this.feedback.denied(player, claim, levelRequired);
        return false;
    }

    /** Items whose use on a block places or changes something, as Paper lists them. */
    private static boolean requiresBuildTrust(@NotNull ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }

        Item item = stack.getItem();
        return item instanceof BlockItem
                || item instanceof BucketItem
                || item instanceof SolidBucketItem
                || item instanceof FlintAndSteelItem
                || item instanceof FireChargeItem
                || item instanceof BoneMealItem
                || item instanceof SpawnEggItem
                || item instanceof HangingEntityItem
                || item instanceof ArmorStandItem
                || item instanceof MinecartItem
                || item instanceof BoatItem
                || item instanceof PlaceOnWaterBlockItem
                || item instanceof EndCrystalItem
                // Dyes, ink and honeycomb recolor, glow or wax signs and wax copper.
                || item instanceof DyeItem
                || item instanceof InkSacItem
                || item instanceof GlowInkSacItem
                || item instanceof HoneycombItem
                || item instanceof BrushItem;
    }

    /**
     * Axes strip and scrape, hoes till, shovels flatten paths. Looked up by tag: 26.3 turned these
     * tools into plain items with components, removing their classes.
     */
    private static boolean reshapesWorld(@NotNull ItemStack stack)
    {
        return stack.is(ItemTags.AXES) || stack.is(ItemTags.HOES) || stack.is(ItemTags.SHOVELS);
    }

    /** Blocks a player operates by hand, which holding a tool must not turn into building. */
    private static boolean isSwitchable(@NotNull Block block)
    {
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock;
    }

    /** Sorts a right-clicked block into the groups Paper's interaction rules tell apart. */
    private static @NotNull BlockUseKind useKind(
            @NotNull BlockState state,
            @Nullable BlockEntity blockEntity,
            @NotNull ItemStack stack)
    {
        Block block = state.getBlock();
        if (block instanceof LecternBlock)
        {
            // An empty lectern takes the book in hand; any other click opens its book to read.
            return !state.getValue(LecternBlock.HAS_BOOK) && stack.is(ItemTags.LECTERN_BOOKS)
                    ? BlockUseKind.LECTERN_BOOK
                    : BlockUseKind.LECTERN;
        }
        // Inventories, as Bukkit's InventoryHolder blocks: ender chests, campfires and lecterns are not.
        if (blockEntity instanceof Container || isContainerLike(block))
        {
            return BlockUseKind.CONTAINER;
        }
        if (block instanceof DoorBlock)
        {
            return BlockUseKind.DOOR;
        }
        if (block instanceof TrapDoorBlock)
        {
            return BlockUseKind.TRAPDOOR;
        }
        if (block instanceof FenceGateBlock)
        {
            return BlockUseKind.FENCE_GATE;
        }
        if (block instanceof BedBlock)
        {
            return BlockUseKind.BED;
        }
        if (block instanceof ButtonBlock || block instanceof LeverBlock)
        {
            return BlockUseKind.SWITCH;
        }
        if (block instanceof NoteBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock
                || block instanceof DaylightDetectorBlock
                || isRedstoneWire(block)
                || block instanceof DragonEggBlock
                || state.is(BlockTags.FLOWER_POTS)
                || state.is(BlockTags.CANDLES)
                || state.is(COPPER_GOLEM_STATUES))
        {
            return BlockUseKind.BUILD;
        }
        return BlockUseKind.UNPROTECTED;
    }

    /** By id: 26.3 renamed {@code RedStoneWireBlock} to {@code RedstoneWireBlock}. */
    private static boolean isRedstoneWire(@NotNull Block block)
    {
        return "minecraft:redstone_wire".equals(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    /** Blocks with no inventory that Paper still guards with container trust. */
    private static boolean isContainerLike(@NotNull Block block)
    {
        return block instanceof BeaconBlock
                || block instanceof BeehiveBlock
                || block instanceof BellBlock
                || block instanceof CakeBlock
                || block instanceof CandleCakeBlock
                || block instanceof AbstractCauldronBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof CaveVinesBlock
                || block instanceof CaveVinesPlantBlock
                || block instanceof PumpkinBlock
                || block instanceof AnvilBlock;
    }

    private static boolean isEmptyBucket(@NotNull ItemStack stack)
    {
        return "minecraft:bucket".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
