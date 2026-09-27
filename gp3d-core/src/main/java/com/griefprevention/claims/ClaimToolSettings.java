package com.griefprevention.claims;

import com.griefprevention.protection.ExplosionProtectionSettings.ClaimWorldMode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The claim-tool and visualization subset of config.yml consumed by native adapters. */
public final class ClaimToolSettings
{
    public static final String DEFAULT_INVESTIGATION_TOOL = "STICK";
    public static final String DEFAULT_MODIFICATION_TOOL = "GOLDEN_SHOVEL";
    public static final int DEFAULT_MINIMUM_WIDTH = 5;
    public static final int DEFAULT_MINIMUM_AREA = 100;

    private final @NotNull String investigationTool;
    private final @NotNull String modificationTool;
    private final int minimumWidth;
    private final int minimumArea;
    private final boolean visualizationGlow;
    private final @NotNull Map<String, ClaimWorldMode> worldModes;

    public ClaimToolSettings(
            @NotNull String investigationTool,
            @NotNull String modificationTool,
            int minimumWidth,
            int minimumArea,
            boolean visualizationGlow,
            @NotNull Map<String, ClaimWorldMode> worldModes)
    {
        this.investigationTool = investigationTool;
        this.modificationTool = modificationTool;
        this.minimumWidth = minimumWidth;
        this.minimumArea = minimumArea;
        this.visualizationGlow = visualizationGlow;
        this.worldModes = Collections.unmodifiableMap(new LinkedHashMap<>(worldModes));
    }

    public static @NotNull ClaimToolSettings upstreamDefaults()
    {
        return new ClaimToolSettings(
                DEFAULT_INVESTIGATION_TOOL,
                DEFAULT_MODIFICATION_TOOL,
                DEFAULT_MINIMUM_WIDTH,
                DEFAULT_MINIMUM_AREA,
                false,
                Collections.<String, ClaimWorldMode>emptyMap()
        );
    }

    /**
     * @return the configured investigation tool, as a Bukkit material name or namespaced item id
     */
    public @NotNull String investigationTool()
    {
        return this.investigationTool;
    }

    /**
     * @return the configured modification tool, as a Bukkit material name or namespaced item id
     */
    public @NotNull String modificationTool()
    {
        return this.modificationTool;
    }

    public int minimumWidth()
    {
        return this.minimumWidth;
    }

    public int minimumArea()
    {
        return this.minimumArea;
    }

    public boolean visualizationGlow()
    {
        return this.visualizationGlow;
    }

    /**
     * Whether players may create or edit claims in a world. Unlisted worlds allow it, as they do
     * for explosion protection.
     */
    public boolean claimsEnabled(@NotNull String worldKey)
    {
        return this.worldModes.get(worldKey) != ClaimWorldMode.DISABLED;
    }

    /**
     * Translates a configured tool into a namespaced item id. Bukkit material names such as
     * {@code GOLDEN_SHOVEL} become {@code minecraft:golden_shovel}; names that already carry a
     * namespace, such as a modded item, pass through lowercased.
     */
    public static @NotNull String itemId(@NotNull String configured)
    {
        String trimmed = configured.trim().toLowerCase(Locale.ROOT);
        if (trimmed.contains(":"))
        {
            return trimmed;
        }
        switch (trimmed)
        {
            // Pre-1.13 material names that GriefPrevention configs may still carry.
            case "gold_spade":
                return "minecraft:golden_shovel";
            case "wood_spade":
                return "minecraft:wooden_shovel";
            case "iron_spade":
                return "minecraft:iron_shovel";
            case "diamond_spade":
                return "minecraft:diamond_shovel";
            default:
                return "minecraft:" + trimmed;
        }
    }
}
