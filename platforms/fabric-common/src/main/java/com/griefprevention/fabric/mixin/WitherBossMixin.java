package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.boss.wither.WitherBoss")
abstract class WitherBossMixin
{
    @WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"), require = 0)
    private boolean griefPrevention$witherDestroy(ServerLevel level, BlockPos pos, boolean drop, Entity breaker, Operation<Boolean> original)
    {
        if (!FabricWorldProtection.mayWitherGrief(level, pos)) return false;
        return original.call(level, pos, drop, breaker);
    }

    @WrapOperation(method = "performRangedAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"), require = 0)
    private net.minecraft.world.level.Explosion griefPrevention$witherSkull(Level level, Entity source, double x, double y, double z, float power, boolean fire, Level.ExplosionInteraction interaction, Operation<net.minecraft.world.level.Explosion> original)
    {
        return original.call(level, source, x, y, z, power, fire, interaction);
    }
}
