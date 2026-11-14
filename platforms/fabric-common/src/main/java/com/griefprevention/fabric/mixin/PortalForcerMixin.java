package com.griefprevention.fabric.mixin;
import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(PortalForcer.class)
abstract class PortalForcerMixin {
    @WrapOperation(method = "createPortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), require = 0)
    private boolean griefPrevention$frame(ServerLevel level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return FabricWorldProtection.mayCreatePortal(level, java.util.List.of(pos), com.griefprevention.fabric.FabricMutationContext.current() == null ? null : com.griefprevention.fabric.FabricMutationContext.current().actor()) && original.call(level, pos, state, flags);
    }
    @WrapOperation(method = "createPortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"), require = 0)
    private boolean griefPrevention$clear(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return FabricWorldProtection.mayCreatePortal(level, java.util.List.of(pos), com.griefprevention.fabric.FabricMutationContext.current() == null ? null : com.griefprevention.fabric.FabricMutationContext.current().actor()) && original.call(level, pos, state);
    }
}
