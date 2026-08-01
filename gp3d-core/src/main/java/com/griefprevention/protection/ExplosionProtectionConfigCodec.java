package com.griefprevention.protection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads only the config.yml fields currently activated by Fabric explosion protection. */
@ApiStatus.Internal
public final class ExplosionProtectionConfigCodec
{
    private static final String ROOT = "GriefPrevention";
    private static final String BLOCK_CLAIMS = "BlockLandClaimExplosions";
    private static final String BLOCK_SURFACE_CREEPERS = "BlockSurfaceCreeperExplosions";
    private static final String BLOCK_SURFACE_OTHER = "BlockSurfaceOtherExplosions";
    private static final String CLAIMS = "Claims";
    private static final String MODE = "Mode";

    private final Yaml yaml;

    public ExplosionProtectionConfigCodec()
    {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(100);
        options.setCodePointLimit(4 * 1024 * 1024);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    public synchronized @NotNull ExplosionProtectionSettings decode(@NotNull String input)
            throws ExplosionProtectionConfigException
    {
        final Object loaded;
        try
        {
            loaded = this.yaml.load(input);
        }
        catch (YAMLException exception)
        {
            throw new ExplosionProtectionConfigException("Invalid config YAML: " + exception.getMessage(), exception);
        }

        if (loaded == null)
        {
            return ExplosionProtectionSettings.upstreamDefaults();
        }
        Map<String, Object> document = stringMap(loaded, "config root");
        Map<String, Object> root = optionalMap(document.get(ROOT), ROOT);
        if (root.isEmpty())
        {
            return ExplosionProtectionSettings.upstreamDefaults();
        }

        boolean blockClaims = bool(root.get(BLOCK_CLAIMS), true, BLOCK_CLAIMS);
        boolean blockSurfaceCreepers = bool(
                root.get(BLOCK_SURFACE_CREEPERS),
                true,
                BLOCK_SURFACE_CREEPERS
        );
        boolean blockSurfaceOther = bool(root.get(BLOCK_SURFACE_OTHER), true, BLOCK_SURFACE_OTHER);

        Map<String, Object> claims = optionalMap(root.get(CLAIMS), ROOT + "." + CLAIMS);
        Map<String, Object> modes = optionalMap(claims.get(MODE), ROOT + "." + CLAIMS + "." + MODE);
        Map<String, ExplosionProtectionSettings.ClaimWorldMode> parsedModes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : modes.entrySet())
        {
            if (!(entry.getValue() instanceof String))
            {
                throw new ExplosionProtectionConfigException(
                        "World mode for " + entry.getKey() + " must be Survival, Creative, or Disabled."
                );
            }
            try
            {
                parsedModes.put(
                        entry.getKey(),
                        ExplosionProtectionSettings.ClaimWorldMode.parse((String) entry.getValue())
                );
            }
            catch (IllegalArgumentException exception)
            {
                throw new ExplosionProtectionConfigException(
                        "Unknown claim mode '" + entry.getValue() + "' for world " + entry.getKey() + ".",
                        exception
                );
            }
        }

        return new ExplosionProtectionSettings(
                blockClaims,
                blockSurfaceCreepers,
                blockSurfaceOther,
                parsedModes
        );
    }

    private static boolean bool(@Nullable Object raw, boolean defaultValue, @NotNull String field)
            throws ExplosionProtectionConfigException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (raw instanceof Boolean)
        {
            return (Boolean) raw;
        }
        throw new ExplosionProtectionConfigException(field + " must be true or false.");
    }

    private static @NotNull Map<String, Object> optionalMap(@Nullable Object raw, @NotNull String context)
            throws ExplosionProtectionConfigException
    {
        if (raw == null)
        {
            return Collections.emptyMap();
        }
        return stringMap(raw, context);
    }

    private static @NotNull Map<String, Object> stringMap(@NotNull Object raw, @NotNull String context)
            throws ExplosionProtectionConfigException
    {
        if (!(raw instanceof Map))
        {
            throw new ExplosionProtectionConfigException(context + " must be a YAML mapping.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
        {
            if (!(entry.getKey() instanceof String))
            {
                throw new ExplosionProtectionConfigException(context + " contains a non-string key.");
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }
}
