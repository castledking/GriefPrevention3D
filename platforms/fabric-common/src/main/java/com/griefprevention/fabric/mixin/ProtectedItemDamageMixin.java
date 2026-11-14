package com.griefprevention.fabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reserved death drops survive fire, explosions and cactus until their normal despawn. */
@Mixin(ItemEntity.class)
abstract class ProtectedItemDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$protectDrops(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (((ItemEntityAccessor)this).griefPrevention$getTarget() != null) cir.setReturnValue(false);
    }
}
