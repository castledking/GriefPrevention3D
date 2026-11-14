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
            at = @At("HEAD"), cancellable = true, require = 0)
    private void griefPrevention$protectPotionEffects(
            MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> callback)
    {
        LivingEntity target = (LivingEntity) (Object) this;
        boolean potionImpact = source instanceof AreaEffectCloud || FabricPotionSources.isPotionImpact();
        if (effect.getEffect().equals(MobEffects.WEAVING) && potionImpact
                && !FabricWorldProtection.mayApplyWeaving(target, ((EntityAccessor) this).griefPrevention$getLevel(), source))
        {
            callback.setReturnValue(false);
            return;
        }
        if (potionImpact
                && !FabricWorldProtection.mayApplyPotionEffect(target, ((EntityAccessor) this).griefPrevention$getLevel(), source, effect))
        {
            callback.setReturnValue(false);
        }
    }
}
