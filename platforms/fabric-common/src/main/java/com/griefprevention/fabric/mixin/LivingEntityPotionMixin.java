package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricPotionSources;
import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers both base and custom potion effects at their common application point. */
@Mixin(LivingEntity.class)
abstract class LivingEntityPotionMixin
{
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void griefPrevention$protectFromWeaving(
            MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> callback)
    {
        if (effect.getEffect().equals(MobEffects.WEAVING)
                && (source instanceof AreaEffectCloud || FabricPotionSources.isPotionImpact())
                && !FabricWorldProtection.mayApplyWeaving((LivingEntity) (Object) this,
                        ((EntityAccessor) this).griefPrevention$getLevel(), source))
        {
            callback.setReturnValue(false);
        }
    }
}
