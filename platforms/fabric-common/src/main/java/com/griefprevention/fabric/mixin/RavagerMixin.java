package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Ravager")
abstract class RavagerMixin
{
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"), require = 0)
    private boolean griefPrevention$ravagerDestroy(ServerLevel level, BlockPos pos, boolean drop, net.minecraft.world.entity.Entity breaker, Operation<Boolean> original)
    {
        if (!FabricWorldProtection.mayRavagersBreakBlocks() || !FabricWorldProtection.mayMobChangeClaimedBlock(level, pos))
        {
            return false;
        }
        return original.call(level, pos, drop, breaker);
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"), require = 0)
    private boolean griefPrevention$ravagerDestroyLevel(Level level, BlockPos pos, boolean drop, net.minecraft.world.entity.Entity breaker, Operation<Boolean> original)
    {
        if (!FabricWorldProtection.mayRavagersBreakBlocks() || !FabricWorldProtection.mayMobChangeClaimedBlock(level, pos))
        {
            return false;
        }
        return original.call(level, pos, drop, breaker);
    }
}
