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

/** Reads upstream claim-block and per-player claim mutation settings. */
public final class ClaimBlockConfigCodec
{
    private static final String ROOT = "GriefPrevention";
    private static final String CLAIMS = "Claims";
    private static final String INITIAL_BLOCKS = "InitialBlocks";
    private static final String BLOCKS_ACCRUED_PER_HOUR = "BlocksAccruedPerHour";
    private static final String CLAIM_BLOCKS_ACCRUED_PER_HOUR = "Claim Blocks Accrued Per Hour";
    private static final String MAX_ACCRUED_BLOCKS = "MaxAccruedBlocks";
    private static final String MAX_ACCRUED_CLAIM_BLOCKS = "Max Accrued Claim Blocks";
    private static final String ACCRUED_IDLE_THRESHOLD = "AccruedIdleThreshold";
    private static final String ACCRUED_IDLE_THRESHOLD_SPACED = "Accrued Idle Threshold";
    private static final String ACCRUED_IDLE_PERCENT = "AccruedIdlePercent";
    private static final String DEFAULT = "Default";
    private static final String MAXIMUM_CLAIMS_PER_PLAYER = "MaximumNumberOfClaimsPerPlayer";
    private static final String ABANDON_RETURN_RATIO = "AbandonReturnRatio";

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
        int legacyBlocksAccruedPerHour = integer(
                claims.get(BLOCKS_ACCRUED_PER_HOUR),
                ClaimBlockSettings.DEFAULT_BLOCKS_ACCRUED_PER_HOUR,
                ROOT + "." + CLAIMS + "." + BLOCKS_ACCRUED_PER_HOUR
        );
        Map<String, Object> blocksAccruedPerHourSection = optionalMap(
                claims.get(CLAIM_BLOCKS_ACCRUED_PER_HOUR),
                ROOT + "." + CLAIMS + "." + CLAIM_BLOCKS_ACCRUED_PER_HOUR
        );
        int blocksAccruedPerHour = integer(
                blocksAccruedPerHourSection.get(DEFAULT),
                legacyBlocksAccruedPerHour,
                ROOT + "." + CLAIMS + "." + CLAIM_BLOCKS_ACCRUED_PER_HOUR + "." + DEFAULT
        );
        int legacyMaximumAccruedClaimBlocks = integer(
                claims.get(MAX_ACCRUED_BLOCKS),
                ClaimBlockSettings.DEFAULT_MAXIMUM_ACCRUED_CLAIM_BLOCKS,
                ROOT + "." + CLAIMS + "." + MAX_ACCRUED_BLOCKS
        );
        Map<String, Object> maximumAccruedClaimBlocksSection = optionalMap(
                claims.get(MAX_ACCRUED_CLAIM_BLOCKS),
                ROOT + "." + CLAIMS + "." + MAX_ACCRUED_CLAIM_BLOCKS
        );
        int maximumAccruedClaimBlocks = integer(
                maximumAccruedClaimBlocksSection.get(DEFAULT),
                legacyMaximumAccruedClaimBlocks,
                ROOT + "." + CLAIMS + "." + MAX_ACCRUED_CLAIM_BLOCKS + "." + DEFAULT
        );
        int legacyAccruedIdleThreshold = integer(
                claims.get(ACCRUED_IDLE_THRESHOLD),
                ClaimBlockSettings.DEFAULT_ACCRUED_IDLE_THRESHOLD,
                ROOT + "." + CLAIMS + "." + ACCRUED_IDLE_THRESHOLD
        );
        int accruedIdleThreshold = integer(
                claims.get(ACCRUED_IDLE_THRESHOLD_SPACED),
                legacyAccruedIdleThreshold,
                ROOT + "." + CLAIMS + "." + ACCRUED_IDLE_THRESHOLD_SPACED
        );
        int accruedIdlePercent = Math.max(0, integer(
                claims.get(ACCRUED_IDLE_PERCENT),
                ClaimBlockSettings.DEFAULT_ACCRUED_IDLE_PERCENT,
                ROOT + "." + CLAIMS + "." + ACCRUED_IDLE_PERCENT
        ));
        int maximumClaimsPerPlayer = integer(
                claims.get(MAXIMUM_CLAIMS_PER_PLAYER),
                ClaimBlockSettings.DEFAULT_MAXIMUM_CLAIMS_PER_PLAYER,
                ROOT + "." + CLAIMS + "." + MAXIMUM_CLAIMS_PER_PLAYER
        );
        double abandonReturnRatio = decimal(
                claims.get(ABANDON_RETURN_RATIO),
                ClaimBlockSettings.DEFAULT_ABANDON_RETURN_RATIO,
                ROOT + "." + CLAIMS + "." + ABANDON_RETURN_RATIO
        );
        return new ClaimBlockSettings(
                initialBlocks,
                blocksAccruedPerHour,
                maximumAccruedClaimBlocks,
                accruedIdleThreshold,
                accruedIdlePercent,
                maximumClaimsPerPlayer,
                abandonReturnRatio
        );
    }

    private static double decimal(@Nullable Object raw, double defaultValue, @NotNull String field)
            throws ClaimBlockConfigException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (!(raw instanceof Number))
        {
            throw new ClaimBlockConfigException(field + " must be a number.");
        }
        // Deliberately do not clamp the ratio. Bukkit accepts the configured double verbatim.
        return ((Number) raw).doubleValue();
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
