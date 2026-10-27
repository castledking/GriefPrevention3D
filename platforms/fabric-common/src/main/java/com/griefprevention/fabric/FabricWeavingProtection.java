package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.protection.BlockPoint;
import com.griefprevention.protection.WorldProtectionSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/** Resolves weaving against the destination's claim, including 3D subdivisions. */
final class FabricWeavingProtection
{
    private final FabricClaimRepository claims;
    private final WorldProtectionSettings settings;

    FabricWeavingProtection(@NotNull FabricClaimRepository claims, @NotNull WorldProtectionSettings settings)
    {
        this.claims = claims;
        this.settings = settings;
    }

    boolean mayPlaceWeb(
            @NotNull String world,
            @NotNull BlockPoint destination,
            @Nullable BiPredicate<ClaimSnapshot, ClaimTrustLevel> playerTrust,
            boolean ignoresClaims)
    {
        if (!settings.appliesTo(world)) return true;

        ClaimSnapshot claim = claimAt(world, destination);
        if (claim == null)
        {
            return playerTrust == null || ignoresClaims || !settings.creativeRulesApply(world);
        }
        return playerTrust != null && playerTrust.test(claim, ClaimTrustLevel.BUILD);
    }

    boolean mayApplyToCreature(
            @NotNull String world,
            @NotNull BlockPoint destination,
            @Nullable BiPredicate<ClaimSnapshot, ClaimTrustLevel> playerTrust,
            @Nullable FabricPotionSources.Origin dispenser)
    {
        if (!settings.appliesTo(world)) return true;

        ClaimSnapshot claim = claimAt(world, destination);
        if (claim == null) return true;
        if (playerTrust != null) return playerTrust.test(claim, ClaimTrustLevel.CONTAINER);

        if (dispenser == null || !world.equals(dispenser.world())) return false;
        ClaimSnapshot source = claimAt(world, dispenser.position());
        return source != null && claim.id() != null && claim.id().equals(source.id());
    }

    private @Nullable ClaimSnapshot claimAt(String world, BlockPoint point)
    {
        return claims.findClaimAt(world, point.x(), point.y(), point.z(), false, false).orElse(null);
    }
}
