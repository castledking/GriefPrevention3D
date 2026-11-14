package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal")
abstract class SilverfishMergeMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$merge(CallbackInfo ci) {
        if (!FabricWorldProtection.maySilverfishBreakBlocks()) ci.cancel();
    }
}
