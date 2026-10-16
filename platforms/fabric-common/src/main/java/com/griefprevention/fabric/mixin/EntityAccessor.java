package com.griefprevention.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An entity's level: {@code Entity.level()} moved to an interface in 1.21.9, so no one call reaches
 * it on every release the 1.21.11 adapter serves, but the field stayed put.
 */
@Mixin(Entity.class)
public interface EntityAccessor
{
    @Accessor("level")
    Level griefPrevention$getLevel();
}
