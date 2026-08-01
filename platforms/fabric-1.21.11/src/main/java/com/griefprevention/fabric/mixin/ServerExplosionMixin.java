package com.griefprevention.fabric.mixin;

import com.griefprevention.fabric.FabricExplosionProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin
{
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private @Nullable Entity source;
    @Shadow @Final private Explosion.BlockInteraction blockInteraction;

    @ModifyVariable(method = "explode", at = @At(value = "STORE"), ordinal = 0)
    private List<BlockPos> griefPrevention$filterAffectedBlocks(List<BlockPos> affectedBlocks)
    {
        return FabricExplosionProtection.filterAffectedBlocks(
                this.level,
                this.source,
                this.blockInteraction,
                affectedBlocks
        );
    }
}
