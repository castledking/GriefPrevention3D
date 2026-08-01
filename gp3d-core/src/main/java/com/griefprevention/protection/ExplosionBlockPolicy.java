package com.griefprevention.protection;

import com.griefprevention.persistence.ClaimDocument;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Pure decision function matching GriefPrevention's destructive explosion filtering. */
@ApiStatus.Internal
public final class ExplosionBlockPolicy
{
    private ExplosionBlockPolicy()
    {
    }

    public static boolean mayDamageBlock(
            @NotNull ExplosionProtectionSettings settings,
            @NotNull String worldKey,
            @NotNull ExplosionSourceType sourceType,
            boolean normalEnvironment,
            int seaLevel,
            int blockY,
            @Nullable ClaimDocument claim)
    {
        ExplosionProtectionSettings.ClaimWorldMode worldMode = settings.worldMode(worldKey);
        if (worldMode == ExplosionProtectionSettings.ClaimWorldMode.DISABLED)
        {
            return true;
        }
        if (worldMode == ExplosionProtectionSettings.ClaimWorldMode.CREATIVE)
        {
            return false;
        }

        if (claim != null)
        {
            if (!settings.blockLandClaimExplosions())
            {
                return true;
            }
            return sourceType == ExplosionSourceType.WITHER
                    ? claim.witherExplosionsAllowed()
                    : claim.explosivesAllowed();
        }

        boolean applySurfaceRules = normalEnvironment
                && (sourceType == ExplosionSourceType.CREEPER
                ? settings.blockSurfaceCreeperExplosions()
                : settings.blockSurfaceOtherExplosions());
        return !applySurfaceRules || blockY < seaLevel - 7;
    }
}
