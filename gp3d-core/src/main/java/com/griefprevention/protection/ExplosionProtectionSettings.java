package com.griefprevention.protection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Platform-neutral subset of config.yml that controls explosion block damage. */
@ApiStatus.Internal
public final class ExplosionProtectionSettings
{
    public enum ClaimWorldMode
    {
        SURVIVAL,
        CREATIVE,
        DISABLED;

        static @NotNull ClaimWorldMode parse(@NotNull String value)
        {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private final boolean blockLandClaimExplosions;
    private final boolean blockSurfaceCreeperExplosions;
    private final boolean blockSurfaceOtherExplosions;
    private final @NotNull Map<String, ClaimWorldMode> worldModes;

    public ExplosionProtectionSettings(
            boolean blockLandClaimExplosions,
            boolean blockSurfaceCreeperExplosions,
            boolean blockSurfaceOtherExplosions,
            @NotNull Map<String, ClaimWorldMode> worldModes)
    {
        this.blockLandClaimExplosions = blockLandClaimExplosions;
        this.blockSurfaceCreeperExplosions = blockSurfaceCreeperExplosions;
        this.blockSurfaceOtherExplosions = blockSurfaceOtherExplosions;
        this.worldModes = Collections.unmodifiableMap(new LinkedHashMap<>(worldModes));
    }

    public static @NotNull ExplosionProtectionSettings upstreamDefaults()
    {
        return new ExplosionProtectionSettings(true, true, true, Collections.<String, ClaimWorldMode>emptyMap());
    }

    public boolean blockLandClaimExplosions()
    {
        return this.blockLandClaimExplosions;
    }

    public boolean blockSurfaceCreeperExplosions()
    {
        return this.blockSurfaceCreeperExplosions;
    }

    public boolean blockSurfaceOtherExplosions()
    {
        return this.blockSurfaceOtherExplosions;
    }

    public @NotNull ClaimWorldMode worldMode(@NotNull String worldKey)
    {
        ClaimWorldMode mode = this.worldModes.get(worldKey);
        return mode == null ? ClaimWorldMode.SURVIVAL : mode;
    }

    public @NotNull Map<String, ClaimWorldMode> worldModes()
    {
        return this.worldModes;
    }
}
