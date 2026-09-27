package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper's BlockDispenseEvent rule: nothing is dispensed across a claim border. */
@Mixin(DispenserBlock.class)
abstract class DispenserBlockMixin
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
