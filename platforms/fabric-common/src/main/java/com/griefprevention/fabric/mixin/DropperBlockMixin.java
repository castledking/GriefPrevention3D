package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Droppers override dispensing, so they need the same rule as dispensers. */
@Mixin(DropperBlock.class)
abstract class DropperBlockMixin
{
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$keepDispensingInsideClaims(
            ServerLevel level,
            BlockState state,
            BlockPos pos,
            CallbackInfo callback)
    {
        if (!FabricWorldProtection.mayDispense(level, state, pos))
        {
            callback.cancel();
        }
    }
}
