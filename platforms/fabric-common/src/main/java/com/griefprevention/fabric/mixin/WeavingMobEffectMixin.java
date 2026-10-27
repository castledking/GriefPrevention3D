package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/** Carries the dying entity into vanilla's separate per-web placement method. */
@Mixin(targets = "net.minecraft.world.effect.WeavingMobEffect")
abstract class WeavingMobEffectMixin
{
    @Unique
    private final ThreadLocal<LivingEntity> griefPrevention$weavingSource = new ThreadLocal<>();

    @WrapMethod(method = "onMobRemoved")
    private void griefPrevention$rememberDyingEntity(
            ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason, Operation<Void> original)
    {
        LivingEntity previous = griefPrevention$weavingSource.get();
        griefPrevention$weavingSource.set(entity);
        try
        {
            original.call(level, entity, amplifier, reason);
        }
        finally
        {
            if (previous == null) griefPrevention$weavingSource.remove();
            else griefPrevention$weavingSource.set(previous);
        }
    }

    @Group(name = "griefPrevention$weavingPlacement", min = 1, max = 1)
    @WrapOperation(method = "spawnCobwebsRandomlyAround", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), require = 0)
    private boolean griefPrevention$protectEachWeb(
            ServerLevel level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original)
    {
        return FabricWorldProtection.mayPlaceWeavingWeb(level, pos, griefPrevention$weavingSource.get())
                && original.call(level, pos, state, flags);
    }

    // 26.3 switched the same placement to setBlockAndUpdate.
    @Group(name = "griefPrevention$weavingPlacement", min = 1, max = 1)
    @WrapOperation(method = "spawnCobwebsRandomlyAround", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"), require = 0)
    private boolean griefPrevention$protectEachWebAndUpdate(
            ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original)
    {
        return FabricWorldProtection.mayPlaceWeavingWeb(level, pos, griefPrevention$weavingSource.get())
                && original.call(level, pos, state);
    }
}
