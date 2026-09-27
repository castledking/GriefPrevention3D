package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.griefprevention.fabric.FabricWorldProtection.FireVerdict;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paper's fire rules. A fire tick first tries to burn its neighbors away, then to ignite blocks
 * around it; either is refused where the rules forbid it, and a refused fire is put out afterwards
 * so it cannot burn forever beside blocks it may not consume.
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin
{
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/FireBlock;checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V"))
    private void griefPrevention$protectBurningBlocks(
            FireBlock fire,
            Level level,
            BlockPos burning,
            int chance,
            RandomSource random,
            int age,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos firePos,
            @Share("extinguish") LocalBooleanRef extinguish)
    {
        FireVerdict verdict = FabricWorldProtection.mayBurn(level, burning, firePos);
        if (verdict == FireVerdict.ALLOW)
        {
            original.call(fire, level, burning, chance, random, age);
        }
        else if (verdict == FireVerdict.DENY_AND_EXTINGUISH)
        {
            extinguish.set(true);
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/FireBlock;getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I"))
    private int griefPrevention$protectIgnitedBlocks(
            FireBlock fire,
            LevelReader reader,
            BlockPos target,
            Operation<Integer> original,
            @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos firePos,
            @Share("extinguish") LocalBooleanRef extinguish)
    {
        int odds = original.call(fire, reader, target);
        // Most of the area around a fire cannot ignite at all; only look up claims where it could.
        if (odds <= 0)
        {
            return odds;
        }
        FireVerdict verdict = FabricWorldProtection.maySpreadFire(level, firePos, target);
        if (verdict == FireVerdict.ALLOW)
        {
            return odds;
        }
        if (verdict == FireVerdict.DENY_AND_EXTINGUISH)
        {
            extinguish.set(true);
        }
        return 0;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void griefPrevention$extinguishRefusedFire(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo callback,
            @Share("extinguish") LocalBooleanRef extinguish)
    {
        if (extinguish.get())
        {
            FabricWorldProtection.extinguish(level, pos);
        }
    }
}
