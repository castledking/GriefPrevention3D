package com.griefprevention.protection;

import com.griefprevention.protection.ExplosionProtectionSettings.ClaimWorldMode;
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

/** Reads the config.yml fields behind {@link WorldProtectionSettings}. */
@ApiStatus.Internal
public final class WorldProtectionConfigCodec
{
    private static final String ROOT = "GriefPrevention";
    private static final String CLAIMS = "Claims";
    private static final String PVP = "PvP";

    private final Yaml yaml;

    public WorldProtectionConfigCodec()
    {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(100);
        options.setCodePointLimit(4 * 1024 * 1024);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    public synchronized @NotNull WorldProtectionSettings decode(@NotNull String input)
            throws WorldProtectionConfigException
    {
        final Object loaded = load(input);
        if (loaded == null)
        {
            return WorldProtectionSettings.upstreamDefaults();
        }

        Map<String, Object> root = optionalMap(stringMap(loaded, "config root").get(ROOT), ROOT);
        Map<String, Object> claims = optionalMap(root.get(CLAIMS), ROOT + "." + CLAIMS);
        Map<String, Object> pvp = optionalMap(root.get(PVP), ROOT + "." + PVP);
        Map<String, Object> protectPlayers = optionalMap(
                pvp.get("ProtectPlayersInLandClaims"), ROOT + ".PvP.ProtectPlayersInLandClaims");
        Map<String, Object> pvpToggle = optionalMap(claims.get("PvPToggle"), ROOT + ".Claims.PvPToggle");

        PistonMode pistonMode = PistonMode.of(string(root.get("PistonMovement"), "PistonMovement"));
        // Paper still honours the flags that preceded PistonMovement.
        if (Boolean.FALSE.equals(root.get("LimitPistonsToLandClaims")))
        {
            pistonMode = PistonMode.EVERYWHERE_SIMPLE;
        }
        if (Boolean.FALSE.equals(root.get("CheckPistonMovement")))
        {
            pistonMode = PistonMode.IGNORED;
        }

        return new WorldProtectionSettings(
                bool(root.get("FireSpreads"), false, "FireSpreads"),
                bool(root.get("FireDestroys"), false, "FireDestroys"),
                bool(claims.get("FireSpreadsInClaims"), false, "Claims.FireSpreadsInClaims"),
                bool(claims.get("FireDamagesInClaims"), false, "Claims.FireDamagesInClaims"),
                pistonMode,
                bool(root.get("EndermenMoveBlocks"), false, "EndermenMoveBlocks"),
                bool(root.get("CreaturesTrampleCrops"), false, "CreaturesTrampleCrops"),
                bool(claims.get("ProtectCreatures"), true, "Claims.ProtectCreatures"),
                // Paper defaults these to "no siege worlds"; the Fabric port has no sieges.
                bool(protectPlayers.get("PlayerOwnedClaims"), true, "PvP.ProtectPlayersInLandClaims.PlayerOwnedClaims"),
                bool(protectPlayers.get("AdministrativeClaims"), true, "PvP.ProtectPlayersInLandClaims.AdministrativeClaims"),
                bool(protectPlayers.get("AdministrativeSubdivisions"), true,
                        "PvP.ProtectPlayersInLandClaims.AdministrativeSubdivisions"),
                bool(optionalMap(pvpToggle.get("Claim"), "Claims.PvPToggle.Claim").get("Enabled"), false,
                        "Claims.PvPToggle.Claim.Enabled"),
                bool(optionalMap(pvpToggle.get("Subdivision"), "Claims.PvPToggle.Subdivision").get("Enabled"), false,
                        "Claims.PvPToggle.Subdivision.Enabled"),
                worldModes(optionalMap(claims.get("Mode"), ROOT + ".Claims.Mode"))
        );
    }

    /** Reads the switches behind {@link BlockUseSettings}, with Paper's defaults. */
    public synchronized @NotNull BlockUseSettings decodeBlockUse(@NotNull String input)
            throws WorldProtectionConfigException
    {
        final Object loaded = load(input);
        if (loaded == null)
        {
            return BlockUseSettings.upstreamDefaults();
        }

        Map<String, Object> root = optionalMap(stringMap(loaded, "config root").get(ROOT), ROOT);
        Map<String, Object> claims = optionalMap(root.get(CLAIMS), ROOT + "." + CLAIMS);
        return new BlockUseSettings(
                bool(claims.get("PreventTheft"), true, "Claims.PreventTheft"),
                bool(claims.get("LockWoodenDoors"), false, "Claims.LockWoodenDoors"),
                bool(claims.get("LockTrapDoors"), false, "Claims.LockTrapDoors"),
                bool(claims.get("LockFenceGates"), true, "Claims.LockFenceGates"),
                bool(claims.get("PreventButtonsSwitches"), true, "Claims.PreventButtonsSwitches"),
                bool(claims.get("LecternReadingRequiresAccessTrust"), true, "Claims.LecternReadingRequiresAccessTrust")
        );
    }

    private @Nullable Object load(@NotNull String input) throws WorldProtectionConfigException
    {
        try
        {
            return this.yaml.load(input);
        }
        catch (YAMLException exception)
        {
            throw new WorldProtectionConfigException("Invalid config YAML: " + exception.getMessage(), exception);
        }
    }

    private static @NotNull Map<String, ClaimWorldMode> worldModes(@NotNull Map<String, Object> modes)
            throws WorldProtectionConfigException
    {
        Map<String, ClaimWorldMode> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : modes.entrySet())
        {
            if (!(entry.getValue() instanceof String))
            {
                throw new WorldProtectionConfigException(
                        "World mode for " + entry.getKey() + " must be Survival, Creative, or Disabled.");
            }
            try
            {
                parsed.put(entry.getKey(), ClaimWorldMode.parse((String) entry.getValue()));
            }
            catch (IllegalArgumentException exception)
            {
                throw new WorldProtectionConfigException(
                        "Unknown claim mode '" + entry.getValue() + "' for world " + entry.getKey() + ".", exception);
            }
        }
        return parsed;
    }

    private static @Nullable String string(@Nullable Object raw, @NotNull String field)
            throws WorldProtectionConfigException
    {
        if (raw == null || raw instanceof String)
        {
            return (String) raw;
        }
        throw new WorldProtectionConfigException(field + " must be text.");
    }

    private static boolean bool(@Nullable Object raw, boolean defaultValue, @NotNull String field)
            throws WorldProtectionConfigException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (raw instanceof Boolean)
        {
            return (Boolean) raw;
        }
        throw new WorldProtectionConfigException(field + " must be true or false.");
    }

    private static @NotNull Map<String, Object> optionalMap(@Nullable Object raw, @NotNull String context)
            throws WorldProtectionConfigException
    {
        return raw == null ? Collections.<String, Object>emptyMap() : stringMap(raw, context);
    }

    private static @NotNull Map<String, Object> stringMap(@NotNull Object raw, @NotNull String context)
            throws WorldProtectionConfigException
    {
        if (!(raw instanceof Map))
        {
            throw new WorldProtectionConfigException(context + " must be a YAML mapping.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
        {
            if (!(entry.getKey() instanceof String))
            {
                throw new WorldProtectionConfigException(context + " contains a non-string key.");
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }
}
