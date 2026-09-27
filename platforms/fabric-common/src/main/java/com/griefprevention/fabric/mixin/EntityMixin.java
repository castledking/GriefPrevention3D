package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Melee attacks and projectile hits both reach an entity through {@code hurtOrSimulate}, so this
 * one hook protects claimed creatures, frames, stands and vehicles from arrows as well as swords.
 */
@Mixin(Entity.class)
abstract class EntityMixin
{
    @Inject(method = "hurtOrSimulate", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$protectClaimedEntities(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> callback)
    {
        if (!FabricWorldProtection.mayDamage((Entity) (Object) this, source))
        {
            callback.setReturnValue(false);
        }
    }
}
