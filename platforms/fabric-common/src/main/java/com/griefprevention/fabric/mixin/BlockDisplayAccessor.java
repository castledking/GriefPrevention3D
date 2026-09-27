package com.griefprevention.fabric.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor
{
    @Invoker("setBlockState")
    void griefPrevention$setBlockState(BlockState state);
}
