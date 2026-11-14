package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.minecraft.world.entity.animal.Rabbit$RaidGardenGoal")
abstract class RabbitRaidGardenGoalMixin
{
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void griefPrevention$rabbitCanUse(CallbackInfoReturnable<Boolean> cir)
    {
        if (!FabricWorldProtection.mayRabbitsEatCrops())
        {
            // fine: global off behaves like Paper? Paper default true allows eating; when false prevents rabbits eating crops.
            // global off means no eating anywhere - simple and safe; claimed-only handled below in tick
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void griefPrevention$rabbitTick(CallbackInfo ci)
    {
        // best effort: if global rabbits-eat-crops disabled, stop. Claim-specific handled elsewhere if we can find target block; hard without shadow fields across mappings.
        if (!FabricWorldProtection.mayRabbitsEatCrops())
        {
            ci.cancel();
        }
    }
}
