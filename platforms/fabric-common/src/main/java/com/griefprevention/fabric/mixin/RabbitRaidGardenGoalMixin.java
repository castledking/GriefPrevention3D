package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricMutationContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"net.minecraft.world.entity.animal.Rabbit$RaidGardenGoal", "net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal"})
abstract class RabbitRaidGardenGoalMixin {
    @WrapMethod(method = "tick")
    private void griefPrevention$eat(Operation<Void> original) {
        FabricMutationContext.during(new FabricMutationContext.Context(null, null, false, false, true, false), () -> { original.call(); return null; });
    }
}
