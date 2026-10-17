package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cushions (26.3+) are entities that extend BlockAttachedEntity, which overrides hurtServer.
 * Snowballs and eggs call Entity.hurt() directly, bypassing hurtOrSimulate, so they reach
 * BlockAttachedEntity.hurtServer without being checked by the EntityMixin.
 * This mixin intercepts BlockAttachedEntity.hurtServer to protect cushions from projectiles.
 */
@Mixin(BlockAttachedEntity.class)
abstract class BlockAttachedEntityMixin
{
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$protectClaimedCushions(
            ServerLevel level,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> callback)
    {
        if (!FabricWorldProtection.mayDamage(
                (BlockAttachedEntity) (Object) this,
                level,
                source))
        {
            callback.setReturnValue(false);
        }
    }
}