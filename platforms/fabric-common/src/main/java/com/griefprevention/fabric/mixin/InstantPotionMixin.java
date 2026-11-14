package com.griefprevention.fabric.mixin;
import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
abstract class InstantPotionMixin {
    @Shadow @Final private boolean isHarm;
    @org.spongepowered.asm.mixin.injection.Group(name = "instantPotion", min = 1)
    @Inject(method = {"applyInstantenousEffect", "applyInstantaneousEffect"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void griefPrevention$instant(ServerLevel level, Entity direct, Entity indirect, LivingEntity target, int amplifier, double intensity, CallbackInfo ci) {
        if (isHarm != target.isInvertedHealAndHarm()
                && !FabricWorldProtection.mayApplyPotionEffect(target, level, direct == null ? indirect : direct,
                    new MobEffectInstance(MobEffects.INSTANT_DAMAGE))) ci.cancel();
    }
}
