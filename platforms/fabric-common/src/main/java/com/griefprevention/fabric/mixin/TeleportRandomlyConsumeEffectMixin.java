package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * Chorus fruit, under the same {@code EnderPearlsRequireAccessTrust} rule as ender pearls: a jump
 * that lands in a claim the eater has no access trust in is undone, and vanilla tries another spot.
 * 26.3 teleports through an overload that also takes the blocks to avoid; exactly one of the two
 * calls exists on every release, which the group enforces at startup.
 */
@Mixin(TeleportRandomlyConsumeEffect.class)
abstract class TeleportRandomlyConsumeEffectMixin
{
    @Group(name = "griefPrevention$chorus", min = 1, max = 1)
    @WrapOperation(
            method = "apply",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"),
            require = 0)
    private boolean griefPrevention$keepChorusOutOfClaims(
            LivingEntity entity,
            double x,
            double y,
            double z,
            boolean particles,
            Operation<Boolean> original)
    {
        double fromX = entity.getX();
        double fromY = entity.getY();
        double fromZ = entity.getZ();
        if (!original.call(entity, x, y, z, particles))
        {
            return false;
        }
        return FabricWorldProtection.mayChorusLand(entity, fromX, fromY, fromZ);
    }

    @Group(name = "griefPrevention$chorus", min = 1, max = 1)
    @WrapOperation(
            method = "apply",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZLnet/minecraft/tags/TagKey;)Z",
                    remap = false),
            require = 0)
    private boolean griefPrevention$keepChorusOutOfClaimsAvoiding(
            LivingEntity entity,
            double x,
            double y,
            double z,
            boolean particles,
            TagKey<?> avoid,
            Operation<Boolean> original)
    {
        double fromX = entity.getX();
        double fromY = entity.getY();
        double fromZ = entity.getZ();
        if (!original.call(entity, x, y, z, particles, avoid))
        {
            return false;
        }
        return FabricWorldProtection.mayChorusLand(entity, fromX, fromY, fromZ);
    }
}
