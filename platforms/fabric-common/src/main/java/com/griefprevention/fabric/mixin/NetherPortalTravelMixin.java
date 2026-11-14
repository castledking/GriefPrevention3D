package com.griefprevention.fabric.mixin;
import com.griefprevention.fabric.FabricMutationContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(NetherPortalBlock.class)
abstract class NetherPortalTravelMixin {
    @WrapMethod(method = "getPortalDestination")
    private TeleportTransition griefPrevention$traveler(ServerLevel level, Entity entity, BlockPos pos, Operation<TeleportTransition> original) {
        return FabricMutationContext.during(new FabricMutationContext.Context(null, entity, true, false, false, false),
                () -> original.call(level, entity, pos));
    }
}
