package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(PortalShape.class)
abstract class PortalShapeMixin
{
    @Shadow @Final private LevelAccessor level;
    @Shadow @Final private BlockPos bottomLeft;

    @Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true, require = 0)
    private void griefPrevention$protectPortalCreate(CallbackInfo ci)
    {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        // best-effort: block portal creation if any nearby position in the frame area is in a claim
        // the creator can't build in (or, with no known creator, in any claim).
        List<BlockPos> positions = new ArrayList<>();
        BlockPos base = this.bottomLeft;
        for (int dx = -2; dx <= 3; dx++)
            for (int dy = -1; dy <= 4; dy++)
                for (int dz = -2; dz <= 2; dz++)
                    positions.add(base.offset(dx, dy, dz));
        if (!FabricWorldProtection.mayCreatePortal(serverLevel, positions, null))
        {
            ci.cancel();
        }
    }
}
