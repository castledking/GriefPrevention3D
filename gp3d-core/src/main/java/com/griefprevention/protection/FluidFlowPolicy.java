package com.griefprevention.protection;

import com.griefprevention.claims.ClaimSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongFunction;

/** Keeps water and lava from flowing into a claim from outside it, as Paper's BlockFromTo rule. */
public final class FluidFlowPolicy
{
    private FluidFlowPolicy()
    {
    }

    /**
     * Paper's documented flow matrix: anything may flow into the wilderness; nothing flows from the
     * wilderness into a claim; a claim flows into itself, into its own subdivisions unless they are
     * restricted, and into other top-level claims of the same owner; a subdivision flows only into
     * itself.
     *
     * <p>Paper's own implementation drifts from its matrix: it lets any top-level claim flow into an
     * unrestricted subdivision that touches its border, even another owner's, and lets a parent flow
     * into a restricted subdivision. This follows the matrix.
     *
     * @param from the claim the fluid flows from, or null for the wilderness
     * @param to the claim it would flow into, or null for the wilderness
     * @param toRestricted whether {@code to} is a subdivision that inherits nothing from its parent
     */
    public static boolean mayFlow(
            @Nullable ClaimSnapshot from,
            @Nullable ClaimSnapshot to,
            boolean toRestricted,
            @NotNull LongFunction<ClaimSnapshot> byId)
    {
        if (to == null)
        {
            return true;
        }
        if (from == null)
        {
            return false;
        }
        if (ClaimRelations.sameClaim(from, to))
        {
            return true;
        }

        boolean fromSubdivision = from.parentId() != null;
        if (to.parentId() != null)
        {
            return !fromSubdivision && !toRestricted && ClaimRelations.sameTree(from, to, byId);
        }
        return !fromSubdivision && ClaimRelations.sameOwner(from, to, byId);
    }
}
