package com.griefprevention.fabric.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches the display setters that summon commands use, for client-only boundary displays. */
@Mixin(Display.class)
public interface DisplayAccessor
{
    @Invoker("setTransformation")
    void griefPrevention$setTransformation(Transformation transformation);

    @Invoker("setBrightnessOverride")
    void griefPrevention$setBrightnessOverride(Brightness brightness);

    @Invoker("setViewRange")
    void griefPrevention$setViewRange(float range);

    @Invoker("setShadowRadius")
    void griefPrevention$setShadowRadius(float radius);

    @Invoker("setShadowStrength")
    void griefPrevention$setShadowStrength(float strength);

    @Invoker("setGlowColorOverride")
    void griefPrevention$setGlowColorOverride(int color);
}
