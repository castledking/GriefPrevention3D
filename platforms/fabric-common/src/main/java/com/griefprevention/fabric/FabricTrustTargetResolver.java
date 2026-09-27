package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimTrustIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/** Resolves command targets without confusing an unknown player with a permission node. */
final class FabricTrustTargetResolver
{
    private FabricTrustTargetResolver()
    {
    }

    static @Nullable String resolve(
            @NotNull String target,
            @NotNull Function<String, UUID> knownPlayerLookup)
    {
        if (target.startsWith("[") || target.endsWith("]"))
        {
            return ClaimTrustIdentifier.fromPermissionTarget(target);
        }
        if ("public".equalsIgnoreCase(target))
        {
            return "public";
        }

        try
        {
            return UUID.fromString(target).toString();
        }
        catch (IllegalArgumentException ignored)
        {
            UUID knownPlayer = knownPlayerLookup.apply(target);
            return knownPlayer == null
                    ? ClaimTrustIdentifier.fromPermissionTarget(target)
                    : knownPlayer.toString();
        }
    }
}
