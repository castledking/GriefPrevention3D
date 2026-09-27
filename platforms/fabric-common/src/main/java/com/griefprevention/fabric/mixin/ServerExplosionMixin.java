package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricExplosionProtection;
import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin
{
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private @Nullable Entity source;
    @Shadow @Final private Explosion.BlockInteraction blockInteraction;

    /** Explosions never hurt what a claim protects: its creatures, frames, stands and vehicles. */
    @WrapOperation(
            method = "hurtEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean griefPrevention$protectClaimedEntities(
            Entity entity,
            ServerLevel hurtLevel,
            DamageSource damageSource,
            float amount,
            Operation<Boolean> original)
    {
        return FabricWorldProtection.mayExplosionDamage(hurtLevel, entity)
                && original.call(entity, hurtLevel, damageSource, amount);
    }

    @ModifyVariable(method = "explode", at = @At(value = "STORE"), ordinal = 0)
    private List<BlockPos> griefPrevention$filterAffectedBlocks(List<BlockPos> affectedBlocks)
    {
        return FabricExplosionProtection.filterAffectedBlocks(
                this.level,
                this.source,
                this.blockInteraction,
                affectedBlocks
        );
    }
}
