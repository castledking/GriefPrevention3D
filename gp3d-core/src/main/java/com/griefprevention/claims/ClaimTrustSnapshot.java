package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable platform-neutral view of explicit claim trust and deny entries.
 */
public final class ClaimTrustSnapshot
{
    public static final @NotNull String PUBLIC_IDENTIFIER = "public";

    private final @Nullable UUID ownerId;
    private final @NotNull Map<String, ClaimTrustLevel> permissionsByIdentifier;
    private final @NotNull Set<String> managerIdentifiers;
    private final @NotNull Set<String> neighborIdentifiers;
    private final @NotNull Set<String> deniedIdentifiers;
    private final @NotNull Set<String> pvpTrustedIdentifiers;
    private final @NotNull Set<String> pveTrustedIdentifiers;

    public ClaimTrustSnapshot(
            @Nullable UUID ownerId,
            @NotNull Map<String, ClaimTrustLevel> permissionsByIdentifier,
            @NotNull Collection<String> managerIdentifiers,
            @NotNull Collection<String> deniedIdentifiers)
    {
        this(ownerId, permissionsByIdentifier, managerIdentifiers, Collections.emptySet(), deniedIdentifiers,
                Collections.emptySet(), Collections.emptySet());
    }

    public ClaimTrustSnapshot(
            @Nullable UUID ownerId,
            @NotNull Map<String, ClaimTrustLevel> permissionsByIdentifier,
            @NotNull Collection<String> managerIdentifiers,
            @NotNull Collection<String> neighborIdentifiers,
            @NotNull Collection<String> deniedIdentifiers)
    {
        this(ownerId, permissionsByIdentifier, managerIdentifiers, neighborIdentifiers, deniedIdentifiers,
                Collections.emptySet(), Collections.emptySet());
    }

    public ClaimTrustSnapshot(
            @Nullable UUID ownerId,
            @NotNull Map<String, ClaimTrustLevel> permissionsByIdentifier,
            @NotNull Collection<String> managerIdentifiers,
            @NotNull Collection<String> neighborIdentifiers,
            @NotNull Collection<String> deniedIdentifiers,
            @NotNull Collection<String> pvpTrustedIdentifiers,
            @NotNull Collection<String> pveTrustedIdentifiers)
    {
        this.ownerId = ownerId;
        Set<String> normalizedNeighbors = mutableNormalizedIdentifiers(neighborIdentifiers);
        this.permissionsByIdentifier = normalizePermissions(permissionsByIdentifier, normalizedNeighbors);
        this.managerIdentifiers = normalizeIdentifiers(managerIdentifiers);
        this.neighborIdentifiers = Collections.unmodifiableSet(normalizedNeighbors);
        this.deniedIdentifiers = normalizeIdentifiers(deniedIdentifiers);
        this.pvpTrustedIdentifiers = normalizeIdentifiers(pvpTrustedIdentifiers);
        this.pveTrustedIdentifiers = normalizeIdentifiers(pveTrustedIdentifiers);
    }

    public static @NotNull ClaimTrustSnapshot empty(@Nullable UUID ownerId)
    {
        return new ClaimTrustSnapshot(ownerId, Collections.emptyMap(), Collections.emptySet(), Collections.emptySet());
    }

