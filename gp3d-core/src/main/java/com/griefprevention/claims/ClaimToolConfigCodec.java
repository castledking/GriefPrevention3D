package com.griefprevention.claims;

import com.griefprevention.protection.ExplosionProtectionSettings.ClaimWorldMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the config.yml fields that shape the claim tools and their visualization. */
public final class ClaimToolConfigCodec
{
    private static final String ROOT = "GriefPrevention";
    private static final String CLAIMS = "Claims";
    private static final String INVESTIGATION_TOOL = "InvestigationTool";
    private static final String MODIFICATION_TOOL = "ModificationTool";
    private static final String MINIMUM_WIDTH = "MinimumWidth";
    private static final String MINIMUM_AREA = "MinimumArea";
    private static final String MODE = "Mode";
    private static final String VISUALIZATION_GLOW = "VisualizationGlow";

    private final Yaml yaml;

    public ClaimToolConfigCodec()
    {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(100);
        options.setCodePointLimit(4 * 1024 * 1024);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    /** Reads {@code Claims.TransferClaim}, with Paper's defaults. */
    public synchronized @NotNull ClaimTransferSettings decodeTransfer(@NotNull String input)
            throws ClaimToolConfigException
    {
        final Object loaded;
        try
        {
            loaded = this.yaml.load(input);
        }
        catch (YAMLException exception)
        {
            throw new ClaimToolConfigException("Invalid config YAML: " + exception.getMessage(), exception);
        }
        if (loaded == null)
        {
            return ClaimTransferSettings.upstreamDefaults();
        }

        Map<String, Object> root = optionalMap(stringMap(loaded, "config root").get(ROOT), ROOT);
        Map<String, Object> claims = optionalMap(root.get(CLAIMS), ROOT + "." + CLAIMS);
        Map<String, Object> transfer = optionalMap(claims.get("TransferClaim"), ROOT + ".Claims.TransferClaim");
        Object price = transfer.get("Price");
        if (price != null && !(price instanceof Number))
        {
            throw new ClaimToolConfigException("Claims.TransferClaim.Price must be a number.");
        }
        double parsedPrice = price == null ? 0.0 : ((Number) price).doubleValue();
        if (parsedPrice < 0.0 || Double.isNaN(parsedPrice) || Double.isInfinite(parsedPrice))
        {
            throw new ClaimToolConfigException("Claims.TransferClaim.Price must not be negative.");
        }
        return new ClaimTransferSettings(
                bool(transfer.get("Enabled"), false, "Claims.TransferClaim.Enabled"),
                parsedPrice);
    }

    public synchronized @NotNull ClaimToolSettings decode(@NotNull String input) throws ClaimToolConfigException
    {
        final Object loaded;
        try
        {
            loaded = this.yaml.load(input);
        }
        catch (YAMLException exception)
        {
            throw new ClaimToolConfigException("Invalid config YAML: " + exception.getMessage(), exception);
        }
        if (loaded == null)
        {
            return ClaimToolSettings.upstreamDefaults();
        }

        Map<String, Object> root = optionalMap(stringMap(loaded, "config root").get(ROOT), ROOT);
        Map<String, Object> claims = optionalMap(root.get(CLAIMS), ROOT + "." + CLAIMS);
        Map<String, ClaimWorldMode> modes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : optionalMap(claims.get(MODE), ROOT + "." + CLAIMS + "." + MODE).entrySet())
        {
            if (!(entry.getValue() instanceof String))
            {
                throw new ClaimToolConfigException(
                        "World mode for " + entry.getKey() + " must be Survival, Creative, or Disabled.");
            }
            try
            {
                modes.put(entry.getKey(), ClaimWorldMode.parse((String) entry.getValue()));
            }
            catch (IllegalArgumentException exception)
            {
                throw new ClaimToolConfigException(
                        "Unknown claim mode '" + entry.getValue() + "' for world " + entry.getKey() + ".", exception);
            }
        }

        return new ClaimToolSettings(
                string(claims.get(INVESTIGATION_TOOL), ClaimToolSettings.DEFAULT_INVESTIGATION_TOOL, INVESTIGATION_TOOL),
                string(claims.get(MODIFICATION_TOOL), ClaimToolSettings.DEFAULT_MODIFICATION_TOOL, MODIFICATION_TOOL),
                integer(claims.get(MINIMUM_WIDTH), ClaimToolSettings.DEFAULT_MINIMUM_WIDTH, MINIMUM_WIDTH),
                integer(claims.get(MINIMUM_AREA), ClaimToolSettings.DEFAULT_MINIMUM_AREA, MINIMUM_AREA),
                bool(root.get(VISUALIZATION_GLOW), false, VISUALIZATION_GLOW),
                modes
        );
    }

    private static @NotNull String string(@Nullable Object raw, @NotNull String defaultValue, @NotNull String field)
            throws ClaimToolConfigException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (!(raw instanceof String) || ((String) raw).trim().isEmpty())
        {
            throw new ClaimToolConfigException(field + " must name an item.");
        }
        return ((String) raw).trim();
    }

    private static int integer(@Nullable Object raw, int defaultValue, @NotNull String field)
            throws ClaimToolConfigException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (!(raw instanceof Integer))
        {
            throw new ClaimToolConfigException(field + " must be a whole number.");
        }
        return (Integer) raw;
    }

    private static boolean bool(@Nullable Object raw, boolean defaultValue, @NotNull String field)
            throws ClaimToolConfigException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (!(raw instanceof Boolean))
        {
            throw new ClaimToolConfigException(field + " must be true or false.");
        }
        return (Boolean) raw;
    }

    private static @NotNull Map<String, Object> optionalMap(@Nullable Object raw, @NotNull String context)
            throws ClaimToolConfigException
    {
        return raw == null ? Collections.<String, Object>emptyMap() : stringMap(raw, context);
    }

    private static @NotNull Map<String, Object> stringMap(@NotNull Object raw, @NotNull String context)
            throws ClaimToolConfigException
    {
        if (!(raw instanceof Map))
        {
            throw new ClaimToolConfigException(context + " must be a YAML mapping.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
        {
            if (!(entry.getKey() instanceof String))
            {
                throw new ClaimToolConfigException(context + " contains a non-string key.");
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }
}
