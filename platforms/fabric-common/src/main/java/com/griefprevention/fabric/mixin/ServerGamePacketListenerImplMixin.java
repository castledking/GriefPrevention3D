package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricScrollResize;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hotbar and key packets {@code /scrollresize} listens to, which Fabric has no events for: a
 * scroll of the hotbar moves a side of the claim, and dropping or swapping hands cancels.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin
{
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$scrollResize(ServerboundSetCarriedItemPacket packet, CallbackInfo callback)
    {
        if (FabricScrollResize.onHotbarSlot(this.player, packet.getSlot()))
        {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$cancelScrollResize(ServerboundPlayerActionPacket packet, CallbackInfo callback)
    {
        if (FabricScrollResize.onPlayerAction(this.player, packet.getAction()))
        {
            callback.cancel();
        }
    }
}
