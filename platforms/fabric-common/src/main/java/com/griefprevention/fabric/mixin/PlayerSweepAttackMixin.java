package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * A sword's sweep hurts the entities beside its target without going through
 * {@code hurtOrSimulate}, where {@code EntityMixin} applies the claim rules, so a sweep could reach
 * claimed animals, villagers, stands and players in safe zones. Each swept entity gets the same check
 * as a direct hit. Up to 1.21.4 the sweep runs inside {@code attack} through {@code Entity.hurt},
 * knocking the entity back first; 1.21.5 – 1.21.10 call {@code hurtServer} there instead, and from
 * 1.21.11 the sweep is {@code doSweepAttack}, which only knocks back and applies fire aspect after the
 * damage lands. Exactly one sweep damage call exists on every release.
 */
@Mixin(Player.class)
abstract class PlayerSweepAttackMixin
{
    @Group(name = "griefPrevention$sweep", min = 1, max = 1)
    @WrapOperation(
            method = "attack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),
            require = 0)
    private void griefPrevention$protectSweptEntities(
            LivingEntity swept,
            DamageSource source,
            float amount,
            Operation<Void> original)
    {
        if (FabricWorldProtection.mayDamage(swept, ((EntityAccessor) swept).griefPrevention$getLevel(), source))
        {
            original.call(swept, source, amount);
        }
    }

    @Group(name = "griefPrevention$sweep", min = 1, max = 1)
    @WrapOperation(
            method = {"attack", "doSweepAttack"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            require = 0)
    private boolean griefPrevention$protectSweptEntitiesOnTheServer(
            LivingEntity swept,
            ServerLevel level,
            DamageSource source,
            float amount,
            Operation<Boolean> original)
    {
        return FabricWorldProtection.mayDamage(swept, level, source) && original.call(swept, level, source, amount);
    }

    /** Up to 1.21.4 a swept entity is knocked back before it is hurt; the attack's own target passes. */
    @WrapOperation(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"),
            require = 0)
    private void griefPrevention$keepSweptEntitiesInPlace(
            LivingEntity pushed,
            double strength,
            double x,
            double z,
            Operation<Void> original)
    {
        if (FabricWorldProtection.mayAttack(pushed, ((EntityAccessor) pushed).griefPrevention$getLevel(),
                (Player) (Object) this))
        {
            original.call(pushed, strength, x, z);
        }
    }
}
