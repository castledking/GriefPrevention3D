package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lava's random tick only ignites nearby blocks; Paper refuses that where fire may not spread. */
@Mixin(LavaFluid.class)
abstract class LavaFluidMixin
{
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$keepLavaFromIgniting(
            ServerLevel level,
            BlockPos pos,
            FluidState state,
            RandomSource random,
            CallbackInfo callback)
    {
        if (!FabricWorldProtection.mayLavaIgnite(level))
        {
            callback.cancel();
        }
    }
}
