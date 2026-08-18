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
            "Usage: /claimpvp [true|false|on|off] [confirm]"),
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
            "Type /claimpvp {0} confirm to confirm."),
    NO_PENDING_PVP_TOGGLE(
            "NoPendingPvpToggle",
            "No pending PvP toggle to confirm."),
    PENDING_PVP_TOGGLE_EXPIRED(
            "PendingPvpToggleExpired",
            "Your pending PvP toggle has expired. Please try again."),
    PVP_TOGGLE_ENABLED_WITH_FEE(
            "PvPToggleEnabledWithFee",
            "PvP enabled in this {0}. Fee charged: {1}."),
    PVP_TOGGLE_DISABLED_WITH_FEE(
            "PvPToggleDisabledWithFee",
            "PvP disabled in this {0}. Fee charged: {1}."),
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
            "You don't have permission to use this command.");

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
