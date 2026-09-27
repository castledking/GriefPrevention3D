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
            # visualization, fire, fluid, piston and combat options.
            GriefPrevention:
              ConfigVersion: 1
              BlockLandClaimExplosions: true
              BlockSurfaceCreeperExplosions: true
              BlockSurfaceOtherExplosions: true
              PistonMovement: CLAIMS_ONLY
              EndermenMoveBlocks: false
              CreaturesTrampleCrops: false
              PvP:
                AllowContainerAccess: false
                AllowRespawnAnchor: false
                ProtectPlayersInLandClaims:
                  PlayerOwnedClaims: true
                  AdministrativeClaims: true
                  AdministrativeSubdivisions: true
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
                ProtectCreatures: true
                PvPToggle:
                  Claim:
                    Enabled: false
                  Subdivision:
                    Enabled: false
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
              ResizeFailOverlap: "Can't resize here because it would overlap another nearby claim."
              CreateClaimFailOverlapShort: "Your selected area overlaps an existing claim."
              ClaimCreationFailedOverClaimCountLimit: "You've reached your limit on land claims.  Use /abandonclaim to remove one before creating another."
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
              NoManageTrust: "You don't have {0}'s permission to manage permissions here."
              OwnerNameForAdminClaims: "an administrator"
              PlaceholderTrustLevelUnclaimed: "Unclaimed"
              PlaceholderTrustLevelOwner: "Owner"
              PlaceholderTrustLevelManager: "Manager"
              PlaceholderTrustLevelBuilder: "Builder"
              PlaceholderTrustLevelAccess: "Access"
              PlaceholderTrustLevelContainer: "Container"
              PlaceholderTrustLevelUntrusted: "Untrusted"
              PvpToggleNotEnabled: "PvP toggle commands are not enabled."
              PvpToggleNotEnabledForClaimType: "PvP cannot be toggled in this type of claim."
              PvpToggleUsage: "Usage: /claim pvp [true|false|on|off] [confirm]"
              PvpToggleAlreadyEnabled: "PvP is already enabled in this {0}."
              PvpToggleAlreadyDisabled: "PvP is already disabled in this {0}."
              ConfirmPvpToggleEnabledNoFee: "Do you want to enable PvP in this {0}?"
              ConfirmPvpToggleDisabledNoFee: "Do you want to disable PvP in this {0}?"
              ConfirmPvpToggleEnabledWithFee: "Do you want to pay {0} to enable PvP in this {1}?"
              ConfirmPvpToggleDisabledWithFee: "Do you want to pay {0} to disable PvP in this {1}?"
              ConfirmPvpToggleInstruction: "Type /claim pvp {0} confirm or /claimpvpconfirm to confirm."
              NoPendingPvpToggle: "No pending PvP toggle to confirm."
              PendingPvpToggleExpired: "Your pending PvP toggle has expired. Please try again."
              PvPToggleEnabledWithFee: "PvP enabled in this {0} for {1}."
              PvPToggleDisabledWithFee: "PvP disabled in this {0} for {1}."
              PvPToggleEnabled: "PvP enabled in this {0}."
              PvPToggleDisabled: "PvP disabled in this {0}."
              ClaimLabel: "claim"
              SubdivisionLabel: "subdivision"
              NoPermissionForCommand: "You don't have permission to do that."
              ShowNearbyClaims: "Found {0} land claims."
              TooFarAway: "That's too far away."
              ClaimsDisabledWorld: "Land claims are disabled in this world."
              CreateClaimFailOverlap: "You can't create a claim here because it would overlap your other claim.  Use /abandonclaim to delete it, or use your shovel at a corner to resize it."
              CreateClaimFailOverlapOtherPlayer: "You can't create a claim here because it would overlap {0}'s claim."
              ResizeClaimInsufficientArea: "This claim would be too small.  Any claim must use at least {0} total claim blocks."
              RemainingBlocks: "You may claim up to {0} more blocks."
              AbandonSuccess: "Claim abandoned.  You now have {0} available claim blocks."
              NoDamageClaimedEntity: "That belongs to {0}."
              CantFightWhileImmune: "You can't fight someone while you're protected from PvP."
              PlayerInPvPSafeZone: "That player is in a PvP safe zone."
              NoPistonsOutsideClaims: "Warning: Pistons won't move blocks outside land claims."
              CommandRequiresPlayer: "This command can only be used by players."
              CommandNotFound: "Unknown subcommand: {0}"
              PlayerNotFound2: "No player by that name has logged in recently."
              InvalidPermissionID: "Please specify a player name, or a permission in [brackets]."
              GrantPermissionConfirmation: "Granted {0} permission to {1} {2}."
              CollectivePublic: "the public"
              BuildPermission: "build"
              ContainersPermission: "access containers and animals"
              AccessPermission: "use buttons and levers"
              ManagePermission: "manage permissions"
              LocationCurrentClaim: "in this claim"
              LocationAllClaims: "in all your claims"
              UntrustIndividualAllClaims: "Revoked {0}'s access to ALL your claims.  To set permissions for a single claim, stand inside it."
              UntrustEveryoneAllClaims: "Cleared permissions in ALL your claims.  To set permissions for a single claim, stand inside it."
              ClearPermsOwnerOnly: "Only the claim owner can clear all permissions."
              UntrustAllOwnerOnly: "Only the claim owner can clear all its permissions."
              ClearPermissionsOneClaim: "Cleared permissions in this claim.  To set permission for ALL your claims, stand outside them."
              ManagersDontUntrustManagers: "Only the claim owner can demote a manager."
              UntrustIndividualSingleClaim: "Revoked {0}'s access to this claim.  To set permissions for a ALL your claims, stand outside them."
              TrustListNoClaim: "Stand inside the claim you're curious about."
              TrustListHeader: "Explicit permissions here:"
              Manage: "Manage"
              Build: "Build"
              Containers: "Containers"
              Access: "Access"
              Neighbor: "Neighbor"
              HasSubclaimRestriction: "This subclaim does not inherit permissions from the parent"
              StartBlockMath: "{0} blocks from play + {1} bonus = {2} total."
              ClaimsListHeader: "Claims:"
              ContinueBlockMath: " (-{0} blocks)"
              ClaimsListNoPermission: "You don't have permission to get information about another player's land claims."
              SuccessfulAbandon: "Claims abandoned.  You now have {0} available claim blocks."
              DeleteTopLevelClaim: "To delete a subdivision, stand inside it.  Otherwise, use /abandontoplevelclaim to delete this claim and all subdivisions."
              ConfirmAbandonAllClaims: "Are you sure you want to abandon ALL of your claims?  Please confirm with /abandonallclaims confirm"
              YouHaveNoClaims: "You don't have any land claims."
              AdjustBlocksSuccess: "Adjusted {0}'s bonus claim blocks by {1}.  New total bonus blocks: {2}."
              AdjustBlocksAllSuccess: "Adjusted all online players' bonus claim blocks by {0}."
              AdjustGroupBlocksSuccess: "Adjusted bonus claim blocks for players with the {0} permission by {1}.  New total: {2}."
              SetClaimBlocksSuccess: "Updated accrued claim blocks."
              AdminClaimsMode: "Administrative claims mode active.  Any claims created will be free and editable by other administrators."
              BasicClaimsMode: "Returned to basic claim creation mode."
              PlayerOfflineTime: "  Last login: {0} days ago."
              MinimumRadius: "Minimum radius is {0}."
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
