package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper's PlayerFishEvent rule: reeling a claimed animal or armor stand in takes container trust. */
@Mixin(FishingHook.class)
abstract class FishingHookMixin
{
    @Inject(method = "pullEntity", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$keepClaimedEntitiesHooked(Entity hooked, CallbackInfo callback)
    {
        if (!FabricWorldProtection.mayReelIn(((FishingHook) (Object) this).getPlayerOwner(), hooked))
        {
            callback.cancel();
        }
    }
}
