package com.griefprevention.protection;

/** A block position, free of any platform's position type. */
public final class BlockPoint
{
    private final int x;
    private final int y;
    private final int z;

    public BlockPoint(int x, int y, int z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x()
    {
        return this.x;
    }

    public int y()
    {
        return this.y;
    }

    public int z()
    {
        return this.z;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof BlockPoint)) return false;
        BlockPoint that = (BlockPoint) other;
        return this.x == that.x && this.y == that.y && this.z == that.z;
    }

    @Override
    public int hashCode()
    {
        return 31 * (31 * this.x + this.y) + this.z;
    }

    @Override
    public String toString()
    {
        return this.x + "," + this.y + "," + this.z;
    }
}
