package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the upstream {@code GriefPrevention.Claims.InitialBlocks} option. */
public final class ClaimBlockConfigCodec
{
    private static final String ROOT = "GriefPrevention";
    private static final String CLAIMS = "Claims";
    private static final String INITIAL_BLOCKS = "InitialBlocks";

    private final Yaml yaml;

    public ClaimBlockConfigCodec()
    {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(100);
        options.setCodePointLimit(4 * 1024 * 1024);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    public synchronized @NotNull ClaimBlockSettings decode(@NotNull String input)
            throws ClaimBlockConfigException
    {
        final Object loaded;
        try
        {
            loaded = this.yaml.load(input);
        }
        catch (YAMLException exception)
        {
            throw new ClaimBlockConfigException("Invalid config YAML: " + exception.getMessage(), exception);
        }

        if (loaded == null)
        {
            return ClaimBlockSettings.upstreamDefaults();
        }

        Map<String, Object> document = stringMap(loaded, "config root");
        Map<String, Object> root = optionalMap(document.get(ROOT), ROOT);
        Map<String, Object> claims = optionalMap(root.get(CLAIMS), ROOT + "." + CLAIMS);
        int initialBlocks = integer(
                claims.get(INITIAL_BLOCKS),
                ClaimBlockSettings.DEFAULT_INITIAL_BLOCKS,
                ROOT + "." + CLAIMS + "." + INITIAL_BLOCKS
        );
        return new ClaimBlockSettings(initialBlocks);
    }

    private static int integer(@Nullable Object raw, int defaultValue, @NotNull String field)
            throws ClaimBlockConfigException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (!(raw instanceof Number))
        {
            throw new ClaimBlockConfigException(field + " must be a whole number.");
        }

        Number number = (Number) raw;
        long value = number.longValue();
        if (number.doubleValue() != (double) value
                || value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE)
        {
            throw new ClaimBlockConfigException(field + " must be a 32-bit whole number.");
        }
        return (int) value;
    }

    private static @NotNull Map<String, Object> optionalMap(
            @Nullable Object raw,
            @NotNull String context)
            throws ClaimBlockConfigException
    {
        if (raw == null)
        {
            return Collections.emptyMap();
        }
        return stringMap(raw, context);
    }

    private static @NotNull Map<String, Object> stringMap(
            @NotNull Object raw,
            @NotNull String context)
            throws ClaimBlockConfigException
    {
        if (!(raw instanceof Map))
        {
            throw new ClaimBlockConfigException(context + " must be a YAML mapping.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
        {
            if (!(entry.getKey() instanceof String))
            {
                throw new ClaimBlockConfigException(context + " contains a non-string key.");
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }
}
