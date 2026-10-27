package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricWorldProtection;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A projectile's impact position must never substitute for the dispenser that launched it. */
@Mixin(ProjectileDispenseBehavior.class)
abstract class ProjectileDispenseBehaviorMixin
{
    @ModifyExpressionValue(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ProjectileItem;asProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile griefPrevention$rememberDispenser(Projectile projectile, @Local(argsOnly = true) BlockSource source)
    {
        FabricWorldProtection.rememberDispensedProjectile(projectile, source.level(), source.pos());
        return projectile;
    }
}
