package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers which lectern a lectern menu reads from, so taking its book can be checked there. */
@Mixin(LecternBlockEntity.class)
abstract class LecternBlockEntityMixin
{
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void griefPrevention$rememberLectern(
            int containerId,
            Inventory inventory,
            Player player,
            CallbackInfoReturnable<AbstractContainerMenu> callback)
    {
        FabricWorldProtection.lecternMenuOpened(callback.getReturnValue(), (LecternBlockEntity) (Object) this);
    }
}
