package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimAccessSubject;
import com.griefprevention.claims.ClaimTrustIdentifier;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Resolves provider-backed trust entries and applies the existing shared trust semantics. */
final class FabricClaimTrustEvaluator
{
    private FabricClaimTrustEvaluator()
    {
    }

    static boolean allows(
            @NotNull UUID playerId,
            @NotNull ClaimTrustSnapshot trust,
            @NotNull ClaimTrustLevel required,
            @NotNull FabricPermissionResolver permissions)
    {
        if (trust.isOwner(playerId))
        {
            return true;
        }

        ClaimAccessSubject subject = subject(playerId, trust, permissions);
        if (trust.hasExplicitPermission(subject, required))
        {
            return true;
        }
        return !trust.isPermissionDenied(subject, required) && trust.hasPublicPermission(required);
    }

    static @NotNull ClaimAccessSubject subject(
            @NotNull UUID playerId,
            @NotNull ClaimTrustSnapshot trust,
            @NotNull FabricPermissionResolver permissions)
    {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.addAll(trust.permissionsByIdentifier().keySet());
        candidates.addAll(trust.managerIdentifiers());
        candidates.addAll(trust.deniedIdentifiers());

        Set<String> grantedIdentifiers = new LinkedHashSet<>();
        for (String candidate : candidates)
        {
            String permissionNode = ClaimTrustIdentifier.permissionNode(candidate);
            if (permissionNode != null && permissions.hasPermission(playerId, permissionNode))
            {
                grantedIdentifiers.add(ClaimTrustIdentifier.forPermission(permissionNode));
            }
        }
        return ClaimAccessSubject.of(playerId, grantedIdentifiers);
    }
}
