package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * Paper's falling-block rule, which stops sand cannons: a block that lands somewhere other than
 * straight below where it fell from may only settle in a claim that holds its origin. Refusing the
 * placement makes vanilla drop it as an item instead. 26.3 places the block with
 * {@code setBlockAndUpdate}; exactly one of the two calls exists on every release.
 */
@Mixin(FallingBlockEntity.class)
abstract class FallingBlockEntityMixin
{
    @Group(name = "griefPrevention$landing", min = 1, max = 1)
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            require = 0)
    private boolean griefPrevention$keepCannonsOutOfClaims(
            Level level,
            BlockPos pos,
            BlockState state,
            int flags,
            Operation<Boolean> original)
    {
        return FabricWorldProtection.mayFallingBlockLand(level, ((FallingBlockEntity) (Object) this).getStartPos(), pos)
                && original.call(level, pos, state, flags);
    }

    @Group(name = "griefPrevention$landing", min = 1, max = 1)
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            require = 0)
    private boolean griefPrevention$keepCannonsOutOfClaimsAndUpdate(
            Level level,
            BlockPos pos,
            BlockState state,
            Operation<Boolean> original)
    {
        return FabricWorldProtection.mayFallingBlockLand(level, ((FallingBlockEntity) (Object) this).getStartPos(), pos)
                && original.call(level, pos, state);
    }
}
