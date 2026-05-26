package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalPoint2i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An in-progress orthogonal path before it becomes a committed polygon edit.
 */
public final class ShapedPathDraft
{
    private final @Nullable Long claimId;
    private final List<OrthogonalPoint2i> points;
    private final @Nullable OrthogonalPoint2i snappedPreviewPoint;
    private final boolean closureReady;

    public ShapedPathDraft(
            @Nullable Long claimId,
            @NotNull List<OrthogonalPoint2i> points,
            @Nullable OrthogonalPoint2i snappedPreviewPoint,
            boolean closureReady
    )
    {
        this.claimId = claimId;
        this.points = List.copyOf(points);
        this.snappedPreviewPoint = snappedPreviewPoint;
        this.closureReady = closureReady;
    }

    public static @NotNull ShapedPathDraft empty(@Nullable Long claimId)
    {
        return new ShapedPathDraft(claimId, List.of(), null, false);
    }

    public @Nullable Long claimId()
    {
        return this.claimId;
    }

    public @NotNull List<OrthogonalPoint2i> points()
    {
        return this.points;
    }

    public @Nullable OrthogonalPoint2i snappedPreviewPoint()
    {
        return this.snappedPreviewPoint;
    }

    public boolean closureReady()
    {
        return this.closureReady;
    }

    public @NotNull ShapedPathDraft withAddedPoint(@NotNull OrthogonalPoint2i point)
    {
        List<OrthogonalPoint2i> updated = new ArrayList<>(this.points);
        updated.add(point);
        boolean closureReady = updated.size() >= 4 && updated.get(0).equals(point);
        return new ShapedPathDraft(this.claimId, updated, this.snappedPreviewPoint, closureReady);
    }

    public @NotNull ShapedPathDraft withSnappedPreview(@Nullable OrthogonalPoint2i previewPoint)
    {
        return new ShapedPathDraft(this.claimId, this.points, previewPoint, this.closureReady);
    }
}
