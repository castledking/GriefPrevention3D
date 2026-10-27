package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricPotionSources;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/** Preserves the launch origin while a splash applies effects or a lingering potion creates its cloud. */
@Mixin(AbstractThrownPotion.class)
abstract class AbstractThrownPotionMixin
{
    @WrapMethod(method = "onHit")
    private void griefPrevention$carryDispenserOrigin(HitResult hit, Operation<Void> original)
    {
        FabricPotionSources.duringImpact(this, () -> original.call(hit));
    }
}
