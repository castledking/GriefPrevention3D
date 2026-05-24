package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A single validation issue found in a proposed orthogonal polygon.
 */
public final class OrthogonalPolygonValidationIssue
{
    private final @NotNull OrthogonalPolygonValidationIssueType type;
    private final @NotNull String message;
    private final @Nullable OrthogonalPoint2i point;
    private final @Nullable Integer firstEdgeIndex;
    private final @Nullable Integer secondEdgeIndex;

    public OrthogonalPolygonValidationIssue(
            @NotNull OrthogonalPolygonValidationIssueType type,
            @NotNull String message,
            @Nullable OrthogonalPoint2i point,
            @Nullable Integer firstEdgeIndex,
            @Nullable Integer secondEdgeIndex)
    {
        this.type = type;
        this.message = message;
        this.point = point;
        this.firstEdgeIndex = firstEdgeIndex;
        this.secondEdgeIndex = secondEdgeIndex;
    }

    public @NotNull OrthogonalPolygonValidationIssueType type()
    {
        return this.type;
    }

    public @NotNull String message()
    {
        return this.message;
    }

    public @Nullable OrthogonalPoint2i point()
    {
        return this.point;
    }

    public @Nullable Integer firstEdgeIndex()
    {
        return this.firstEdgeIndex;
    }

    public @Nullable Integer secondEdgeIndex()
    {
        return this.secondEdgeIndex;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof OrthogonalPolygonValidationIssue)) return false;
        OrthogonalPolygonValidationIssue that = (OrthogonalPolygonValidationIssue) other;
        return this.type == that.type
                && this.message.equals(that.message)
                && Objects.equals(this.point, that.point)
                && Objects.equals(this.firstEdgeIndex, that.firstEdgeIndex)
                && Objects.equals(this.secondEdgeIndex, that.secondEdgeIndex);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.type, this.message, this.point, this.firstEdgeIndex, this.secondEdgeIndex);
    }

    @Override
    public String toString()
    {
        return "OrthogonalPolygonValidationIssue[type=" + this.type
                + ", message=" + this.message
                + ", point=" + this.point
                + ", firstEdgeIndex=" + this.firstEdgeIndex
                + ", secondEdgeIndex=" + this.secondEdgeIndex
                + "]";
    }
}
