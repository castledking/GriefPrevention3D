package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBlockConfigCodec;
import com.griefprevention.claims.ClaimBlockConfigException;
import com.griefprevention.claims.ClaimBlockSettings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class FabricDataFolder
{
    static final String LOCATION_MIGRATION_MARKER = "_fabricDataLocation";
    private static final String LOCATION_MIGRATION_MARKER_CONTENT = """
            version=1
            source=config/GriefPreventionData
            destination=plugins/GriefPreventionData
            """;

    private static final ClaimBlockConfigCodec CLAIM_BLOCK_CONFIG_CODEC = new ClaimBlockConfigCodec();
    private static final String DEFAULT_CONFIG_TEMPLATE = """
            # GriefPrevention3D Fabric uses the same data folder name and top-level config shape as the Paper plugin.
            # Only the options wired by the Fabric port are active right now.
            GriefPrevention:
              ConfigVersion: 1
              BlockLandClaimExplosions: true
              BlockSurfaceCreeperExplosions: true
              BlockSurfaceOtherExplosions: true
              PvP:
                AllowContainerAccess: false
                AllowRespawnAnchor: false
              Claims:
                InitialBlocks: %d
                Claim Blocks Accrued Per Hour:
                  Default: %d
                Max Accrued Claim Blocks:
                  Default: %d
                Accrued Idle Threshold: %d
                AccruedIdlePercent: %d
                MaximumNumberOfClaimsPerPlayer: %d
                AbandonReturnRatio: %s
                Mode:
                  world: Survival
                  world_nether: Disabled
                  world_the_end: Disabled
                InvestigationTool: STICK
                ModificationTool: GOLDEN_SHOVEL
                MinimumWidth: 5
                MinimumArea: 100
                AllowNestedSubClaims: false
                AllowShapedClaims: false
                UseClaimSelectSessions: true
                UseClaimSelectedMessages: false
                FireSpreadsInClaims: false
                FireDamagesInClaims: false
              VisualizationGlow: false
              FireSpreads: false
              FireDestroys: false
            """;
    private static final String DEFAULT_CONFIG = defaultConfig(ClaimBlockSettings.upstreamDefaults());

    private static final String DEFAULT_MESSAGES = """
            # GriefPrevention3D Fabric keeps message keys under the same Messages.* root as the Paper plugin.
            # This file currently seeds the native Fabric messages; more Paper messages will be added as features port.
            Messages:
              BlockNotClaimed: "No one has claimed this block."
              BlockClaimed: "That block has been claimed by {0}."
              NoCreateClaimPermission: "You don't have permission to claim land."
              ResizeStart: "Resizing claim.  Use your shovel again at the new location for this corner."
              ClaimStart: "Claim corner set!  Use the shovel again at the opposite corner to claim a rectangle of land.  To cancel, put your shovel away."
              NewClaimTooNarrow: "This claim would be too small.  Any claim must be at least {0} blocks wide."
              ResizeClaimTooNarrow: "This new size would be too small.  Claims must be at least {0} blocks wide."
              ResizeNeedMoreBlocks: "You don't have enough blocks for this size.  You need {0} more."
              CreateClaimFailOverlapShort: "Your selected area overlaps an existing claim."
              ClaimCreationFailedOverClaimCountLimit: "You've reached your limit on land claims. Use /abandonclaim to remove one before creating another."
              CreateClaimInsufficientBlocks: "You don't have enough blocks to claim that entire area.  You need {0} more blocks."
              CreateClaimSuccess: "Claim created!  Use /trust to share it with friends."
              ClaimResizeSuccess: "Claim resized.  {0} available claim blocks remaining."
              EndBlockMath: " = {0} blocks left to spend"
              OnlyOwnersModifyClaims: "Only {0} can modify this claim."
              NotYourClaim: "This isn't your claim."
              DeleteClaimMissing: "There's no claim here."
              DeleteSuccess: "Claim deleted."
              NoAccessPermission: "You don't have {0}'s permission to use that."
              NoBuildPermission: "You don't have {0}'s permission to build here."
              NoContainersPermission: "You don't have {0}'s permission to use that."
              OwnerNameForAdminClaims: "an administrator"
              PlaceholderTrustLevelUnclaimed: "Unclaimed"
              PlaceholderTrustLevelOwner: "Owner"
              PlaceholderTrustLevelManager: "Manager"
              PlaceholderTrustLevelBuilder: "Builder"
              PlaceholderTrustLevelAccess: "Access"
              PlaceholderTrustLevelContainer: "Container"
              PlaceholderTrustLevelUntrusted: "Untrusted"
            """;

    private FabricDataFolder()
    {
    }

    /**
     * Resolves the Paper-compatible datastore location, importing the previous Fabric-only
     * location after a complete validation when necessary.
     */
    static @NotNull Path resolveSharedDataFolder(
            @NotNull Path gameDirectory,
            @NotNull Path configDirectory,
            @NotNull Logger logger)
    {
        Path shared = gameDirectory.resolve("plugins").resolve("GriefPreventionData").normalize();
        Path previousFabric = configDirectory.resolve("GriefPreventionData").normalize();
        if (shared.toAbsolutePath().normalize().equals(previousFabric.toAbsolutePath().normalize()))
        {
            return shared;
        }

        boolean sharedExists = existsWithoutFollowingLinks(shared);
        boolean previousExists = existsWithoutFollowingLinks(previousFabric);
        requireDirectoryWhenPresent(shared, sharedExists, "shared Paper/Fabric datastore");
        requireDirectoryWhenPresent(previousFabric, previousExists, "previous Fabric datastore");

        if (sharedExists)
        {
            if (previousExists && !hasLocationMigrationMarker(shared))
            {
                throw new IllegalStateException(
                        "Both " + shared + " and " + previousFabric + " contain GriefPrevention data, "
                                + "but no completed location-migration marker exists. Refusing to choose a datastore."
                );
            }
            if (previousExists)
            {
                logger.info(
                        "Using shared GriefPrevention datastore {}; the previous Fabric copy remains at {} as a rollback backup.",
                        shared,
                        previousFabric
                );
            }
            return shared;
        }

        if (!previousExists)
        {
            return shared;
        }

        return importPreviousFabricData(previousFabric, shared, logger);
    }

    private static @NotNull Path importPreviousFabricData(
            @NotNull Path source,
            @NotNull Path destination,
            @NotNull Logger logger)
    {
        Path staged = null;
        boolean promoted = false;
        try
        {
            Path parent = destination.getParent();
            Files.createDirectories(parent);
            staged = Files.createTempDirectory(parent, ".GriefPreventionData.import-");
            FabricClaimFileStore.copyRecursively(source, staged);

            // This validates the complete graph, schema, IDs, and player-data layout before the
            // shared path becomes visible. Any required schema normalization happens only in the copy.
            FabricClaimFileStore.load(staged, logger);
            Files.writeString(
                    staged.resolve(LOCATION_MIGRATION_MARKER),
                    LOCATION_MIGRATION_MARKER_CONTENT,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE);
            promoted = true;
            logger.info(
                    "Imported and validated GriefPrevention data from {} to shared datastore {}; "
                            + "the source was retained as a rollback backup.",
                    source,
                    destination
            );
            return destination;
        }
        catch (IOException | IllegalStateException exception)
        {
            throw new IllegalStateException(
                    "Could not safely import the previous Fabric datastore from " + source
                            + " to " + destination + "; the source was left untouched.",
                    exception
            );
        }
        finally
        {
            if (!promoted && staged != null)
            {
                FabricClaimFileStore.deleteRecursivelyQuietly(staged);
            }
        }
    }

    private static boolean existsWithoutFollowingLinks(@NotNull Path path)
    {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireDirectoryWhenPresent(
            @NotNull Path path,
            boolean present,
            @NotNull String description)
    {
        if (present && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IllegalStateException("The " + description + " is not a regular directory: " + path);
        }
    }

    private static boolean hasLocationMigrationMarker(@NotNull Path dataFolder)
    {
        Path marker = dataFolder.resolve(LOCATION_MIGRATION_MARKER);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS))
        {
            return false;
        }
        try
        {
            return LOCATION_MIGRATION_MARKER_CONTENT.equals(
                    Files.readString(marker, StandardCharsets.UTF_8)
            );
        }
        catch (IOException exception)
        {
            return false;
        }
    }

    static void ensureDefaults(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        try
        {
            Files.createDirectories(dataFolder);
        }
        catch (IOException e)
        {
            logger.warn("Could not create the Fabric data folder {}.", dataFolder, e);
            return;
        }

        Path configFile = dataFolder.resolve("config.yml");
        try
        {
            boolean updated = ensureConfigDefaults(configFile);
            if (updated && Files.exists(configFile))
            {
                logger.info("Added missing Fabric defaults to {} without replacing existing values.", configFile);
            }
        }
        catch (IOException | ClaimBlockConfigException e)
        {
            logger.warn("Could not initialize or update Fabric config defaults in {}.", configFile, e);
        }

        Path messagesFile = dataFolder.resolve("messages.yml");
        try
        {
            ensureFileDefaults(messagesFile, DEFAULT_MESSAGES);
        }
        catch (IOException e)
        {
            logger.warn("Could not initialize or update Fabric message defaults in {}.", messagesFile, e);
        }
    }

    private static boolean ensureConfigDefaults(@NotNull Path file)
            throws IOException, ClaimBlockConfigException
    {
        if (!Files.exists(file))
        {
            Files.writeString(file, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            return false;
        }

        String existing = Files.readString(file, StandardCharsets.UTF_8);
        ClaimBlockSettings effectiveSettings = CLAIM_BLOCK_CONFIG_CODEC.decode(existing);
        return mergeAndWrite(file, existing, defaultConfig(effectiveSettings));
    }

    private static boolean ensureFileDefaults(@NotNull Path file, @NotNull String defaults)
            throws IOException
    {
        if (!Files.exists(file))
        {
            Files.writeString(file, defaults, StandardCharsets.UTF_8);
            return false;
        }

        String existing = Files.readString(file, StandardCharsets.UTF_8);
        return mergeAndWrite(file, existing, defaults);
    }

    private static boolean mergeAndWrite(
            @NotNull Path file,
            @NotNull String existing,
            @NotNull String defaults)
            throws IOException
    {
        String merged = YamlDefaultsUpdater.mergeMissing(existing, defaults);
        if (merged.equals(existing))
        {
            return false;
        }
        writeAtomically(file, merged);
        return true;
    }

    private static void writeAtomically(@NotNull Path target, @NotNull String contents)
            throws IOException
    {
        Path writeTarget = Files.isSymbolicLink(target) ? target.toRealPath() : target;
        Path parent = writeTarget.getParent();
        Path temporary = Files.createTempFile(parent, "." + writeTarget.getFileName(), ".tmp");
        try
        {
            Files.writeString(
                    temporary,
                    contents,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try
            {
                Files.move(
                        temporary,
                        writeTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            catch (AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, writeTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    private static @NotNull String defaultConfig(@NotNull ClaimBlockSettings settings)
    {
        return DEFAULT_CONFIG_TEMPLATE.formatted(
                settings.initialBlocks(),
                settings.blocksAccruedPerHour(),
                settings.maximumAccruedClaimBlocks(),
                settings.accruedIdleThreshold(),
                settings.accruedIdlePercent(),
                settings.maximumClaimsPerPlayer(),
                Double.toString(settings.abandonReturnRatio())
        );
    }
}
