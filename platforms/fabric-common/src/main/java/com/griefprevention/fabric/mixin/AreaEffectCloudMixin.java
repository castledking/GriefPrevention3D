package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricPotionSources;
import net.minecraft.world.entity.AreaEffectCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lingering clouds retain their dispenser's origin after the projectile disappears. */
@Mixin(AreaEffectCloud.class)
abstract class AreaEffectCloudMixin
{
    @Inject(method = "<init>", at = @At("RETURN"))
    private void griefPrevention$inheritDispenserOrigin(CallbackInfo callback)
    {
        FabricPotionSources.inheritImpactOrigin(this);
    }
}
