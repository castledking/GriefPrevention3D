package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Platform-neutral actor used when evaluating claim trust.
 */
public final class ClaimAccessSubject
{
    private final @NotNull UUID playerId;
    private final @NotNull Set<String> identifiers;

    private ClaimAccessSubject(@NotNull UUID playerId, @NotNull Collection<String> identifiers)
    {
        this.playerId = playerId;
        Set<String> normalized = new LinkedHashSet<>();
        for (String identifier : identifiers)
        {
            String value = ClaimTrustSnapshot.normalizeIdentifier(identifier);
            if (!value.isEmpty())
            {
                normalized.add(value);
            }
        }
        this.identifiers = Collections.unmodifiableSet(normalized);
    }

    public static @NotNull ClaimAccessSubject of(@NotNull UUID playerId)
    {
        return new ClaimAccessSubject(playerId, Collections.emptySet());
    }

    public static @NotNull ClaimAccessSubject of(
            @NotNull UUID playerId,
            @NotNull Collection<String> identifiers)
    {
        return new ClaimAccessSubject(playerId, identifiers);
    }

    public @NotNull UUID playerId()
    {
        return this.playerId;
    }

    public @NotNull Set<String> identifiers()
    {
        return this.identifiers;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimAccessSubject)) return false;
        ClaimAccessSubject that = (ClaimAccessSubject) other;
        return this.playerId.equals(that.playerId) && this.identifiers.equals(that.identifiers);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.playerId, this.identifiers);
    }

    @Override
    public String toString()
    {
        return "ClaimAccessSubject[playerId=" + this.playerId + ", identifiers=" + this.identifiers + "]";
    }
}
