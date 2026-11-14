package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricMutationContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
abstract class ItemStackUseMixin {
    @WrapMethod(method = "useOn")
    private InteractionResult griefPrevention$actor(UseOnContext context, Operation<InteractionResult> original) {
        return FabricMutationContext.during(new FabricMutationContext.Context(null, context.getPlayer(), false, false, false, false),
                () -> original.call(context));
    }
}
