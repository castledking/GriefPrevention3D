package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper cancels the fire a lightning strike starts in a claim, whoever called the lightning down. */
@Mixin(LightningBolt.class)
abstract class LightningBoltMixin
{
    @Inject(method = "spawnFire", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$noLightningFireInClaims(int extraFires, CallbackInfo callback)
    {
        Entity bolt = (Entity) (Object) this;
        if (!FabricWorldProtection.mayLightningStartFire(((EntityAccessor) bolt).griefPrevention$getLevel(), bolt))
        {
            callback.cancel();
        }
    }
}
