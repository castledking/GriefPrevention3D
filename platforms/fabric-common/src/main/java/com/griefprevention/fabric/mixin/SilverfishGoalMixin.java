package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricMutationContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal")
abstract class SilverfishGoalMixin {
    @WrapMethod(method = "tick")
    private void griefPrevention$wake(Operation<Void> original) {
        FabricMutationContext.during(new FabricMutationContext.Context(null, null, false, true, false, false), () -> { original.call(); return null; });
    }
}
