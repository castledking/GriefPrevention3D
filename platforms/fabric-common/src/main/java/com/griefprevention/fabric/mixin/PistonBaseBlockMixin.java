package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Paper's piston rule: once vanilla has worked out which blocks a push or pull moves, the move is
 * refused if it would carry blocks across a claim border. A refused piston simply does not fire.
 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin
{
    @WrapOperation(
            method = "moveBlocks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
    private boolean griefPrevention$keepPistonsInsideClaims(
            PistonStructureResolver resolver,
            Operation<Boolean> original,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) Direction facing,
            @Local(argsOnly = true) boolean extending)
    {
        return original.call(resolver)
                && FabricWorldProtection.mayMovePiston(
                        level,
                        pos,
                        facing,
                        extending,
                        resolver.getPushDirection(),
                        resolver.getToPush(),
                        resolver.getToDestroy());
    }
}
