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
                Allow3DSubdivisions: true
                AllowShapedClaims: false
                AllowShapedSubClaims: false
                ShapedMinimumWidth: 1
                ShapedMinimumArea: 25
                AllowTrappedInAdminClaims: false
                EnderPearlsRequireAccessTrust: true
                RefundDeniedEnderPearls: true
                RaidTriggersRequireBuildTrust: true
                UseClaimSelectSessions: true
                UseClaimSelectedMessages: false
                FireSpreadsInClaims: false
                FireDamagesInClaims: false
                ProtectCreatures: true
                PreventTheft: true
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
                  AllClaims:
                    DaysInactive: 60
                    ExceptWhenOwnerHasTotalClaimBlocks: 10000
                    ExceptWhenOwnerHasBonusClaimBlocks: 5000
              Advanced:
                ClaimExpirationCheckRate: 60
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
              IgnoringClaims: "Now ignoring claims."
              RespectingClaims: "Now respecting claims."
              DeletionSubdivisionWarning: "This claim includes subdivisions.  If you're sure you want to delete it, use /deleteclaim again."
              CantDeleteAdminClaim: "You don't have permission to delete administrative claims."
              DeleteAllSuccess: "Deleted all of {0}'s claims."
              AllAdminDeleted: "Deleted all administrative claims."
              WorldNotFound: "World not found."
              ConvertClaimMissing: "There's no claim here.  Stand in the claim you want to convert."
              ConvertClaimAlreadyAdmin: "This claim is already an administrative claim."
              ConvertClaimAlreadyBasic: "This claim is already a basic claim."
              ConvertClaimAdminSuccess: "Claim converted to an administrative claim."
              ConvertClaimBasicSuccess: "Administrative claim converted to a basic claim you own."
              TransferTopLevel: "Only top level claims (not subdivisions) may be transferred.  Stand outside of the subdivision and try again."
              TransferClaimMissing: "There's no claim here.  Stand in the administrative claim you want to transfer."
              TransferSuccess: "Claim transferred."
              TransferClaimPermission: "That command requires the administrative claims permission."
              TransferClaimNotEnabled: "Giving claims to other players is not enabled on this server."
              TransferClaimNoClaim: "There's no claim here.  Stand in the claim you want to give away."
              TransferClaimSelf: "You already own this claim."
              TransferClaimRecipientNeedsBlocks: "{0} doesn't have enough claim blocks for this claim.  They need {1} more."
              TransferClaimRecipientAtLimit: "{0} already has as many claims as they are allowed."
              ConfirmTransferClaimNoFee: "Do you want to give this claim to {0}?"
              ConfirmTransferClaimInstruction: "Type /transferclaim {0} confirm to confirm."
              TransferClaimSuccess: "Gave this claim to {0}."
              TransferClaimReceived: "{0} gave you their claim at {1}."
              EconomyNoVault: "Economy support requires Vault plugin to be installed."
              ExplosivesDisabled: "This claim is now protected from explosions.  Use /claimexplosions again to disable."
              ExplosivesEnabled: "This claim is now vulnerable to explosions.  Use /claimexplosions again to re-enable protections."
              WitherExplosionsEnabled: "This claim is now vulnerable to wither explosions.  Use /witherexplosions again to re-enable protections."
              WitherExplosionsDisabled: "This claim is now protected from wither explosions.  Use /witherexplosions again to disable protections."
              SubdivisionMode: "Subdivision mode.  Use your shovel to create subdivisions in your existing claims.  Use /basicclaims to exit."
              SubdivisionMode3D: "3D Subdivision mode.  Use your shovel to create 3D subdivisions in your existing claims.  Use /basicclaims to exit."
              SubdivisionVideo2: "Click for Subdivision Help: {0}"
              SubdivisionStart: "Subdivision corner set!  Use your shovel at the location for the opposite corner of this new subdivision."
              SubdivisionSuccess: "Subdivision created!  Use /trust to share it with friends."
              CreateSubdivisionOverlap: "Your selected area overlaps another subdivision."
              CreateClaimFailOverlapRegion: "You can't claim all of this because you're not allowed to build here."
              ResizeFailOverlapSubdivision: "You can't create a subdivision here because it would overlap another subdivision.  Consider /abandonclaim to delete it, or use your shovel at a corner to resize it."
              ResizeFailSubdivisionExceedsParent: "Could not resize: Subdivision was not kept within the parent's confines on all sides causing a conflict zone appearance on the main claim."
              ResizeFailSubdivision: "Cannot resize claim: The new boundaries would exclude an existing subdivision. Please remove or move the subdivision first."
              Subdivisions3DDisabled: "3D subdivisions are disabled on this server."
              SubclaimRestricted: "This subclaim's permissions will no longer inherit from the parent claim"
              SubclaimUnrestricted: "This subclaim's permissions will now inherit from the parent claim"
              StandInSubclaim: "You need to be standing in a subclaim to restrict it"
              MainClaimSubdivisionInheritEnabled: "All subdivisions in this claim will inherit permissions."
              MainClaimSubdivisionInheritDisabled: "All subdivisions in this claim will NOT inherit permissions."
              AdminSubdivisionRestricted: "That subdivision is administered by staff.  Talk to staff if you need it changed."
              NotTrappedHere: "You can build here.  Save yourself."
              RescuePending: "If you stay put for 10 seconds, you'll be teleported out.  Please wait."
              RescueAbortedMoved: "You moved!  Rescue cancelled."
              TrappedWontWorkHere: "Sorry, unable to find a safe location to teleport you to.  Contact an admin."
              ClaimAlertsEnabled: "Alerts enabled for this claim."
              ClaimAlertsDisabled: "Alerts disabled for this claim."
              ClaimAlertsEnabledGlobal: "Alerts enabled for all claims."
              ClaimAlertsDisabledGlobal: "Alerts disabled for all claims."
              ClaimAlertsUsage: "Use /claimtogglealerts on|off to toggle alerts for ALL claims, or stand in a specific claim and try again."
              ExternalPistonBoundaryViolation: "&6Warning: A piston tried to push a block into your claim @ x:{0} y:{1} z:{2}!"
              InternalPistonBoundaryViolation: "&6Warning: A piston tried to push a block outside your claim @ x:{0} y:{1} z:{2}!"
              ExternalLiquidBoundaryViolation: "&6Warning: Liquid tried to flow into your claim @ x:{0} y:{1} z:{2}!"
              InternalLiquidBoundaryViolation: "&6Warning: Liquid tried to flow out of your claim @ x:{0} y:{1} z:{2}!"
              BoundaryViolationBurstSummary: "&6Warning: {0} {1} violations near your claim @ x:{2} y:{3} z:{4}!"
              ScrollResizeStart: "Scroll resize: look at a side, then scroll up to push it away from you or down to pull it toward you. Right-click to apply; left-click or drop (Q) to cancel."
              ScrollResizeStartCorner: "Scroll resize: look along a side at the corner you selected, then scroll up to push it away from you or down to pull it toward you. Right-click to apply; left-click or drop (Q) to cancel."
              ScrollResizeNeedTool: "Hold your claim tool to use scroll resize."
              ScrollResizeNoClaim: "Stand in a claim, or right-click one of its corners with your claim tool, then run /scrollresize."
              ScrollResizeSize: "{0} x {1}"
              ScrollResizeSize3D: "{0} x {1} x {2}"
              ScrollResizeBlocksLeft: "{0} ({1} claim blocks left)"
              ScrollResizeConflict: "That outline overlaps another claim or doesn't fit its parent or subdivisions. Scroll back or left-click to cancel."
              ScrollResizeNotEnoughBlocks: "You need {0} more claim blocks for that. Scroll back or left-click to cancel."
              ScrollResizeLimit: "That side can't move any further."
              ScrollResizeCancelled: "Scroll resize cancelled."
              ScrollResizeTimedOut: "Scroll resize cancelled after a minute without scrolling."
              ScrollResizeUnchanged: "Scroll resize closed without changes."
              ScrollResizeClaimGone: "That claim no longer exists."
              ShapedClaimsDisabled: "Shaped claims are disabled on this server."
              ShapedClaimsMode: "Shaped claim mode. Right-click to place corners in unclaimed land or to start and extend shaped edits from claim boundaries. Boundary clicks create segment markers as needed. Use /basicclaims to exit."
              ShapedClaimEditMessage: "{0}"
              ShapedClaimSuccess: "Shaped claim created! Use /trust to share it with friends."
              ShapedClaimResized: "Shaped claim resized."
              ShapedClaims2DOnly: "Shaped outlines can only be used on 2D claims."
              ShapedSubClaimsDisabled: "Shaped subdivisions are disabled on this server."
              ShapedSubdivisionNoParent: "Right-click inside a claim you can edit to start a shaped subdivision."
              ShapedSubdivisionOutsideParent: "Shaped subdivisions have to stay inside the claim you're subdividing."
              ShapedSubdivisionOn3DClaim: "Shaped subdivisions only work inside top-level 2D claims."
              ShapedSubdivisionSuccess: "Shaped subdivision created."
              MustHoldModificationToolForThat: "You must be holding a golden shovel to do that."
              StandInClaimToResize: "Stand inside the land claim you want to resize."
              ClaimsExtendToSky: "Land claims always extend to max build height."
              ClaimsExtendLimit: "That side cannot move any further."
              ClaimsAutoExtendDownward: "Land claims auto-extend deeper into the ground when you place blocks under them."
              SurvivalBasicsVideo2: "Click for Land Claim Help: {0}"
              ClaimExpiryPlayerHeading: "Claim expiration for {0}:"
              ClaimExpiryOwnerStatus: "Owner: {0} ({1})"
              ClaimExpiryOwnerOnline: "online"
              ClaimExpiryOwnerOffline: "offline"
              ClaimExpiryLastActive: "Last seen: {0} ago"
              ClaimExpiryLastActiveUnknown: "Last seen: unknown"
              ClaimExpiryExpiresIn: "Expires in: {0}"
              ClaimExpiryExpired: "Expired {0} ago"
              ClaimExpiryExempt: "This claim is exempt from expiration: {0}"
              ClaimExpiryNeverExpires: "This claim will never expire."
              ClaimExpiryNoClaim: "You are not standing in a claim."
              ClaimExpiryPlayerNoClaims: "{0} has no claims."
              ClaimExpiryPlayerNotFound: "Could not find player {0}."
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
