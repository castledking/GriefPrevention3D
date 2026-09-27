package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trampled farmland: 26.1 renamed {@code FarmBlock} to {@code FarmlandBlock}, and 26.3 replaced
 * the static {@code turnToDirt} with {@code turnToBaseBlock}. Exactly one injection must apply on
 * every release, which the group enforces at startup.
 */
@Pseudo
@Mixin(targets = {
        "net.minecraft.world.level.block.FarmBlock",
        "net.minecraft.world.level.block.FarmlandBlock"
})
abstract class FarmlandTrampleMixin
{
    @Group(name = "griefPrevention$trample", min = 1, max = 1)
    @Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true, require = 0)
    private static void griefPrevention$protectCrops(
            Entity entity,
            BlockState state,
            Level level,
            BlockPos pos,
            CallbackInfo callback)
    {
        if (!FabricWorldProtection.mayTrample(entity, level, pos))
        {
            callback.cancel();
        }
    }

    @Group(name = "griefPrevention$trample", min = 1, max = 1)
    @Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void griefPrevention$protectCropsFromBaseBlock(
            Entity entity,
            BlockState state,
            Level level,
            BlockPos pos,
            CallbackInfo callback)
    {
        if (!FabricWorldProtection.mayTrample(entity, level, pos))
        {
            callback.cancel();
        }
    }
}
