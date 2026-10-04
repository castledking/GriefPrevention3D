package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.LecternMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Paper's PlayerTakeLecternBookEvent rule: taking a lectern's book takes container trust. */
@Mixin(LecternMenu.class)
abstract class LecternMenuMixin
{
    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$guardTheBook(Player player, int buttonId, CallbackInfoReturnable<Boolean> callback)
    {
        if (buttonId == LecternMenu.BUTTON_TAKE_BOOK
                && !FabricWorldProtection.mayTakeLecternBook((AbstractContainerMenu) (Object) this, player))
        {
            callback.setReturnValue(false);
        }
    }
}
