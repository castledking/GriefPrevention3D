package com.griefprevention.protection;

import com.griefprevention.claims.ClaimOwnership;
import com.griefprevention.claims.ClaimSnapshot;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongFunction;

/** Decides which claims protect the players inside them from other players, as Paper does. */
public final class PvpProtectionPolicy
{
    private PvpProtectionPolicy()
    {
    }

    /**
     * Paper's {@code claimIsPvPSafeZone}. When /claimpvp is enabled for the claim's type and the
     * claim was explicitly toggled, its own setting decides, in both directions. Every other claim
     * follows the ProtectPlayersInLandClaims settings for its type.
     *
     * @param pvpEnabled the claim's stored PvP setting
     * @param pvpToggled whether PvP was explicitly toggled for the claim; a stored "off" always was
     */
    public static boolean isSafeZone(
            @NotNull WorldProtectionSettings settings,
            @NotNull ClaimSnapshot claim,
            boolean pvpEnabled,
            boolean pvpToggled,
            @NotNull LongFunction<ClaimSnapshot> byId)
    {
        boolean topLevel = claim.parentId() == null;
        boolean toggleEnabled = topLevel ? settings.pvpToggleForClaims() : settings.pvpToggleForSubdivisions();
        if (toggleEnabled && (pvpToggled || !pvpEnabled))
        {
            return !pvpEnabled;
        }

        boolean admin = ClaimOwnership.effectiveOwnerId(claim, byId) == null;
        if (admin)
        {
            return topLevel ? settings.noCombatInAdminClaims() : settings.noCombatInAdminSubdivisions();
        }
        return settings.noCombatInPlayerClaims();
    }
}
