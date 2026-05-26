package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.geometry.OrthogonalPolygonValidationIssue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A non-committed result to visualize or describe back to the player.
 */
public final class ClaimEditPreview
{
    private final @Nullable OrthogonalPolygon polygon;
    private final @Nullable SegmentSelection highlightedSegment;
    private final @NotNull List<OrthogonalPoint2i> draftPoints;
    private final @Nullable OrthogonalPoint2i snappedPoint;
    private final @NotNull List<OrthogonalPoint2i> conflictPoints;
    private final @NotNull List<OrthogonalPolygonValidationIssue> issues;
    private final @NotNull List<String> messages;

    public ClaimEditPreview(
            @Nullable OrthogonalPolygon polygon,
            @Nullable SegmentSelection highlightedSegment,
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @Nullable OrthogonalPoint2i snappedPoint,
            @NotNull List<OrthogonalPoint2i> conflictPoints,
            @NotNull List<OrthogonalPolygonValidationIssue> issues,
            @NotNull List<String> messages)
    {
        this.polygon = polygon;
        this.highlightedSegment = highlightedSegment;
        this.draftPoints = List.copyOf(draftPoints);
        this.snappedPoint = snappedPoint;
        this.conflictPoints = List.copyOf(conflictPoints);
        this.issues = List.copyOf(issues);
        this.messages = List.copyOf(messages);
    }

    public @Nullable OrthogonalPolygon polygon()
    {
        return this.polygon;
    }

    public @Nullable SegmentSelection highlightedSegment()
    {
        return this.highlightedSegment;
    }

    public @NotNull List<OrthogonalPoint2i> draftPoints()
    {
        return this.draftPoints;
    }

    public @Nullable OrthogonalPoint2i snappedPoint()
    {
        return this.snappedPoint;
    }

    public @NotNull List<OrthogonalPoint2i> conflictPoints()
    {
        return this.conflictPoints;
    }

    public @NotNull List<OrthogonalPolygonValidationIssue> issues()
    {
        return this.issues;
    }

    public @NotNull List<String> messages()
    {
        return this.messages;
    }

    public static @NotNull ClaimEditPreview empty()
    {
        return new ClaimEditPreview(null, null, List.of(), null, List.of(), List.of(), List.of());
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimEditPreview)) return false;
        ClaimEditPreview that = (ClaimEditPreview) other;
        return Objects.equals(this.polygon, that.polygon)
                && Objects.equals(this.highlightedSegment, that.highlightedSegment)
                && this.draftPoints.equals(that.draftPoints)
                && Objects.equals(this.snappedPoint, that.snappedPoint)
                && this.conflictPoints.equals(that.conflictPoints)
                && this.issues.equals(that.issues)
                && this.messages.equals(that.messages);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.polygon, this.highlightedSegment, this.draftPoints, this.snappedPoint,
                this.conflictPoints, this.issues, this.messages);
    }

    @Override
    public String toString()
    {
        return "ClaimEditPreview[polygon=" + this.polygon
                + ", highlightedSegment=" + this.highlightedSegment
                + ", draftPoints=" + this.draftPoints
                + ", snappedPoint=" + this.snappedPoint
                + ", conflictPoints=" + this.conflictPoints
                + ", issues=" + this.issues
                + ", messages=" + this.messages
                + "]";
    }
}
