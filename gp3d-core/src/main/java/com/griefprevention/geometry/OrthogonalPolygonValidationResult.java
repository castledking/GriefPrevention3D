package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Validation output for a proposed orthogonal polygon path.
 */
public final class OrthogonalPolygonValidationResult
{
    private final List<OrthogonalPoint2i> normalizedPath;
    private final List<OrthogonalPolygonValidationIssue> issues;
    private final OrthogonalPolygon polygon;

    OrthogonalPolygonValidationResult(
            @NotNull List<OrthogonalPoint2i> normalizedPath,
            @NotNull List<OrthogonalPolygonValidationIssue> issues,
            @Nullable OrthogonalPolygon polygon
    )
    {
        this.normalizedPath = Collections.unmodifiableList(new ArrayList<>(normalizedPath));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        this.polygon = polygon;
    }

    public @NotNull List<OrthogonalPoint2i> normalizedPath()
    {
        return this.normalizedPath;
    }

    public @NotNull List<OrthogonalPolygonValidationIssue> issues()
    {
        return this.issues;
    }

    public boolean isValid()
    {
        return this.polygon != null;
    }

    public @Nullable OrthogonalPolygon polygon()
    {
        return this.polygon;
    }
}
