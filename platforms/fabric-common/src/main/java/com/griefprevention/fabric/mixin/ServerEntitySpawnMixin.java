package com.griefprevention.fabric.mixin;
import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerLevel.class)
abstract class ServerEntitySpawnMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$spawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricWorldProtection.maySpawn((ServerLevel)(Object)this, entity)) cir.setReturnValue(false);
    }
}
