package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalPoint2i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Collections;
import java.util.ArrayList;

/**
 * A source-agnostic request against the claim editor.
 */
public final class ClaimEditIntent
{
    private final @NotNull ClaimEditIntentType type;
    private final @NotNull ClaimEditSource source;
    private final @Nullable ClaimEditorMode mode;
    private final @Nullable Long claimId;
    private final @Nullable OrthogonalPoint2i point;
    private final @Nullable Integer amount;
    private final boolean holdingModificationTool;
    private final @NotNull List<OrthogonalPoint2i> selectedCells;

    public ClaimEditIntent(
            @NotNull ClaimEditIntentType type,
            @NotNull ClaimEditSource source,
            @Nullable ClaimEditorMode mode,
            @Nullable Long claimId,
            @Nullable OrthogonalPoint2i point,
            @Nullable Integer amount,
            boolean holdingModificationTool,
            @NotNull List<OrthogonalPoint2i> selectedCells)
    {
        this.type = type;
        this.source = source;
        this.mode = mode;
        this.claimId = claimId;
        this.point = point;
        this.amount = amount;
        this.holdingModificationTool = holdingModificationTool;
        this.selectedCells = Collections.unmodifiableList(new ArrayList<>(selectedCells));
    }

    public @NotNull ClaimEditIntentType type()
    {
        return this.type;
    }

    public @NotNull ClaimEditSource source()
    {
        return this.source;
    }

    public @Nullable ClaimEditorMode mode()
    {
        return this.mode;
    }

    public @Nullable Long claimId()
    {
        return this.claimId;
    }

    public @Nullable OrthogonalPoint2i point()
    {
        return this.point;
    }

    public @Nullable Integer amount()
    {
        return this.amount;
    }

    public boolean holdingModificationTool()
    {
        return this.holdingModificationTool;
    }

    public @NotNull List<OrthogonalPoint2i> selectedCells()
    {
        return this.selectedCells;
    }

    public static @NotNull ClaimEditIntent enterMode(@NotNull ClaimEditSource source, @NotNull ClaimEditorMode mode)
    {
        return new ClaimEditIntent(ClaimEditIntentType.ENTER_MODE, source, mode, null, null, null, false, Collections.emptyList());
    }

    public static @NotNull ClaimEditIntent exitMode(@NotNull ClaimEditSource source)
    {
        return new ClaimEditIntent(ClaimEditIntentType.EXIT_MODE, source, null, null, null, null, false, Collections.emptyList());
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimEditIntent)) return false;
        ClaimEditIntent that = (ClaimEditIntent) other;
        return this.holdingModificationTool == that.holdingModificationTool
                && this.type == that.type
                && this.source == that.source
                && this.mode == that.mode
                && Objects.equals(this.claimId, that.claimId)
                && Objects.equals(this.point, that.point)
                && Objects.equals(this.amount, that.amount)
                && this.selectedCells.equals(that.selectedCells);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.type, this.source, this.mode, this.claimId, this.point, this.amount,
                this.holdingModificationTool, this.selectedCells);
    }

    @Override
    public String toString()
    {
        return "ClaimEditIntent[type=" + this.type
                + ", source=" + this.source
                + ", mode=" + this.mode
                + ", claimId=" + this.claimId
                + ", point=" + this.point
                + ", amount=" + this.amount
                + ", holdingModificationTool=" + this.holdingModificationTool
                + ", selectedCells=" + this.selectedCells
                + "]";
    }
}
