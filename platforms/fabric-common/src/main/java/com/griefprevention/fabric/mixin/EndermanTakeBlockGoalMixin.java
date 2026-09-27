package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code EndermenMoveBlocks}: endermen never pick blocks up unless it is enabled. The goal is
 * package-private, and 26.3 renamed {@code EnderMan} to {@code Enderman}, so both names are listed;
 * whichever the running release lacks is skipped.
 */
@Pseudo
@Mixin(targets = {
        "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal",
        "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal"
})
abstract class EndermanTakeBlockGoalMixin
{
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$keepEndermenFromTakingBlocks(CallbackInfoReturnable<Boolean> callback)
    {
        if (!FabricWorldProtection.mayEndermenMoveBlocks())
        {
            callback.setReturnValue(false);
        }
    }
}
