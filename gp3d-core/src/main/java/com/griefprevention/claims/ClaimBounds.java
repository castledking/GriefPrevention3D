package com.griefprevention.claims;

import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Platform-neutral integer claim bounds.
 */
public final class ClaimBounds
{
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final @Nullable OrthogonalPolygon polygon;

    private ClaimBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            @Nullable OrthogonalPolygon polygon)
    {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.polygon = polygon;
    }

    public static @NotNull ClaimBounds rectangle(int x1, int y1, int z1, int x2, int y2, int z2)
    {
        return new ClaimBounds(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2),
                null
        );
    }

    public static @NotNull ClaimBounds shaped(@NotNull OrthogonalPolygon polygon, int y1, int y2)
    {
        return new ClaimBounds(
                polygon.minX(),
                Math.min(y1, y2),
                polygon.minZ(),
                polygon.maxX(),
                Math.max(y1, y2),
                polygon.maxZ(),
                polygon
        );
    }

    public int minX()
    {
        return this.minX;
    }

    public int minY()
    {
        return this.minY;
    }

    public int minZ()
    {
        return this.minZ;
    }

    public int maxX()
    {
        return this.maxX;
    }

    public int maxY()
    {
        return this.maxY;
    }

    public int maxZ()
    {
        return this.maxZ;
    }

    public @Nullable OrthogonalPolygon polygon()
    {
        return this.polygon;
    }

    public boolean isShaped()
    {
        return this.polygon != null;
    }

    public int xLength()
    {
        return this.maxX - this.minX + 1;
    }

    public int yHeight()
    {
        return this.maxY - this.minY + 1;
    }

    public int zLength()
    {
        return this.maxZ - this.minZ + 1;
    }

    public int area()
    {
        if (this.polygon != null)
        {
            return this.polygon.cellCount();
        }

        try
        {
            return Math.multiplyExact(xLength(), zLength());
        }
        catch (ArithmeticException e)
        {
            return Integer.MAX_VALUE;
        }
    }

    public boolean contains(int x, int y, int z, boolean ignoreY)
    {
        return containsColumn(x, z) && (ignoreY || (y >= this.minY && y <= this.maxY));
    }

    public boolean containsY(int y)
    {
        return y >= this.minY && y <= this.maxY;
    }

    public boolean containsColumn(int x, int z)
    {
        if (x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ)
        {
            return false;
        }

        if (this.polygon == null)
        {
            return true;
        }

        OrthogonalPoint2i blockPoint = new OrthogonalPoint2i(x, z);
        for (OrthogonalEdge2i edge : this.polygon.edges())
        {
            if (edge.containsPoint(blockPoint))
            {
                return true;
            }
        }

        return isInsidePolygon(blockPoint, this.polygon);
    }

    public boolean intersects(@NotNull ClaimBounds other, boolean ignoreY)
    {
        return this.minX <= other.maxX && this.maxX >= other.minX
                && (ignoreY || (this.minY <= other.maxY && this.maxY >= other.minY))
                && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    public boolean overlaps(@NotNull ClaimBounds other, boolean ignoreY)
    {
        if (!intersects(other, ignoreY))
        {
            return false;
        }

        if (ignoreY && (this.isShaped() || other.isShaped()))
        {
            return intersectsColumns(other);
        }

        return true;
    }

    private boolean intersectsColumns(@NotNull ClaimBounds other)
    {
        int overlapMinX = Math.max(this.minX, other.minX);
        int overlapMaxX = Math.min(this.maxX, other.maxX);
        int overlapMinZ = Math.max(this.minZ, other.minZ);
        int overlapMaxZ = Math.min(this.maxZ, other.maxZ);

        for (int x = overlapMinX; x <= overlapMaxX; x++)
        {
            for (int z = overlapMinZ; z <= overlapMaxZ; z++)
            {
                if (this.containsColumn(x, z) && other.containsColumn(x, z))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isInsidePolygon(@NotNull OrthogonalPoint2i point, @NotNull OrthogonalPolygon polygon)
    {
        double sampleX = point.x() + 0.5D;
        double sampleZ = point.z() + 0.5D;
        boolean inside = false;
        List<OrthogonalPoint2i> corners = polygon.corners();

        for (int i = 0, j = corners.size() - 1; i < corners.size(); j = i++)
        {
            OrthogonalPoint2i a = corners.get(i);
            OrthogonalPoint2i b = corners.get(j);
            boolean crosses = (a.z() > sampleZ) != (b.z() > sampleZ);
            if (!crosses)
            {
                continue;
            }

            double intersectionX = (double) (b.x() - a.x()) * (sampleZ - a.z()) / (double) (b.z() - a.z()) + a.x();
            if (sampleX < intersectionX)
            {
                inside = !inside;
            }
        }

        return inside;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimBounds)) return false;
        ClaimBounds that = (ClaimBounds) other;
        return this.minX == that.minX
                && this.minY == that.minY
                && this.minZ == that.minZ
                && this.maxX == that.maxX
                && this.maxY == that.maxY
                && this.maxZ == that.maxZ
                && Objects.equals(this.polygon, that.polygon);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, this.polygon);
    }

    @Override
    public String toString()
    {
        return "ClaimBounds[minX=" + this.minX
                + ", minY=" + this.minY
                + ", minZ=" + this.minZ
                + ", maxX=" + this.maxX
                + ", maxY=" + this.maxY
                + ", maxZ=" + this.maxZ
                + ", polygon=" + this.polygon
                + "]";
    }
}
