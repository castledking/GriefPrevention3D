package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper's BlockFromToEvent rule: water and lava do not flow into a claim from outside it. */
@Mixin(FlowingFluid.class)
abstract class FlowingFluidMixin
{
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$keepFluidsOutOfClaims(
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            Direction direction,
            FluidState fluidState,
            CallbackInfo callback)
    {
        if (!FabricWorldProtection.mayFlow(level, pos, direction))
        {
            callback.cancel();
        }
    }
}
