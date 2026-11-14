package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
abstract class HopperBlockEntityMixin
{
    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true, require = 0)
    private static void griefPrevention$hopperPickup(Level level, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> cir)
    {
        if (!FabricWorldProtection.mayHopperInteract(level, BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ()), null))
        {
            cir.setReturnValue(false);
        }
    }
}
