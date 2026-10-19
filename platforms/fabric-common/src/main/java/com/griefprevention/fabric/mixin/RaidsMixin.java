package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks a raid before it is created or extended when its village claim denies build trust. */
@Mixin(Raids.class)
abstract class RaidsMixin
{
    @Inject(
            method = "createOrExtendRaid",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/raid/Raids;getOrCreateRaid("
                            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/entity/raid/Raid;"),
            cancellable = true)
    private void griefPrevention$requireBuildTrustForRaid(
            ServerPlayer player,
            BlockPos requestedPosition,
            CallbackInfoReturnable<Raid> callback,
            @Local(ordinal = 1) BlockPos raidCenter)
    {
        if (!FabricWorldProtection.mayTriggerRaid(player, raidCenter))
        {
            callback.setReturnValue(null);
        }
    }
}
