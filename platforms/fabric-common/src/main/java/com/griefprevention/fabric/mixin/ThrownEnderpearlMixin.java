package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paper's {@code EnderPearlsRequireAccessTrust}: a pearl that would carry its thrower into a claim
 * they have no access trust in breaks without teleporting them or hatching an endermite.
 */
@Mixin(ThrownEnderpearl.class)
abstract class ThrownEnderpearlMixin
{
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$keepPearlsOutOfClaims(HitResult hit, CallbackInfo callback)
    {
        Projectile pearl = (Projectile) (Object) this;
        if (!FabricWorldProtection.mayPearlLand(pearl, hit))
        {
            pearl.discard();
            callback.cancel();
        }
    }
}
