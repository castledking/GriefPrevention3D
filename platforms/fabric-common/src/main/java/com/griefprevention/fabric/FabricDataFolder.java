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
            # Only the options wired by the Fabric port are active right now; /gpreload rereads the claim tool,
            # visualization, fire, fluid, piston, combat, block-use, raid-trigger and claim expiration options.
            GriefPrevention:
              ConfigVersion: 1
              BlockLandClaimExplosions: true
              BlockSurfaceCreeperExplosions: true
              BlockSurfaceOtherExplosions: true
              PistonMovement: CLAIMS_ONLY
              EndermenMoveBlocks: false
              SilverfishBreakBlocks: false
              CreaturesTrampleCrops: false
              RabbitsEatCrops: true
              LimitTreeGrowth: false
              PvP:
                ProtectFreshSpawns: true
                AllowContainerAccess: false
                AllowRespawnAnchor: false
                ProtectPlayersInLandClaims:
                  PlayerOwnedClaims: true
                  AdministrativeClaims: true
                  AdministrativeSubdivisions: true
              ProtectItemsDroppedOnDeath:
                PvPWorlds: false
                NonPvPWorlds: true
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
                Allow3DSubdivisions: true
                AllowShapedClaims: false
                AllowShapedSubClaims: false
                ShapedMinimumWidth: 1
                ShapedMinimumArea: 25
                AllowTrappedInAdminClaims: false
                EnderPearlsRequireAccessTrust: true
                RefundDeniedEnderPearls: true
                RaidTriggersRequireBuildTrust: true
                PreventCushionMount: false
                HoppersRequireBuildTrust: false
                UseClaimSelectSessions: true
                UseClaimSelectedMessages: false
                FireSpreadsInClaims: false
                FireDamagesInClaims: false
                ProtectCreatures: true
                RavagersBreakBlocks: true
                PreventTheft: true
                VehiclesRequireBuildTrust: false
                PreventBlockPlaceClimbing: false
                LockWoodenDoors: false
                LockTrapDoors: false
                LockFenceGates: true
                PreventButtonsSwitches: true
                LecternReadingRequiresAccessTrust: true
                TransferClaim:
                  Enabled: false
                  Price: 0.0
                PvPToggle:
                  Claim:
                    Enabled: false
                  Subdivision:
                    Enabled: false
                AutomaticNewPlayerClaimsRadius: 4
                Expiration:
                  ChestClaimDays: 7
                  UnusedClaimDays: 0
                  AllClaims:
                    DaysInactive: 60
                    ExceptWhenOwnerHasTotalClaimBlocks: 10000
                    ExceptWhenOwnerHasBonusClaimBlocks: 5000
                  AutomaticNatureRestoration:
                    SurvivalWorlds: false
              Advanced:
                ClaimExpirationCheckRate: 60
              Economy:
                ClaimBlocksEnabled: false
                ClaimBlocksMaxBonus: 0
                ClaimBlocksPurchaseCost: 1.0
                ClaimBlocksSellValue: 0.5
              VisualizationGlow: false
              FireSpreads: false
              FireDestroys: false
            """;
    private static final String DEFAULT_CONFIG = defaultConfig(ClaimBlockSettings.upstreamDefaults());

    private static final String DEFAULT_MESSAGES = defaultMessages();

    /** Generate valid YAML from the shared defaults so regex escapes and new keys cannot drift. */
    private static String defaultMessages()
    {
        StringBuilder yaml = new StringBuilder("# GriefPrevention3D Fabric message defaults.\nMessages:\n");
        for (com.griefprevention.messages.MessageKey key : com.griefprevention.messages.MessageKey.values())
        {
            String text = key.defaultValue().replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
            yaml.append("  ").append(key.key()).append(": \"").append(text).append("\"\n");
        }
        for (String level : new String[] {"Unclaimed", "Owner", "Manager", "Builder", "Access", "Container", "Untrusted"})
        {
            yaml.append("  PlaceholderTrustLevel").append(level).append(": \"").append(level).append("\"\n");
        }
        return yaml.toString();
    }

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
