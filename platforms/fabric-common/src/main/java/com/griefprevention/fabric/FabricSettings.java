package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimExpirationSettings;
import com.griefprevention.claims.ClaimToolConfigCodec;
import com.griefprevention.claims.ClaimToolConfigException;
import com.griefprevention.claims.ClaimToolSettings;
import com.griefprevention.claims.ClaimTransferSettings;
import com.griefprevention.protection.BlockUseSettings;
import com.griefprevention.protection.EnderPearlSettings;
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
    private volatile @NotNull BlockUseSettings blockUse = BlockUseSettings.upstreamDefaults();
    private volatile @NotNull ClaimTransferSettings transfer = ClaimTransferSettings.upstreamDefaults();
    private volatile @NotNull EnderPearlSettings enderPearls = EnderPearlSettings.upstreamDefaults();
    private volatile boolean allowTrappedInAdminClaims;
    private volatile boolean raidTriggersRequireBuildTrust = true;
    private volatile @NotNull ClaimExpirationSettings expiration = ClaimExpirationSettings.upstreamDefaults();
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
        boolean unreadable = false;
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
            unreadable = true;
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

        BlockUseSettings loadedBlockUse;
        try
        {
            loadedBlockUse = this.worldCodec.decodeBlockUse(contents);
        }
        catch (WorldProtectionConfigException exception)
        {
            this.logger.error("Could not read the door, container and lectern settings from {}; "
                    + "using the defaults.", this.configFile, exception);
            loadedBlockUse = BlockUseSettings.upstreamDefaults();
        }

        ClaimTransferSettings loadedTransfer;
        try
        {
            loadedTransfer = this.codec.decodeTransfer(contents);
        }
        catch (ClaimToolConfigException exception)
        {
            // Off is the safe default: nobody gives claims away until the config is fixed.
            this.logger.error("Could not read Claims.TransferClaim from {}; claim giving stays off.",
                    this.configFile, exception);
            loadedTransfer = ClaimTransferSettings.upstreamDefaults();
        }

        EnderPearlSettings loadedEnderPearls;
        try
        {
            loadedEnderPearls = this.worldCodec.decodeEnderPearls(contents);
        }
        catch (WorldProtectionConfigException exception)
        {
            this.logger.error("Could not read the ender pearl settings from {}; using the defaults.",
                    this.configFile, exception);
            loadedEnderPearls = EnderPearlSettings.upstreamDefaults();
        }

        boolean loadedAllowTrappedInAdminClaims;
        try
        {
            loadedAllowTrappedInAdminClaims = this.codec.decodeAllowTrappedInAdminClaims(contents);
        }
        catch (ClaimToolConfigException exception)
        {
            this.logger.error("Could not read Claims.AllowTrappedInAdminClaims from {}; /trapped stays out of "
                    + "administrative claims.", this.configFile, exception);
            loadedAllowTrappedInAdminClaims = false;
        }

        boolean loadedRaidTriggersRequireBuildTrust;
        try
        {
            loadedRaidTriggersRequireBuildTrust = this.worldCodec.decodeRaidTriggersRequireBuildTrust(contents);
        }
        catch (WorldProtectionConfigException exception)
        {
            this.logger.error("Could not read Claims.RaidTriggersRequireBuildTrust from {}; raids stay protected.",
                    this.configFile, exception);
            loadedRaidTriggersRequireBuildTrust = true;
        }

        // Deleting claims on a misread setting cannot be undone, so an unreadable file or a bad value
        // turns expiration off instead of falling back to Paper's defaults.
        ClaimExpirationSettings loadedExpiration;
        try
        {
            loadedExpiration = unreadable ? disabledExpiration() : this.codec.decodeClaimExpiration(contents);
        }
        catch (ClaimToolConfigException exception)
        {
            this.logger.error("Could not read Claims.Expiration from {}; claims will not expire until it is fixed.",
                    this.configFile, exception);
            loadedExpiration = disabledExpiration();
        }

        this.tools = loadedTools;
        this.world = loadedWorld;
        this.blockUse = loadedBlockUse;
        this.transfer = loadedTransfer;
        this.enderPearls = loadedEnderPearls;
        this.allowTrappedInAdminClaims = loadedAllowTrappedInAdminClaims;
        this.raidTriggersRequireBuildTrust = loadedRaidTriggersRequireBuildTrust;
        this.expiration = loadedExpiration;
        this.investigationTool = null;
        this.modificationTool = null;
    }

    /** Fire, fluid, piston, mob and combat rules around claims. */
    @NotNull WorldProtectionSettings world()
    {
        return this.world;
    }

    /** Which blocks a player needs trust to use, and how much. */
    @NotNull BlockUseSettings blockUse()
    {
        return this.blockUse;
    }

    /** Whether players may give their claims away with /transferclaim, and the price. */
    @NotNull ClaimTransferSettings transfer()
    {
        return this.transfer;
    }

    /** Whether ender pearls and chorus fruit need access trust to land in a claim, and refunds. */
    @NotNull EnderPearlSettings enderPearls()
    {
        return this.enderPearls;
    }

    /** Whether /trapped rescues players from administrative claims too. */
    boolean allowTrappedInAdminClaims()
    {
        return this.allowTrappedInAdminClaims;
    }

    /** Whether starting or extending a raid in a claim requires build trust, as on Paper. */
    boolean raidTriggersRequireBuildTrust()
    {
        return this.raidTriggersRequireBuildTrust;
    }

    private static @NotNull ClaimExpirationSettings disabledExpiration()
    {
        return new ClaimExpirationSettings(0, 0, 0, 0,
                ClaimExpirationSettings.DEFAULT_NEW_PLAYER_CLAIM_RADIUS,
                ClaimExpirationSettings.DEFAULT_CHECK_RATE_SECONDS);
    }

    /** {@code Claims.Expiration}: when the claims of players who stopped playing expire. */
    @NotNull ClaimExpirationSettings expiration()
    {
        return this.expiration;
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
