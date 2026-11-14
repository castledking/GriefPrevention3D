package com.griefprevention.fabric.mixin;
import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Mob.class)
abstract class MobSpawnReasonMixin {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void griefPrevention$reason(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
        FabricWorldProtection.rememberSpawnReason((Mob)(Object)this, reason.name());
    }
}
