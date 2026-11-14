package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelMutationMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$scopedChange(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricWorldProtection.mayScopedChange((Level)(Object)this, pos)
                || !FabricWorldProtection.mayPlaceCreativeActive((Level)(Object)this, pos, state)) cir.setReturnValue(false);
    }
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$mobDestroy(BlockPos pos, boolean drops, Entity actor, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricWorldProtection.mayMobDestroy((Level)(Object)this, pos, actor)) cir.setReturnValue(false);
    }
}
