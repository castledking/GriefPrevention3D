package com.griefprevention.claims;

/** Permission nodes shared by the Bukkit and Fabric trust command adapters. */
public final class ClaimTrustCommandPermissions
{
    public static final String ADMIN_CLAIMS = "griefprevention.adminclaims";

    /**
     * Allows running the manage trust command ({@code /managetrust}), which grants a <em>player</em>
     * the right to hand out trust in a claim. Not the same node as {@link #PERMISSION_TRUST}.
     */
    public static final String MANAGE_TRUST = "griefprevention.managetrust";

    /**
     * Allows targeting a {@code [permission.node]} instead of a player when granting trust
     * ({@code /permissiontrust}, {@code /aclaim trust permission}). This is about <em>who can be
     * trusted</em>, not about which trust level is handed out, so it gates every trust level.
     * Staff-only by default. Not the same node as {@link #MANAGE_TRUST}.
     */
    public static final String PERMISSION_TRUST = "griefprevention.permissiontrust";

    private ClaimTrustCommandPermissions()
    {
    }
}
