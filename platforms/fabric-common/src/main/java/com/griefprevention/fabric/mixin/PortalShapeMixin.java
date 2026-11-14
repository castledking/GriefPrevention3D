package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricMutationContext;
import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.Predicate;

@Mixin(PortalShape.class)
abstract class PortalShapeMixin {
    @Shadow @Final private BlockPos bottomLeft;
    @Shadow @Final private Direction rightDir;
    @Shadow @Final private int width;
    @Shadow @Final private int height;

    @Inject(method = "findPortalShape", at = @At("RETURN"), cancellable = true)
    private static void griefPrevention$portal(LevelAccessor level, BlockPos pos, Predicate<PortalShape> predicate,
            Direction.Axis axis, CallbackInfoReturnable<Optional<PortalShape>> cir) {
        if (!(level instanceof ServerLevel server) || cir.getReturnValue().isEmpty()) return;
        PortalShapeMixin shape = (PortalShapeMixin)(Object)cir.getReturnValue().get();
        var blocks = new ArrayList<BlockPos>();
        for (int x = 0; x < shape.width; x++)
            for (int y = 0; y < shape.height; y++) blocks.add(shape.bottomLeft.relative(shape.rightDir, x).above(y));
        var context = FabricMutationContext.current();
        if (!FabricWorldProtection.mayCreatePortal(server, blocks, context == null ? null : context.actor()))
            cir.setReturnValue(Optional.empty());
    }
}
