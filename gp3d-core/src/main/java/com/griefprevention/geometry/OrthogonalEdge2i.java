package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An axis-aligned edge between two points in the X/Z plane.
 */
public final class OrthogonalEdge2i
{
    private final @NotNull OrthogonalPoint2i start;
    private final @NotNull OrthogonalPoint2i end;

    public OrthogonalEdge2i(@NotNull OrthogonalPoint2i start, @NotNull OrthogonalPoint2i end)
    {
        this.start = start;
        this.end = end;
    }

    public @NotNull OrthogonalPoint2i start()
    {
        return this.start;
    }

    public @NotNull OrthogonalPoint2i end()
    {
        return this.end;
    }

    public boolean isHorizontal()
    {
        return start.z() == end.z() && start.x() != end.x();
    }

    public boolean isVertical()
    {
        return start.x() == end.x() && start.z() != end.z();
    }

    public boolean isOrthogonal()
    {
        return isHorizontal() || isVertical();
    }

    public int length()
    {
        return Math.abs(end.x() - start.x()) + Math.abs(end.z() - start.z());
    }

    public int minX()
    {
        return Math.min(start.x(), end.x());
    }

    public int maxX()
    {
        return Math.max(start.x(), end.x());
    }

    public int minZ()
    {
        return Math.min(start.z(), end.z());
    }

    public int maxZ()
    {
        return Math.max(start.z(), end.z());
    }

    public boolean containsPoint(@NotNull OrthogonalPoint2i point)
    {
        if (isHorizontal())
        {
            return point.z() == start.z() && point.x() >= minX() && point.x() <= maxX();
        }

        if (isVertical())
        {
            return point.x() == start.x() && point.z() >= minZ() && point.z() <= maxZ();
        }

        return false;
    }

    public boolean containsInteriorPoint(@NotNull OrthogonalPoint2i point)
    {
        return containsPoint(point) && !point.equals(start) && !point.equals(end);
    }

    public @NotNull OrthogonalDirection outwardDirectionForPositiveOffset()
    {
        if (isHorizontal())
        {
            return OrthogonalDirection.SOUTH;
        }

        if (isVertical())
        {
            return OrthogonalDirection.EAST;
        }

        throw new IllegalStateException("Non-orthogonal edges do not have a normal.");
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof OrthogonalEdge2i)) return false;
        OrthogonalEdge2i that = (OrthogonalEdge2i) other;
        return this.start.equals(that.start) && this.end.equals(that.end);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.start, this.end);
    }

    @Override
    public String toString()
    {
        return "OrthogonalEdge2i[start=" + this.start + ", end=" + this.end + "]";
    }
}
