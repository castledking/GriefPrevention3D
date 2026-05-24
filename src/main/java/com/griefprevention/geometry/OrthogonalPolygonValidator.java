package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OrthogonalPolygonValidator
{
    private OrthogonalPolygonValidator()
    {
    }

    static @NotNull OrthogonalPolygonValidationResult validatePath(@NotNull List<OrthogonalPoint2i> rawPath)
    {
        List<OrthogonalPoint2i> normalizedPath = normalizePath(rawPath);
        List<OrthogonalPolygonValidationIssue> issues = new ArrayList<>();

        if (normalizedPath.size() < 2)
        {
            issues.add(new OrthogonalPolygonValidationIssue(
                    OrthogonalPolygonValidationIssueType.TOO_FEW_POINTS,
                    "An orthogonal claim path needs at least 4 corners.",
                    null,
                    null,
                    null
            ));
            return new OrthogonalPolygonValidationResult(normalizedPath, issues, null);
        }

        boolean closed = normalizedPath.get(0).equals(normalizedPath.get(normalizedPath.size() - 1));
        if (!closed)
        {
            issues.add(new OrthogonalPolygonValidationIssue(
                    OrthogonalPolygonValidationIssueType.NOT_CLOSED,
                    "The path must end on its starting corner.",
                    normalizedPath.get(normalizedPath.size() - 1),
                    null,
                    null
            ));
        }

        List<OrthogonalPoint2i> corners = closed
                ? List.copyOf(normalizedPath.subList(0, normalizedPath.size() - 1))
                : List.copyOf(normalizedPath);

        if (corners.size() < 4)
        {
            issues.add(new OrthogonalPolygonValidationIssue(
                    OrthogonalPolygonValidationIssueType.TOO_FEW_POINTS,
                    "An orthogonal claim needs at least 4 corners.",
                    null,
                    null,
                    null
            ));
        }

        detectDuplicateCorners(corners, issues);
        List<OrthogonalEdge2i> edges = validateEdges(normalizedPath, issues, closed);

        if (closed && !edges.isEmpty())
        {
            detectSelfIntersections(edges, issues);
        }

        if (!issues.isEmpty())
        {
            return new OrthogonalPolygonValidationResult(normalizedPath, issues, null);
        }

        return new OrthogonalPolygonValidationResult(normalizedPath, issues, new OrthogonalPolygon(corners));
    }

    static @NotNull List<OrthogonalEdge2i> buildEdges(@NotNull List<OrthogonalPoint2i> corners)
    {
        List<OrthogonalEdge2i> edges = new ArrayList<>(corners.size());
        for (int i = 0; i < corners.size(); i++)
        {
            OrthogonalPoint2i start = corners.get(i);
            OrthogonalPoint2i end = corners.get((i + 1) % corners.size());
            edges.add(new OrthogonalEdge2i(start, end));
        }

        return List.copyOf(edges);
    }

    static @NotNull List<OrthogonalPoint2i> closePath(@NotNull List<OrthogonalPoint2i> corners)
    {
        List<OrthogonalPoint2i> closed = new ArrayList<>(corners.size() + 1);
        closed.addAll(corners);
        if (!corners.isEmpty())
        {
            closed.add(corners.get(0));
        }

        return List.copyOf(closed);
    }

    private static @NotNull List<OrthogonalPoint2i> normalizePath(@NotNull List<OrthogonalPoint2i> rawPath)
    {
        List<OrthogonalPoint2i> normalized = new ArrayList<>(rawPath.size());
        OrthogonalPoint2i previous = null;
        for (OrthogonalPoint2i point : rawPath)
        {
            if (point == null)
            {
                continue;
            }

            if (!point.equals(previous))
            {
                normalized.add(point);
                previous = point;
            }
        }

        return List.copyOf(normalized);
    }

    private static void detectDuplicateCorners(
            @NotNull List<OrthogonalPoint2i> corners,
            @NotNull List<OrthogonalPolygonValidationIssue> issues
    )
    {
        Set<OrthogonalPoint2i> seen = new HashSet<>();
        for (OrthogonalPoint2i corner : corners)
        {
            if (!seen.add(corner))
            {
                issues.add(new OrthogonalPolygonValidationIssue(
                        OrthogonalPolygonValidationIssueType.DUPLICATE_CORNER,
                        "The path revisits an existing corner before closing.",
                        corner,
                        null,
                        null
                ));
            }
        }
    }

    private static @NotNull List<OrthogonalEdge2i> validateEdges(
            @NotNull List<OrthogonalPoint2i> normalizedPath,
            @NotNull List<OrthogonalPolygonValidationIssue> issues,
            boolean closed
    )
    {
        int edgeCount = closed ? normalizedPath.size() - 1 : normalizedPath.size() - 1;
        List<OrthogonalEdge2i> edges = new ArrayList<>(Math.max(edgeCount, 0));
        for (int i = 0; i + 1 < normalizedPath.size(); i++)
        {
            OrthogonalEdge2i edge = new OrthogonalEdge2i(normalizedPath.get(i), normalizedPath.get(i + 1));
            edges.add(edge);

            if (edge.length() == 0)
            {
                issues.add(new OrthogonalPolygonValidationIssue(
                        OrthogonalPolygonValidationIssueType.ZERO_LENGTH_EDGE,
                        "The path contains a zero-length edge.",
                        edge.start(),
                        i,
                        null
                ));
                continue;
            }

            if (!edge.isOrthogonal())
            {
                issues.add(new OrthogonalPolygonValidationIssue(
                        OrthogonalPolygonValidationIssueType.NON_ORTHOGONAL_EDGE,
                        "Claim edges must stay aligned to the X or Z axis.",
                        edge.end(),
                        i,
                        null
                ));
            }
        }

        return List.copyOf(edges);
    }

    private static void detectSelfIntersections(
            @NotNull List<OrthogonalEdge2i> edges,
            @NotNull List<OrthogonalPolygonValidationIssue> issues
    )
    {
        for (int i = 0; i < edges.size(); i++)
        {
            for (int j = i + 1; j < edges.size(); j++)
            {
                if (areAdjacent(i, j, edges.size()))
                {
                    continue;
                }

                OrthogonalPoint2i intersectionPoint = intersect(edges.get(i), edges.get(j));
                if (intersectionPoint == null)
                {
                    continue;
                }

                issues.add(new OrthogonalPolygonValidationIssue(
                        OrthogonalPolygonValidationIssueType.SELF_INTERSECTION,
                        "The shaped path intersects itself.",
                        intersectionPoint,
                        i,
                        j
                ));
            }
        }
    }

    private static boolean areAdjacent(int first, int second, int edgeCount)
    {
        if (first == second)
        {
            return true;
        }

        if (Math.abs(first - second) == 1)
        {
            return true;
        }

        return first == 0 && second == edgeCount - 1;
    }

    private static @Nullable OrthogonalPoint2i intersect(
            @NotNull OrthogonalEdge2i first,
            @NotNull OrthogonalEdge2i second
    )
    {
        if (!first.isOrthogonal() || !second.isOrthogonal())
        {
            return null;
        }

        if (first.isHorizontal() && second.isVertical())
        {
            return intersects(first, second);
        }

        if (first.isVertical() && second.isHorizontal())
        {
            return intersects(second, first);
        }

        if (first.isHorizontal() && second.isHorizontal() && first.start().z() == second.start().z())
        {
            int overlapMin = Math.max(first.minX(), second.minX());
            int overlapMax = Math.min(first.maxX(), second.maxX());
            if (overlapMin <= overlapMax)
            {
                return new OrthogonalPoint2i(overlapMin, first.start().z());
            }
        }

        if (first.isVertical() && second.isVertical() && first.start().x() == second.start().x())
        {
            int overlapMin = Math.max(first.minZ(), second.minZ());
            int overlapMax = Math.min(first.maxZ(), second.maxZ());
            if (overlapMin <= overlapMax)
            {
                return new OrthogonalPoint2i(first.start().x(), overlapMin);
            }
        }

        return null;
    }

    private static @Nullable OrthogonalPoint2i intersects(
            @NotNull OrthogonalEdge2i horizontal,
            @NotNull OrthogonalEdge2i vertical
    )
    {
        int x = vertical.start().x();
        int z = horizontal.start().z();
        if (x < horizontal.minX() || x > horizontal.maxX())
        {
            return null;
        }

        if (z < vertical.minZ() || z > vertical.maxZ())
        {
            return null;
        }

        return new OrthogonalPoint2i(x, z);
    }
}
