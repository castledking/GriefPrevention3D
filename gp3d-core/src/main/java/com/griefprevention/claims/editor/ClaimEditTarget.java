package com.griefprevention.claims.editor;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Identifies the current claim editing target.
 */
public final class ClaimEditTarget
{
    private final ClaimEditTargetType type;
    private final @Nullable Long claimId;

    public ClaimEditTarget(ClaimEditTargetType type, @Nullable Long claimId)
    {
        this.type = type;
        this.claimId = claimId;
    }

    public ClaimEditTargetType type()
    {
        return this.type;
    }

    public @Nullable Long claimId()
    {
        return this.claimId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimEditTarget)) return false;
        ClaimEditTarget that = (ClaimEditTarget) other;
        return this.type == that.type && Objects.equals(this.claimId, that.claimId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.type, this.claimId);
    }

    @Override
    public String toString()
    {
        return "ClaimEditTarget[type=" + this.type + ", claimId=" + this.claimId + "]";
    }
}
