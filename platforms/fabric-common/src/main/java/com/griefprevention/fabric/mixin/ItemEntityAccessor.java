package com.griefprevention.fabric.mixin;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Accessor("target") UUID griefPrevention$getTarget();
}
