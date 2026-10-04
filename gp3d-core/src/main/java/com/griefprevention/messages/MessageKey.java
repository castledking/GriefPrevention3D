package com.griefprevention.messages;

import org.jetbrains.annotations.NotNull;

/**
 * Message keys shared with the Paper plugin's {@code Messages} enum.
 *
 * <p>The key name and default value must match the Paper entry exactly so a single {@code messages.yml}
 * serves both platforms.
 */
public enum MessageKey
{
    NO_BUILD_PERMISSION("NoBuildPermission", "You don't have {0}'s permission to build here."),
    NO_ACCESS_PERMISSION("NoAccessPermission", "You don't have {0}'s permission to use that."),
    NO_CONTAINERS_PERMISSION("NoContainersPermission", "You don't have {0}'s permission to use that."),
    NO_MANAGE_TRUST("NoManageTrust", "You don't have {0}'s permission to manage permissions here."),
    ONLY_OWNERS_MODIFY_CLAIMS("OnlyOwnersModifyClaims", "Only {0} can modify this claim."),
    OWNER_NAME_FOR_ADMIN_CLAIMS("OwnerNameForAdminClaims", "an administrator"),
    NOT_YOUR_CLAIM("NotYourClaim", "This isn't your claim."),
    CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT(
            "ClaimCreationFailedOverClaimCountLimit",
            "You've reached your limit on land claims.  Use /abandonclaim to remove one before creating another."),
    NEW_CLAIM_TOO_NARROW(
            "NewClaimTooNarrow",
            "This claim would be too small.  Any claim must be at least {0} blocks wide."),
    CREATE_CLAIM_INSUFFICIENT_BLOCKS(
            "CreateClaimInsufficientBlocks",
            "You don't have enough blocks to claim that entire area.  You need {0} more blocks."),
    CREATE_CLAIM_FAIL_OVERLAP_SHORT(
            "CreateClaimFailOverlapShort",
            "Your selected area overlaps an existing claim."),
    RESIZE_CLAIM_TOO_NARROW(
            "ResizeClaimTooNarrow",
            "This new size would be too small.  Claims must be at least {0} blocks wide."),
    RESIZE_NEED_MORE_BLOCKS(
            "ResizeNeedMoreBlocks",
            "You don't have enough blocks for this size.  You need {0} more."),
    RESIZE_FAIL_OVERLAP(
            "ResizeFailOverlap",
            "Can't resize here because it would overlap another nearby claim."),
    PVP_TOGGLE_NOT_ENABLED(
            "PvpToggleNotEnabled",
            "PvP toggle commands are not enabled."),
    PVP_TOGGLE_NOT_ENABLED_FOR_CLAIM_TYPE(
            "PvpToggleNotEnabledForClaimType",
            "PvP cannot be toggled in this type of claim."),
    PVP_TOGGLE_USAGE(
            "PvpToggleUsage",
            "Usage: /claim pvp [true|false|on|off] [confirm]"),
    PVP_TOGGLE_ALREADY_ENABLED(
            "PvpToggleAlreadyEnabled",
            "PvP is already enabled in this {0}."),
    PVP_TOGGLE_ALREADY_DISABLED(
            "PvpToggleAlreadyDisabled",
            "PvP is already disabled in this {0}."),
    CONFIRM_PVP_TOGGLE_ENABLED_NO_FEE(
            "ConfirmPvpToggleEnabledNoFee",
            "Do you want to enable PvP in this {0}?"),
    CONFIRM_PVP_TOGGLE_DISABLED_NO_FEE(
            "ConfirmPvpToggleDisabledNoFee",
            "Do you want to disable PvP in this {0}?"),
    CONFIRM_PVP_TOGGLE_ENABLED_WITH_FEE(
            "ConfirmPvpToggleEnabledWithFee",
            "Do you want to pay {0} to enable PvP in this {1}?"),
    CONFIRM_PVP_TOGGLE_DISABLED_WITH_FEE(
            "ConfirmPvpToggleDisabledWithFee",
            "Do you want to pay {0} to disable PvP in this {1}?"),
    CONFIRM_PVP_TOGGLE_INSTRUCTION(
            "ConfirmPvpToggleInstruction",
            "Type /claim pvp {0} confirm or /claimpvpconfirm to confirm."),
    NO_PENDING_PVP_TOGGLE(
            "NoPendingPvpToggle",
            "No pending PvP toggle to confirm."),
    PENDING_PVP_TOGGLE_EXPIRED(
            "PendingPvpToggleExpired",
            "Your pending PvP toggle has expired. Please try again."),
    PVP_TOGGLE_ENABLED_WITH_FEE(
            "PvPToggleEnabledWithFee",
            "PvP enabled in this {0} for {1}."),
    PVP_TOGGLE_DISABLED_WITH_FEE(
            "PvPToggleDisabledWithFee",
            "PvP disabled in this {0} for {1}."),
    PVP_TOGGLE_ENABLED(
            "PvPToggleEnabled",
            "PvP enabled in this {0}."),
    PVP_TOGGLE_DISABLED(
            "PvPToggleDisabled",
            "PvP disabled in this {0}."),
    CLAIM_LABEL(
            "ClaimLabel",
            "claim"),
    SUBDIVISION_LABEL(
            "SubdivisionLabel",
            "subdivision"),
    NO_PERMISSION_FOR_COMMAND(
            "NoPermissionForCommand",
            "You don't have permission to do that."),
    COMMAND_REQUIRES_PLAYER("CommandRequiresPlayer", "This command can only be used by players."),
    COMMAND_NOT_FOUND("CommandNotFound", "Unknown subcommand: {0}"),
    PLAYER_NOT_FOUND_2("PlayerNotFound2", "No player by that name has logged in recently."),
    INVALID_PERMISSION_ID("InvalidPermissionID", "Please specify a player name, or a permission in [brackets]."),
    GRANT_PERMISSION_CONFIRMATION("GrantPermissionConfirmation", "Granted {0} permission to {1} {2}."),
    COLLECTIVE_PUBLIC("CollectivePublic", "the public"),
    BUILD_PERMISSION("BuildPermission", "build"),
    CONTAINERS_PERMISSION("ContainersPermission", "access containers and animals"),
    ACCESS_PERMISSION("AccessPermission", "use buttons and levers"),
    MANAGE_PERMISSION("ManagePermission", "manage permissions"),
    LOCATION_CURRENT_CLAIM("LocationCurrentClaim", "in this claim"),
    LOCATION_ALL_CLAIMS("LocationAllClaims", "in all your claims"),
    UNTRUST_INDIVIDUAL_ALL_CLAIMS(
            "UntrustIndividualAllClaims",
            "Revoked {0}'s access to ALL your claims.  To set permissions for a single claim, stand inside it."),
    UNTRUST_EVERYONE_ALL_CLAIMS(
            "UntrustEveryoneAllClaims",
            "Cleared permissions in ALL your claims.  To set permissions for a single claim, stand inside it."),
    CLEAR_PERMS_OWNER_ONLY("ClearPermsOwnerOnly", "Only the claim owner can clear all permissions."),
    UNTRUST_ALL_OWNER_ONLY("UntrustAllOwnerOnly", "Only the claim owner can clear all its permissions."),
    CLEAR_PERMISSIONS_ONE_CLAIM(
            "ClearPermissionsOneClaim",
            "Cleared permissions in this claim.  To set permission for ALL your claims, stand outside them."),
    MANAGERS_DONT_UNTRUST_MANAGERS("ManagersDontUntrustManagers", "Only the claim owner can demote a manager."),
    UNTRUST_INDIVIDUAL_SINGLE_CLAIM(
            "UntrustIndividualSingleClaim",
            "Revoked {0}'s access to this claim.  To set permissions for a ALL your claims, stand outside them."),
    TRUST_LIST_NO_CLAIM("TrustListNoClaim", "Stand inside the claim you're curious about."),
    TRUST_LIST_HEADER("TrustListHeader", "Explicit permissions here:"),
    MANAGE("Manage", "Manage"),
    BUILD("Build", "Build"),
    CONTAINERS("Containers", "Containers"),
    ACCESS("Access", "Access"),
    NEIGHBOR("Neighbor", "Neighbor"),
    HAS_SUBCLAIM_RESTRICTION("HasSubclaimRestriction", "This subclaim does not inherit permissions from the parent"),
    START_BLOCK_MATH("StartBlockMath", "{0} blocks from play + {1} bonus = {2} total."),
    CLAIMS_LIST_HEADER("ClaimsListHeader", "Claims:"),
    CONTINUE_BLOCK_MATH("ContinueBlockMath", " (-{0} blocks)"),
    END_BLOCK_MATH("EndBlockMath", " = {0} blocks left to spend"),
    CLAIMS_LIST_NO_PERMISSION(
            "ClaimsListNoPermission",
            "You don't have permission to get information about another player's land claims."),
    SUCCESSFUL_ABANDON("SuccessfulAbandon", "Claims abandoned.  You now have {0} available claim blocks."),
    DELETE_TOP_LEVEL_CLAIM(
            "DeleteTopLevelClaim",
            "To delete a subdivision, stand inside it.  Otherwise, use /abandontoplevelclaim to delete this claim and all subdivisions."),
    CONFIRM_ABANDON_ALL_CLAIMS(
            "ConfirmAbandonAllClaims",
            "Are you sure you want to abandon ALL of your claims?  Please confirm with /abandonallclaims confirm"),
    YOU_HAVE_NO_CLAIMS("YouHaveNoClaims", "You don't have any land claims."),
    ADJUST_BLOCKS_SUCCESS(
            "AdjustBlocksSuccess",
            "Adjusted {0}'s bonus claim blocks by {1}.  New total bonus blocks: {2}."),
    ADJUST_BLOCKS_ALL_SUCCESS("AdjustBlocksAllSuccess", "Adjusted all online players' bonus claim blocks by {0}."),
    ADJUST_GROUP_BLOCKS_SUCCESS(
            "AdjustGroupBlocksSuccess",
            "Adjusted bonus claim blocks for players with the {0} permission by {1}.  New total: {2}."),
    SET_CLAIM_BLOCKS_SUCCESS("SetClaimBlocksSuccess", "Updated accrued claim blocks."),
    ADMIN_CLAIMS_MODE(
            "AdminClaimsMode",
            "Administrative claims mode active.  Any claims created will be free and editable by other administrators."),
    BASIC_CLAIMS_MODE("BasicClaimsMode", "Returned to basic claim creation mode."),
    PLAYER_OFFLINE_TIME("PlayerOfflineTime", "  Last login: {0} days ago."),
    MINIMUM_RADIUS("MinimumRadius", "Minimum radius is {0}."),
    BLOCK_NOT_CLAIMED("BlockNotClaimed", "No one has claimed this block."),
    BLOCK_CLAIMED("BlockClaimed", "That block has been claimed by {0}."),
    SHOW_NEARBY_CLAIMS("ShowNearbyClaims", "Found {0} land claims."),
    TOO_FAR_AWAY("TooFarAway", "That's too far away."),
    CLAIMS_DISABLED_WORLD("ClaimsDisabledWorld", "Land claims are disabled in this world."),
    NO_CREATE_CLAIM_PERMISSION("NoCreateClaimPermission", "You don't have permission to claim land."),
    CLAIM_START(
            "ClaimStart",
            "Claim corner set!  Use the shovel again at the opposite corner to claim a rectangle of land.  To cancel, put your shovel away."),
    CREATE_CLAIM_SUCCESS("CreateClaimSuccess", "Claim created!  Use /trust to share it with friends."),
    CREATE_CLAIM_FAIL_OVERLAP(
            "CreateClaimFailOverlap",
            "You can't create a claim here because it would overlap your other claim.  Use /abandonclaim to delete it, or use your shovel at a corner to resize it."),
    CREATE_CLAIM_FAIL_OVERLAP_OTHER_PLAYER(
            "CreateClaimFailOverlapOtherPlayer",
            "You can't create a claim here because it would overlap {0}'s claim."),
    RESIZE_START("ResizeStart", "Resizing claim.  Use your shovel again at the new location for this corner."),
    CLAIM_RESIZE_SUCCESS("ClaimResizeSuccess", "Claim resized.  {0} available claim blocks remaining."),
    RESIZE_CLAIM_INSUFFICIENT_AREA(
            "ResizeClaimInsufficientArea",
            "This claim would be too small.  Any claim must use at least {0} total claim blocks."),
    REMAINING_BLOCKS("RemainingBlocks", "You may claim up to {0} more blocks."),
    ABANDON_SUCCESS("AbandonSuccess", "Claim abandoned.  You now have {0} available claim blocks."),
    NO_DAMAGE_CLAIMED_ENTITY("NoDamageClaimedEntity", "That belongs to {0}."),
    CANT_FIGHT_WHILE_IMMUNE("CantFightWhileImmune", "You can't fight someone while you're protected from PvP."),
    PLAYER_IN_PVP_SAFE_ZONE("PlayerInPvPSafeZone", "That player is in a PvP safe zone."),
    NO_PISTONS_OUTSIDE_CLAIMS("NoPistonsOutsideClaims", "Warning: Pistons won't move blocks outside land claims."),
    IGNORING_CLAIMS("IgnoringClaims", "Now ignoring claims."),
    RESPECTING_CLAIMS("RespectingClaims", "Now respecting claims."),
    DELETE_CLAIM_MISSING("DeleteClaimMissing", "There's no claim here."),
    DELETION_SUBDIVISION_WARNING(
            "DeletionSubdivisionWarning",
            "This claim includes subdivisions.  If you're sure you want to delete it, use /deleteclaim again."),
    DELETE_SUCCESS("DeleteSuccess", "Claim deleted."),
    CANT_DELETE_ADMIN_CLAIM("CantDeleteAdminClaim", "You don't have permission to delete administrative claims."),
    DELETE_ALL_SUCCESS("DeleteAllSuccess", "Deleted all of {0}'s claims."),
    ALL_ADMIN_DELETED("AllAdminDeleted", "Deleted all administrative claims."),
    WORLD_NOT_FOUND("WorldNotFound", "World not found."),
    CONVERT_CLAIM_MISSING("ConvertClaimMissing", "There's no claim here.  Stand in the claim you want to convert."),
    CONVERT_CLAIM_ALREADY_ADMIN("ConvertClaimAlreadyAdmin", "This claim is already an administrative claim."),
    CONVERT_CLAIM_ALREADY_BASIC("ConvertClaimAlreadyBasic", "This claim is already a basic claim."),
    CONVERT_CLAIM_ADMIN_SUCCESS("ConvertClaimAdminSuccess", "Claim converted to an administrative claim."),
    CONVERT_CLAIM_BASIC_SUCCESS("ConvertClaimBasicSuccess", "Administrative claim converted to a basic claim you own."),
    TRANSFER_TOP_LEVEL(
            "TransferTopLevel",
            "Only top level claims (not subdivisions) may be transferred.  Stand outside of the subdivision and try again."),
    TRANSFER_CLAIM_MISSING(
            "TransferClaimMissing",
            "There's no claim here.  Stand in the administrative claim you want to transfer."),
    TRANSFER_SUCCESS("TransferSuccess", "Claim transferred."),
    TRANSFER_CLAIM_PERMISSION("TransferClaimPermission", "That command requires the administrative claims permission."),
    TRANSFER_CLAIM_NOT_ENABLED("TransferClaimNotEnabled", "Giving claims to other players is not enabled on this server."),
    TRANSFER_CLAIM_NO_CLAIM("TransferClaimNoClaim", "There's no claim here.  Stand in the claim you want to give away."),
    TRANSFER_CLAIM_SELF("TransferClaimSelf", "You already own this claim."),
    TRANSFER_CLAIM_RECIPIENT_NEEDS_BLOCKS(
            "TransferClaimRecipientNeedsBlocks",
            "{0} doesn't have enough claim blocks for this claim.  They need {1} more."),
    TRANSFER_CLAIM_RECIPIENT_AT_LIMIT("TransferClaimRecipientAtLimit", "{0} already has as many claims as they are allowed."),
    CONFIRM_TRANSFER_CLAIM_NO_FEE("ConfirmTransferClaimNoFee", "Do you want to give this claim to {0}?"),
    CONFIRM_TRANSFER_CLAIM_INSTRUCTION("ConfirmTransferClaimInstruction", "Type /transferclaim {0} confirm to confirm."),
    TRANSFER_CLAIM_SUCCESS("TransferClaimSuccess", "Gave this claim to {0}."),
    TRANSFER_CLAIM_RECEIVED("TransferClaimReceived", "{0} gave you their claim at {1}."),
    ECONOMY_NO_VAULT("EconomyNoVault", "Economy support requires Vault plugin to be installed."),
    EXPLOSIVES_DISABLED(
            "ExplosivesDisabled",
            "This claim is now protected from explosions.  Use /claimexplosions again to disable."),
    EXPLOSIVES_ENABLED(
            "ExplosivesEnabled",
            "This claim is now vulnerable to explosions.  Use /claimexplosions again to re-enable protections."),
    WITHER_EXPLOSIONS_ENABLED(
            "WitherExplosionsEnabled",
            "This claim is now vulnerable to wither explosions.  Use /witherexplosions again to re-enable protections."),
    WITHER_EXPLOSIONS_DISABLED(
            "WitherExplosionsDisabled",
            "This claim is now protected from wither explosions.  Use /witherexplosions again to disable protections.");

    private final String key;
    private final String defaultValue;

    MessageKey(@NotNull String key, @NotNull String defaultValue)
    {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    /**
     * @return the key under the {@code Messages} root of {@code messages.yml}
     */
    public @NotNull String key()
    {
        return this.key;
    }

    /**
     * @return the Paper default used when the key is absent from {@code messages.yml}
     */
    public @NotNull String defaultValue()
    {
        return this.defaultValue;
    }
}
