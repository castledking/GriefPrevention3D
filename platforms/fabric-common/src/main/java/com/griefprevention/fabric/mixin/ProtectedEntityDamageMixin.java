package com.griefprevention.fabric.mixin;
import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({ArmorStand.class, net.minecraft.world.entity.decoration.ItemFrame.class,
        net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, net.minecraft.world.entity.vehicle.VehicleEntity.class})
abstract class ProtectedEntityDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$damage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricWorldProtection.mayDamage((net.minecraft.world.entity.Entity)(Object)this, level, source)) cir.setReturnValue(false);
    }
}
