package com.griefprevention.protection;

import com.griefprevention.claims.ClaimSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongFunction;

/** Paper's fire rules: fire neither spreads nor destroys unless configured to, and never across owners. */
public final class FirePolicy
{
    private FirePolicy()
    {
    }

    /**
     * @param fireClaim the claim of the burning fire block, or null
     * @param targetClaim the claim fire would spread into, or null
     */
    public static boolean maySpread(
            @NotNull WorldProtectionSettings settings,
            @Nullable ClaimSnapshot fireClaim,
            @Nullable ClaimSnapshot targetClaim,
            @NotNull LongFunction<ClaimSnapshot> byId)
    {
        if (!settings.fireSpreads())
        {
            return false;
        }
        if (targetClaim == null)
        {
            return true;
        }
        if (!ClaimRelations.sameOwner(fireClaim, targetClaim, byId))
        {
            return false;
        }
        return settings.fireSpreadsInClaims();
    }

    /**
     * @param burningClaim the claim of the block that would burn away, or null
     * @param fireClaim the claim of the fire burning it, or null
     */
    public static boolean mayBurn(
            @NotNull WorldProtectionSettings settings,
            @Nullable ClaimSnapshot burningClaim,
            @Nullable ClaimSnapshot fireClaim,
            @NotNull LongFunction<ClaimSnapshot> byId)
    {
        if (!settings.fireDestroys())
        {
            return false;
        }
        if (burningClaim == null)
        {
            return true;
        }
        if (!settings.fireDamagesInClaims())
        {
            return false;
        }
        // A wall on the claim border lit from outside must not burn.
        return ClaimRelations.sameOwner(burningClaim, fireClaim, byId);
    }
}
