package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricPotionSources;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Preserves the launch origin while a splash applies effects or a lingering potion creates its cloud.
 *
 * <p>1.21.5 split {@code ThrownPotion} into {@code AbstractThrownPotion} and its two kinds. The old
 * name is spelled out because NeoForge, which runs under Mojang's names, serves 1.21.3 through 1.21.10
 * from one adapter remapped to 1.21.10's names; Fabric finds the class through intermediary either way.
 */
@Pseudo
@Mixin(targets = {
        "net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion",
        "net.minecraft.world.entity.projectile.ThrownPotion"
})
abstract class AbstractThrownPotionMixin
{
    @WrapMethod(method = "onHit")
    private void griefPrevention$carryDispenserOrigin(HitResult hit, Operation<Void> original)
    {
        FabricPotionSources.duringImpact(this, () -> original.call(hit));
    }
}
