package com.griefprevention.fabric.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The held hotbar slot: a public field until 1.21.5, behind {@code getSelectedSlot()} from then on. */
@Mixin(Inventory.class)
public interface InventoryAccessor
{
    @Accessor("selected")
    int griefPrevention$getSelected();
}
