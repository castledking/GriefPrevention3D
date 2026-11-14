package com.griefprevention.fabric.mixin;
import com.griefprevention.fabric.FabricWorldProtection;
import com.griefprevention.fabric.FabricMutationContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ServerPlayer.class)
abstract class ServerPlayerProtectionMixin {
    @WrapMethod(method = "die")
    private void griefPrevention$death(DamageSource source, Operation<Void> original) {
        FabricMutationContext.during(new FabricMutationContext.Context(null, (ServerPlayer)(Object)this, false, false, false, true), () -> { original.call(source); return null; });
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "restoreFrom", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void griefPrevention$respawn(ServerPlayer old, boolean alive, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!alive) FabricWorldProtection.markFreshSpawn((ServerPlayer)(Object)this);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "tick", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void griefPrevention$immunity(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        FabricWorldProtection.freshImmune((ServerPlayer)(Object)this);
    }
}
