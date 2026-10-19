package com.griefprevention.fabric;

import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Positions one block in from each end of a shaped boundary edge, matching Paper's corner nib markers. */
final class ShapedBoundaryMarkers
{
    private ShapedBoundaryMarkers()
    {
    }

    static @NotNull List<OrthogonalPoint2i> cornerNibs(@NotNull OrthogonalPolygon polygon)
    {
        Set<OrthogonalPoint2i> nibs = new LinkedHashSet<>();
        for (OrthogonalEdge2i edge : polygon.edges())
        {
            addCornerNibs(nibs, edge.start(), edge.end());
        }
        return List.copyOf(nibs);
    }

    /** The open path's existing segments only; it does not connect the last point back to the first. */
    static @NotNull List<OrthogonalPoint2i> cornerNibs(@NotNull List<OrthogonalPoint2i> path)
    {
        Set<OrthogonalPoint2i> nibs = new LinkedHashSet<>();
        for (int i = 1; i < path.size(); i++)
        {
            addCornerNibs(nibs, path.get(i - 1), path.get(i));
        }
        return List.copyOf(nibs);
    }

    private static void addCornerNibs(
            @NotNull Set<OrthogonalPoint2i> nibs,
            @NotNull OrthogonalPoint2i first,
            @NotNull OrthogonalPoint2i second)
    {
        if (first.z() == second.z())
        {
            int minX = Math.min(first.x(), second.x());
            int maxX = Math.max(first.x(), second.x());
            if (maxX - minX > 1)
            {
                nibs.add(new OrthogonalPoint2i(minX + 1, first.z()));
                nibs.add(new OrthogonalPoint2i(maxX - 1, first.z()));
            }
        }
        else if (first.x() == second.x())
        {
            int minZ = Math.min(first.z(), second.z());
            int maxZ = Math.max(first.z(), second.z());
            if (maxZ - minZ > 1)
            {
                nibs.add(new OrthogonalPoint2i(first.x(), minZ + 1));
                nibs.add(new OrthogonalPoint2i(first.x(), maxZ - 1));
            }
        }
    }
}
