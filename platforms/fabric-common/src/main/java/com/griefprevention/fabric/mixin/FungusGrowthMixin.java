package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricMutationContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = {"net.minecraft.world.level.block.FungusBlock", "net.minecraft.world.level.block.NetherFungusBlock"})
abstract class FungusGrowthMixin {
    @WrapMethod(method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", require = 0)
    private void griefPrevention$grow(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, Operation<Void> original) {
        FabricMutationContext.during(new FabricMutationContext.Context(pos.immutable(), null, false, false, false, false),
                () -> { original.call(level, random, pos, state); return null; });
    }
    @WrapMethod(method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)V", require = 0)
    private void griefPrevention$growModern(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
            @org.spongepowered.asm.mixin.injection.Coerce Object source, Operation<Void> original) {
        FabricMutationContext.during(new FabricMutationContext.Context(pos.immutable(), null, false, false, false, false),
                () -> { original.call(level, random, pos, state, source); return null; });
    }
}
