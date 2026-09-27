package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimToolConfigCodec;
import com.griefprevention.claims.ClaimToolConfigException;
import com.griefprevention.claims.ClaimToolSettings;
import com.griefprevention.protection.WorldProtectionConfigCodec;
import com.griefprevention.protection.WorldProtectionConfigException;
import com.griefprevention.protection.WorldProtectionSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The config.yml settings the Fabric claim tools, visualizations and world protections read,
 * reloaded with /gpreload.
 *
 * <p>Unlike claim data and explosion rules, a bad tool setting does not stop the server: it is logged
 * and replaced by its default, as the Paper plugin does for an unknown tool material.
 */
final class FabricSettings
{
    private final ClaimToolConfigCodec codec = new ClaimToolConfigCodec();
    private final WorldProtectionConfigCodec worldCodec = new WorldProtectionConfigCodec();
    private final Path configFile;
    private final Logger logger;

    private volatile @NotNull ClaimToolSettings tools = ClaimToolSettings.upstreamDefaults();
    private volatile @NotNull WorldProtectionSettings world = WorldProtectionSettings.upstreamDefaults();
    // Resolved on first use: registries are not guaranteed to be populated when the mod initializes.
    private volatile @Nullable Item investigationTool;
    private volatile @Nullable Item modificationTool;

    FabricSettings(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        this.configFile = dataFolder.resolve("config.yml");
        this.logger = logger;
        reload();
    }

    void reload()
    {
        String contents;
        try
        {
            contents = Files.readString(this.configFile, StandardCharsets.UTF_8);
        }
        catch (NoSuchFileException exception)
        {
            contents = "";
        }
        catch (IOException exception)
        {
            this.logger.warn("Could not read {}; using the default claim tool and protection settings.",
                    this.configFile, exception);
            contents = "";
        }

        ClaimToolSettings loadedTools;
        try
        {
            loadedTools = this.codec.decode(contents);
        }
        catch (ClaimToolConfigException exception)
        {
            this.logger.warn("Could not read the claim tool settings from {}; using the defaults.",
                    this.configFile, exception);
            loadedTools = ClaimToolSettings.upstreamDefaults();
        }

        WorldProtectionSettings loadedWorld;
        try
        {
            loadedWorld = this.worldCodec.decode(contents);
        }
        catch (WorldProtectionConfigException exception)
        {
            // The defaults are Paper's, which are the protective ones.
            this.logger.error("Could not read the fire, fluid, piston and combat settings from {}; "
                    + "using the defaults.", this.configFile, exception);
            loadedWorld = WorldProtectionSettings.upstreamDefaults();
        }

        this.tools = loadedTools;
        this.world = loadedWorld;
        this.investigationTool = null;
        this.modificationTool = null;
    }

    /** Fire, fluid, piston, mob and combat rules around claims. */
    @NotNull WorldProtectionSettings world()
    {
        return this.world;
    }

    @NotNull ClaimToolSettings tools()
    {
        return this.tools;
    }

    boolean isInvestigationTool(@NotNull ItemStack stack)
    {
        Item tool = this.investigationTool;
        if (tool == null)
        {
            tool = resolve(this.tools.investigationTool(), ClaimToolSettings.DEFAULT_INVESTIGATION_TOOL);
            this.investigationTool = tool;
        }
        return !stack.isEmpty() && stack.is(tool);
    }

    boolean isModificationTool(@NotNull ItemStack stack)
    {
        Item tool = this.modificationTool;
        if (tool == null)
        {
            tool = resolve(this.tools.modificationTool(), ClaimToolSettings.DEFAULT_MODIFICATION_TOOL);
            this.modificationTool = tool;
        }
        return !stack.isEmpty() && stack.is(tool);
    }

    private @NotNull Item resolve(@NotNull String configured, @NotNull String fallback)
    {
        Optional<Item> item = lookup(configured);
        if (item.isPresent())
        {
            return item.get();
        }
        this.logger.error("Item {} not found. Defaulting to {}. Please update your config.yml.", configured, fallback);
        return lookup(fallback).orElseThrow(() -> new IllegalStateException("Missing default item " + fallback));
    }

    private static @NotNull Optional<Item> lookup(@NotNull String configured)
    {
        Identifier id = Identifier.tryParse(ClaimToolSettings.itemId(configured));
        return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
    }
}
