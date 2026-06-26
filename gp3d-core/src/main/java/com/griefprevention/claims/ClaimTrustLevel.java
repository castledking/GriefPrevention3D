package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral claim trust level.
 */
public enum ClaimTrustLevel
{
    /**
     * Owner-only claim editing.
     */
    EDIT(0),
    /**
     * Permission management. Also grants build, container, and access.
     */
    MANAGE(1),
    /**
     * Build and break permission. Also grants container and access.
     */
    BUILD(2),
    /**
     * Container and inventory interaction permission. Also grants access.
     */
    CONTAINER(3),
    /**
     * Basic access permission.
     */
    ACCESS(4),
    /**
     * Neighbor trust permission. Allows bypassing minimum distance checks for claim creation.
     */
    NEIGHBOR(5);

    private final int trustLevel;

    ClaimTrustLevel(int trustLevel)
    {
        this.trustLevel = trustLevel;
    }

    public boolean isGrantedBy(@Nullable ClaimTrustLevel other)
    {
        return other != null && other.trustLevel <= this.trustLevel;
    }

    public @NotNull String denySuffix()
    {
        switch (this)
        {
            case MANAGE:
                return "#manager";
            case BUILD:
                return "#build";
            case CONTAINER:
                return "#inventory";
            case ACCESS:
                return "#access";
            case NEIGHBOR:
                return "#neighbor";
            case EDIT:
                return "";
            default:
                throw new IllegalStateException("Unknown claim trust level: " + this);
        }
    }
}
