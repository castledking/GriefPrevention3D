package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.persistence.ClaimDocument;
import com.griefprevention.protection.ExplosionBlockPolicy;
import com.griefprevention.protection.ExplosionProtectionConfigCodec;
import com.griefprevention.protection.ExplosionProtectionConfigException;
import com.griefprevention.protection.ExplosionProtectionSettings;
import com.griefprevention.protection.ExplosionSourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Bridges Minecraft explosions to the platform-neutral upstream policy. */
@ApiStatus.Internal
public final class FabricExplosionProtection
{
    private static volatile @Nullable FabricExplosionProtection active;

    private final @NotNull FabricClaimRepository claims;
    private final @NotNull ExplosionProtectionSettings settings;

    private FabricExplosionProtection(
            @NotNull FabricClaimRepository claims,
            @NotNull ExplosionProtectionSettings settings)
    {
        this.claims = claims;
        this.settings = settings;
    }

    static void install(@NotNull Path configFile, @NotNull FabricClaimRepository claims)
    {
        try
        {
            String input = Files.readString(configFile, StandardCharsets.UTF_8);
            ExplosionProtectionSettings settings = new ExplosionProtectionConfigCodec().decode(input);
            active = new FabricExplosionProtection(claims, settings);
        }
        catch (IOException | ExplosionProtectionConfigException exception)
        {
            throw new IllegalStateException(
                    "Could not safely activate Fabric explosion protection from " + configFile + ".",
                    exception
            );
        }
    }

    public static @NotNull List<BlockPos> filterAffectedBlocks(
            @NotNull ServerLevel level,
            @Nullable Entity source,
            @NotNull Explosion.BlockInteraction interaction,
            @NotNull List<BlockPos> affectedBlocks)
    {
        FabricExplosionProtection protection = active;
        if (protection == null)
        {
            // The mixin may become active before the mod initializer. No claimed block damage is
            // safer than briefly allowing an unprotected explosion during startup.
            return new ArrayList<>();
        }
        return protection.filter(level, source, interaction, affectedBlocks);
    }

    private @NotNull List<BlockPos> filter(
            @NotNull ServerLevel level,
            @Nullable Entity source,
            @NotNull Explosion.BlockInteraction interaction,
            @NotNull List<BlockPos> affectedBlocks)
    {
        String worldKey = this.claims.worldKey(level);
        if (this.settings.worldMode(worldKey) == ExplosionProtectionSettings.ClaimWorldMode.DISABLED)
        {
            return affectedBlocks;
        }

        if (interaction == Explosion.BlockInteraction.KEEP)
        {
            return affectedBlocks;
        }

        if (interaction == Explosion.BlockInteraction.TRIGGER_BLOCK)
        {
            return filterTriggeredBlocks(level, source, affectedBlocks);
        }

        ExplosionSourceType sourceType = sourceType(source);
        boolean normalEnvironment = Level.OVERWORLD.equals(level.dimension());
        List<BlockPos> allowed = new ArrayList<>(affectedBlocks.size());
        for (BlockPos block : affectedBlocks)
        {
            if (level.getBlockState(block).isAir())
            {
                continue;
            }

            ClaimSnapshot snapshot = this.claims.findClaimAt(level, block);
            ClaimDocument document = null;
            if (snapshot != null && snapshot.id() != null)
            {
                document = this.claims.documentFor(snapshot.id());
                if (document == null)
                {
                    continue;
                }
            }

            if (ExplosionBlockPolicy.mayDamageBlock(
                    this.settings,
                    worldKey,
                    sourceType,
                    normalEnvironment,
                    level.getSeaLevel(),
                    block.getY(),
                    document
            ))
            {
                allowed.add(block);
            }
        }
        return allowed;
    }

    private @NotNull List<BlockPos> filterTriggeredBlocks(
            @NotNull ServerLevel level,
            @Nullable Entity source,
            @NotNull List<BlockPos> affectedBlocks)
    {
        Entity actor = source;
        if (source instanceof Projectile)
        {
            Entity owner = ((Projectile) source).getOwner();
            if (owner != null)
            {
                actor = owner;
            }
        }

        List<BlockPos> allowed = new ArrayList<>(affectedBlocks.size());
        for (BlockPos block : affectedBlocks)
        {
            if (level.getBlockState(block).isAir())
            {
                continue;
            }

            ClaimSnapshot claim = this.claims.findClaimAt(level, block);
            if (claim == null)
            {
                allowed.add(block);
                continue;
            }

            if (actor instanceof Player && mayAccess((Player) actor, claim))
            {
                allowed.add(block);
                continue;
            }

            // Match upstream's dispenser exception as closely as Minecraft exposes it: an
            // ownerless projectile originating inside the same claim may trigger its blocks.
            if (source instanceof Projectile && ((Projectile) source).getOwner() == null)
            {
                ClaimSnapshot sourceClaim = this.claims.findClaimAt(level, source.blockPosition());
                if (sourceClaim != null && java.util.Objects.equals(sourceClaim.id(), claim.id()))
                {
                    allowed.add(block);
                }
            }
        }
        return allowed;
    }

    private boolean mayAccess(@NotNull Player player, @NotNull ClaimSnapshot claim)
    {
        return this.claims.allows(claim, player.getUUID(), ClaimTrustLevel.ACCESS);
    }

    private static @NotNull ExplosionSourceType sourceType(@Nullable Entity source)
    {
        if (source instanceof Creeper)
        {
            return ExplosionSourceType.CREEPER;
        }
        if (source instanceof WitherBoss || source instanceof WitherSkull)
        {
            return ExplosionSourceType.WITHER;
        }
        return ExplosionSourceType.OTHER;
    }
}
