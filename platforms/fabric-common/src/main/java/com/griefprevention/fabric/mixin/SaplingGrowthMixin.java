package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricMutationContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SaplingBlock.class)
abstract class SaplingGrowthMixin {
    @WrapMethod(method = "advanceTree")
    private void griefPrevention$grow(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, Operation<Void> original) {
        FabricMutationContext.during(new FabricMutationContext.Context(pos.immutable(), null, false, false, false, false),
                () -> { original.call(level, pos, state, random); return null; });
    }
}
