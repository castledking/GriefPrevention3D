package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricScrollResize;
import com.griefprevention.fabric.FabricBlockPlaceClimbProtection;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
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

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void griefPrevention$denyRejectedBlockSteps(ServerboundMovePlayerPacket packet, CallbackInfo callback)
    {
        if (FabricBlockPlaceClimbProtection.beforeMove(player,
                packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()),
                packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot())))
        {
            callback.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void griefPrevention$rememberSupportedPosition(ServerboundMovePlayerPacket packet, CallbackInfo callback)
    {
        FabricBlockPlaceClimbProtection.moved(player);
    }

    @Inject(method = "teleport", at = @At("HEAD"))
    private void griefPrevention$clearClimbingCheckpoint(CallbackInfo callback)
    {
        FabricBlockPlaceClimbProtection.clear(player);
    }

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
