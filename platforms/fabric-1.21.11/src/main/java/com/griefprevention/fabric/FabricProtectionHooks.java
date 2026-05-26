package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimAccessSubject;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

final class FabricProtectionHooks
{
    private final FabricClaimRepository claims;

    FabricProtectionHooks(@NotNull FabricClaimRepository claims)
    {
        this.claims = claims;
    }

    void register()
    {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                canUseClaim(level, player, pos, ClaimTrustLevel.BUILD));

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
                handleBlockUse(player, level, hand, hitResult));

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
                canUseClaim(level, player, entity.blockPosition(), ClaimTrustLevel.BUILD)
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
        if (requiresBuildTrust(stack))
        {
            BlockPos targetPos = clickedPos.relative(hitResult.getDirection());
            return canUseClaim(level, player, clickedPos, ClaimTrustLevel.BUILD)
                    && canUseClaim(level, player, targetPos, ClaimTrustLevel.BUILD)
                    ? InteractionResult.PASS
                    : InteractionResult.FAIL;
        }

        ClaimTrustLevel requiredTrust = level.getBlockEntity(clickedPos) == null
                ? ClaimTrustLevel.ACCESS
                : ClaimTrustLevel.CONTAINER;
        return canUseClaim(level, player, clickedPos, requiredTrust)
                ? InteractionResult.PASS
                : InteractionResult.FAIL;
    }

    private @NotNull InteractionResult handleEntityUse(
            @NotNull Player player,
            @NotNull Level level,
            @NotNull InteractionHand hand,
            @NotNull Entity entity)
    {
        ClaimTrustLevel requiredTrust = requiresBuildTrust(player.getItemInHand(hand))
                ? ClaimTrustLevel.BUILD
                : ClaimTrustLevel.CONTAINER;
        return canUseClaim(level, player, entity.blockPosition(), requiredTrust)
                ? InteractionResult.PASS
                : InteractionResult.FAIL;
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

        ClaimTrustSnapshot trust = this.claims.trustFor(claim);
        if (trust == null)
        {
            trust = ClaimTrustSnapshot.empty(claim.ownerId());
        }

        ClaimAccessSubject subject = ClaimAccessSubject.of(player.getUUID());
        if (trust.hasExplicitPermission(subject, levelRequired))
        {
            return true;
        }

        return !trust.isPermissionDenied(subject, levelRequired) && trust.hasPublicPermission(levelRequired);
    }

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
                || item instanceof PlaceOnWaterBlockItem;
    }
}
