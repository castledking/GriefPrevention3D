package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalDirection;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An editable subsection of a parent-claim boundary.
 */
public final class SegmentSelection
{
    private final long claimId;
    private final int edgeIndex;
    private final @Nullable Integer startNodeIndex;
    private final @Nullable Integer endNodeIndex;
    private final @Nullable OrthogonalDirection outwardDirection;

    public SegmentSelection(
            long claimId,
            int edgeIndex,
            @Nullable Integer startNodeIndex,
            @Nullable Integer endNodeIndex,
            @Nullable OrthogonalDirection outwardDirection)
    {
        this.claimId = claimId;
        this.edgeIndex = edgeIndex;
        this.startNodeIndex = startNodeIndex;
        this.endNodeIndex = endNodeIndex;
        this.outwardDirection = outwardDirection;
    }

    public long claimId()
    {
        return this.claimId;
    }

    public int edgeIndex()
    {
        return this.edgeIndex;
    }

    public @Nullable Integer startNodeIndex()
    {
        return this.startNodeIndex;
    }

    public @Nullable Integer endNodeIndex()
    {
        return this.endNodeIndex;
    }

    public @Nullable OrthogonalDirection outwardDirection()
    {
        return this.outwardDirection;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof SegmentSelection)) return false;
        SegmentSelection that = (SegmentSelection) other;
        return this.claimId == that.claimId
                && this.edgeIndex == that.edgeIndex
                && Objects.equals(this.startNodeIndex, that.startNodeIndex)
                && Objects.equals(this.endNodeIndex, that.endNodeIndex)
                && this.outwardDirection == that.outwardDirection;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.claimId, this.edgeIndex, this.startNodeIndex, this.endNodeIndex, this.outwardDirection);
    }

    @Override
    public String toString()
    {
        return "SegmentSelection[claimId=" + this.claimId
                + ", edgeIndex=" + this.edgeIndex
                + ", startNodeIndex=" + this.startNodeIndex
                + ", endNodeIndex=" + this.endNodeIndex
                + ", outwardDirection=" + this.outwardDirection
                + "]";
    }
}
