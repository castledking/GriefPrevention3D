package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
abstract class HopperBlockEntityMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void griefPrevention$pickup(Container hopper, ItemEntity item, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricWorldProtection.mayHopperPickup(hopper, item)) cir.setReturnValue(false);
    }
    @Inject(method = "tryMoveInItem", at = @At("HEAD"), cancellable = true)
    private static void griefPrevention$transfer(Container source, Container destination, ItemStack stack, int slot, Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        if (!FabricWorldProtection.mayTransferItems(source, destination)) cir.setReturnValue(stack);
    }
}
