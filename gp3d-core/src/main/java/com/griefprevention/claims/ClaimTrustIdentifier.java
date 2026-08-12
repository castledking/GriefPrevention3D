package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Shared encoding rules for Bukkit-compatible {@code [permission.node]} trust identifiers. */
public final class ClaimTrustIdentifier
{
    private ClaimTrustIdentifier()
    {
    }

    /**
     * Converts a command target into a permission trust identifier.
     *
     * <p>Bracketed targets are explicit. Unbracketed targets require a dot so an unknown player
     * name cannot silently become a permission node.
     */
    public static @Nullable String fromPermissionTarget(@Nullable String target)
    {
        String normalized = ClaimTrustSnapshot.normalizeIdentifier(target);
        boolean startsWithBracket = normalized.startsWith("[");
        boolean endsWithBracket = normalized.endsWith("]");
        if (startsWithBracket || endsWithBracket)
        {
            if (!startsWithBracket || !endsWithBracket || normalized.length() < 3)
            {
                return null;
            }
            String permission = ClaimTrustSnapshot.normalizeIdentifier(
                    normalized.substring(1, normalized.length() - 1)
            );
            return permission.isEmpty() ? null : forPermission(permission);
        }

        return normalized.contains(".") ? forPermission(normalized) : null;
    }

    /** Extracts the permission node from a stored identifier, including deny-suffixed entries. */
    public static @Nullable String permissionNode(@Nullable String identifier)
    {
        String normalized = ClaimTrustSnapshot.normalizeIdentifier(identifier);
        if (!normalized.startsWith("["))
        {
            return null;
        }

        int closingBracket = normalized.lastIndexOf(']');
        if (closingBracket < 2)
        {
            return null;
        }

        String suffix = normalized.substring(closingBracket + 1);
        if (!suffix.isEmpty() && !isDenySuffix(suffix))
        {
            return null;
        }

        String permission = ClaimTrustSnapshot.normalizeIdentifier(
                normalized.substring(1, closingBracket)
        );
        return permission.isEmpty() ? null : permission;
    }

    public static @NotNull String forPermission(@NotNull String permissionNode)
    {
        String normalized = ClaimTrustSnapshot.normalizeIdentifier(permissionNode);
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException("Permission node cannot be blank.");
        }
        return "[" + normalized + "]";
    }

    private static boolean isDenySuffix(@NotNull String suffix)
    {
        return suffix.equals(ClaimTrustLevel.MANAGE.denySuffix())
                || suffix.equals(ClaimTrustLevel.BUILD.denySuffix())
                || suffix.equals(ClaimTrustLevel.CONTAINER.denySuffix())
                || suffix.equals(ClaimTrustLevel.ACCESS.denySuffix())
                || suffix.equals(ClaimTrustLevel.NEIGHBOR.denySuffix());
    }
}
