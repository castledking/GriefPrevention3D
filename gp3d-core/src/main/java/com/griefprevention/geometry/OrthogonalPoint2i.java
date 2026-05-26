package com.griefprevention.geometry;

import java.util.Objects;

/**
 * An immutable integer-based point in the X/Z plane.
 */
public final class OrthogonalPoint2i
{
    private final int x;
    private final int z;

    public OrthogonalPoint2i(int x, int z)
    {
        this.x = x;
        this.z = z;
    }

    public int x()
    {
        return this.x;
    }

    public int z()
    {
        return this.z;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof OrthogonalPoint2i)) return false;
        OrthogonalPoint2i that = (OrthogonalPoint2i) other;
        return this.x == that.x && this.z == that.z;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.x, this.z);
    }

    @Override
    public String toString()
    {
        return "OrthogonalPoint2i[x=" + this.x + ", z=" + this.z + "]";
    }
}