    public static @NotNull String normalizeIdentifier(@Nullable String identifier)
    {
        if (identifier == null)
        {
            return "";
        }

        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    public @Nullable UUID ownerId()
    {
        return this.ownerId;
    }

    public @NotNull Map<String, ClaimTrustLevel> permissionsByIdentifier()
    {
        return this.permissionsByIdentifier;
    }

    public @NotNull Set<String> managerIdentifiers()
    {
        return this.managerIdentifiers;
    }

    public @NotNull Set<String> neighborIdentifiers()
    {
        return this.neighborIdentifiers;
    }

    public @NotNull Set<String> deniedIdentifiers()
    {
        return this.deniedIdentifiers;
    }

    /**
     * Identifiers granted standalone combat trust against players. Never implied by other trust
     * levels; enforced only while the host platform's PvP-trust feature is enabled.
     */
    public @NotNull Set<String> pvpTrustedIdentifiers()
    {
        return this.pvpTrustedIdentifiers;
    }

    /**
     * Identifiers granted standalone combat trust against creatures. Never implied by other trust
     * levels; enforced only while the host platform's PvE-trust feature is enabled.
     */
    public @NotNull Set<String> pveTrustedIdentifiers()
    {
        return this.pveTrustedIdentifiers;
    }

    public boolean isOwner(@NotNull UUID playerId)
    {
        return playerId.equals(this.ownerId);
    }

    public boolean hasExplicitPermission(@NotNull UUID playerId, @NotNull ClaimTrustLevel level)
    {
        if (isOwner(playerId))
        {
            return true;
        }

        return hasExplicitIdentifierPermission(playerId.toString(), level);
    }

    public boolean hasExplicitPermission(
            @NotNull UUID playerId,
            @NotNull Collection<String> identifiers,
            @NotNull ClaimTrustLevel level)
    {
        if (hasExplicitPermission(playerId, level))
        {
            return true;
        }

        for (String identifier : identifiers)
        {
            // Match Bukkit permission-node trust: a generally denied [permission.node]
            // cannot grant explicit trust, while UUID trust remains owner/direct-entry based.
            if (!isPermissionDenied(identifier) && hasExplicitIdentifierPermission(identifier, level))
            {
                return true;
            }
        }

        return false;
    }

    public boolean hasExplicitPermission(@NotNull ClaimAccessSubject subject, @NotNull ClaimTrustLevel level)
    {
        return hasExplicitPermission(subject.playerId(), subject.identifiers(), level);
    }

    public boolean hasExplicitIdentifierPermission(@Nullable String identifier, @NotNull ClaimTrustLevel level)
    {
        if (level == ClaimTrustLevel.EDIT)
        {
            return false;
        }

        String normalized = normalizeIdentifier(identifier);
        if (normalized.isEmpty())
        {
            return false;
        }

        // Manage and interaction trust are stored independently, so removing manage trust does
        // not discard an explicit interaction grant. Either stored grant can satisfy the check.
        if (this.managerIdentifiers.contains(normalized) && level.isGrantedBy(ClaimTrustLevel.MANAGE))
        {
            return true;
        }

        if (level == ClaimTrustLevel.NEIGHBOR && this.neighborIdentifiers.contains(normalized))
        {
            return true;
        }

        return level.isGrantedBy(this.permissionsByIdentifier.get(normalized));
    }

    public boolean hasPublicPermission(@NotNull ClaimTrustLevel level)
    {
        return hasExplicitIdentifierPermission(PUBLIC_IDENTIFIER, level);
    }

    public boolean isPermissionDenied(@Nullable String identifier)
    {
        return isPermissionDenied(identifier, null);
    }

    public boolean isPermissionDenied(@Nullable String identifier, @Nullable ClaimTrustLevel level)
    {
        String normalized = normalizeIdentifier(identifier);
        if (normalized.isEmpty())
        {
            return false;
        }

        if (this.deniedIdentifiers.contains(normalized))
        {
            return true;
        }

        return level != null
                && level != ClaimTrustLevel.EDIT
                && this.deniedIdentifiers.contains(normalized + level.denySuffix());
    }

    public boolean isPermissionDenied(@NotNull ClaimAccessSubject subject, @NotNull ClaimTrustLevel level)
    {
        if (isPermissionDenied(subject.playerId().toString(), level))
        {
            return true;
        }

        for (String identifier : subject.identifiers())
        {
            if (isPermissionDenied(identifier, level))
            {
                return true;
            }
        }

        return false;
    }

    private static @NotNull Map<String, ClaimTrustLevel> normalizePermissions(
            @NotNull Map<String, ClaimTrustLevel> permissionsByIdentifier,
            @NotNull Set<String> neighborIdentifiers)
    {
        Map<String, ClaimTrustLevel> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ClaimTrustLevel> entry : permissionsByIdentifier.entrySet())
        {
            String identifier = normalizeIdentifier(entry.getKey());
            ClaimTrustLevel level = entry.getValue();
            if (!identifier.isEmpty() && level != null)
            {
                if (level == ClaimTrustLevel.NEIGHBOR)
                {
                    neighborIdentifiers.add(identifier);
                }
                else
                {
                    normalized.put(identifier, level);
                }
            }
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static @NotNull Set<String> normalizeIdentifiers(@NotNull Collection<String> identifiers)
    {
        return Collections.unmodifiableSet(mutableNormalizedIdentifiers(identifiers));
    }

    private static @NotNull Set<String> mutableNormalizedIdentifiers(@NotNull Collection<String> identifiers)
    {
        Set<String> normalized = new LinkedHashSet<>();
        for (String identifier : identifiers)
        {
            String value = normalizeIdentifier(identifier);
            if (!value.isEmpty())
            {
                normalized.add(value);
            }
        }
        return normalized;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimTrustSnapshot)) return false;
        ClaimTrustSnapshot that = (ClaimTrustSnapshot) other;
        return Objects.equals(this.ownerId, that.ownerId)
                && this.permissionsByIdentifier.equals(that.permissionsByIdentifier)
                && this.managerIdentifiers.equals(that.managerIdentifiers)
                && this.neighborIdentifiers.equals(that.neighborIdentifiers)
                && this.deniedIdentifiers.equals(that.deniedIdentifiers)
                && this.pvpTrustedIdentifiers.equals(that.pvpTrustedIdentifiers)
                && this.pveTrustedIdentifiers.equals(that.pveTrustedIdentifiers);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.ownerId, this.permissionsByIdentifier, this.managerIdentifiers,
                this.neighborIdentifiers, this.deniedIdentifiers, this.pvpTrustedIdentifiers,
                this.pveTrustedIdentifiers);
    }

    @Override
    public String toString()
    {
        return "ClaimTrustSnapshot[ownerId=" + this.ownerId
                + ", permissionsByIdentifier=" + this.permissionsByIdentifier
                + ", managerIdentifiers=" + this.managerIdentifiers
                + ", neighborIdentifiers=" + this.neighborIdentifiers
                + ", deniedIdentifiers=" + this.deniedIdentifiers
                + ", pvpTrustedIdentifiers=" + this.pvpTrustedIdentifiers
                + ", pveTrustedIdentifiers=" + this.pveTrustedIdentifiers
                + "]";
    }
}
