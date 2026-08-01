package com.griefprevention.persistence;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Internal, platform-neutral representation of one persisted claim section.
 *
 * <p>This is deliberately separate from the mutable Bukkit {@code Claim} API. Platform adapters
 * may map their runtime objects to this document without exposing serialization details to addons.
 */
@ApiStatus.Internal
public final class ClaimDocument
{
    private final @NotNull ClaimSnapshot snapshot;
    private final @NotNull ClaimTrustSnapshot trust;
    private final @NotNull List<OrthogonalPoint2i> shapeCorners;
    private final boolean inheritNothing;
    private final boolean inheritNothingForNewSubdivisions;
    private final boolean explosivesAllowed;
    private final boolean witherExplosionsAllowed;
    private final boolean pvpEnabled;
    private final boolean alertsEnabled;
    private final long modifiedDate;
    private final @Nullable String storageKey;
    private final @NotNull Map<String, Object> extraFields;

    public ClaimDocument(
            @NotNull ClaimSnapshot snapshot,
            @NotNull ClaimTrustSnapshot trust,
            @NotNull List<OrthogonalPoint2i> shapeCorners,
            boolean inheritNothing,
            boolean inheritNothingForNewSubdivisions,
            boolean explosivesAllowed,
            boolean witherExplosionsAllowed,
            boolean pvpEnabled,
            boolean alertsEnabled,
            long modifiedDate,
            @Nullable String storageKey,
            @NotNull Map<String, Object> extraFields)
    {
        this.snapshot = snapshot;
        this.trust = trust;
        this.shapeCorners = Collections.unmodifiableList(new ArrayList<>(shapeCorners));
        this.inheritNothing = inheritNothing;
        this.inheritNothingForNewSubdivisions = inheritNothingForNewSubdivisions;
        this.explosivesAllowed = explosivesAllowed;
        this.witherExplosionsAllowed = witherExplosionsAllowed;
        this.pvpEnabled = pvpEnabled;
        this.alertsEnabled = alertsEnabled;
        this.modifiedDate = modifiedDate;
        this.storageKey = storageKey;
        this.extraFields = immutableStringMap(extraFields);
    }

    public static @NotNull ClaimDocument create(@NotNull ClaimSnapshot snapshot, long modifiedDate)
    {
        return new ClaimDocument(
                snapshot,
                ClaimTrustSnapshot.empty(snapshot.ownerId()),
                Collections.<OrthogonalPoint2i>emptyList(),
                false,
                false,
                false,
                false,
                true,
                true,
                modifiedDate,
                snapshot.id() == null ? null : String.valueOf(snapshot.id()),
                Collections.<String, Object>emptyMap()
        );
    }

    public @NotNull ClaimSnapshot snapshot()
    {
        return this.snapshot;
    }

    public @NotNull ClaimTrustSnapshot trust()
    {
        return this.trust;
    }

    public @NotNull List<OrthogonalPoint2i> shapeCorners()
    {
        return this.shapeCorners;
    }

    public boolean inheritNothing()
    {
        return this.inheritNothing;
    }

    public boolean inheritNothingForNewSubdivisions()
    {
        return this.inheritNothingForNewSubdivisions;
    }

    public boolean explosivesAllowed()
    {
        return this.explosivesAllowed;
    }

    public boolean witherExplosionsAllowed()
    {
        return this.witherExplosionsAllowed;
    }

    public boolean pvpEnabled()
    {
        return this.pvpEnabled;
    }

    public boolean alertsEnabled()
    {
        return this.alertsEnabled;
    }

    public long modifiedDate()
    {
        return this.modifiedDate;
    }

    public @Nullable String storageKey()
    {
        return this.storageKey;
    }

    public @NotNull Map<String, Object> extraFields()
    {
        return this.extraFields;
    }

    public @NotNull ClaimDocument withSnapshot(@NotNull ClaimSnapshot updatedSnapshot, long updatedModifiedDate)
    {
        return new ClaimDocument(
                updatedSnapshot,
                this.trust,
                shapeCorners(updatedSnapshot),
                this.inheritNothing,
                this.inheritNothingForNewSubdivisions,
                this.explosivesAllowed,
                this.witherExplosionsAllowed,
                this.pvpEnabled,
                this.alertsEnabled,
                updatedModifiedDate,
                this.storageKey,
                this.extraFields
        );
    }

    public @NotNull ClaimDocument withTrust(@NotNull ClaimTrustSnapshot updatedTrust)
    {
        return new ClaimDocument(
                this.snapshot,
                updatedTrust,
                this.shapeCorners,
                this.inheritNothing,
                this.inheritNothingForNewSubdivisions,
                this.explosivesAllowed,
                this.witherExplosionsAllowed,
                this.pvpEnabled,
                this.alertsEnabled,
                this.modifiedDate,
                this.storageKey,
                this.extraFields
        );
    }

    private @NotNull List<OrthogonalPoint2i> shapeCorners(@NotNull ClaimSnapshot updatedSnapshot)
    {
        if (!updatedSnapshot.bounds().isShaped())
        {
            return Collections.emptyList();
        }

        return updatedSnapshot.bounds().polygon().corners();
    }

    private static @NotNull Map<String, Object> immutableStringMap(@NotNull Map<String, Object> source)
    {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet())
        {
            copy.put(entry.getKey(), immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static @Nullable Object immutableValue(@Nullable Object value)
    {
        if (value instanceof Map)
        {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                copy.put(immutableValue(entry.getKey()), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List)
        {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value)
            {
                copy.add(immutableValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set)
        {
            Set<Object> copy = new LinkedHashSet<>();
            for (Object item : (Set<?>) value)
            {
                copy.add(immutableValue(item));
            }
            return Collections.unmodifiableSet(copy);
        }
        if (value instanceof Date)
        {
            return new Date(((Date) value).getTime());
        }
        if (value instanceof byte[])
        {
            return ((byte[]) value).clone();
        }
        return value;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimDocument)) return false;
        ClaimDocument that = (ClaimDocument) other;
        return this.inheritNothing == that.inheritNothing
                && this.inheritNothingForNewSubdivisions == that.inheritNothingForNewSubdivisions
                && this.explosivesAllowed == that.explosivesAllowed
                && this.witherExplosionsAllowed == that.witherExplosionsAllowed
                && this.pvpEnabled == that.pvpEnabled
                && this.alertsEnabled == that.alertsEnabled
                && this.modifiedDate == that.modifiedDate
                && this.snapshot.equals(that.snapshot)
                && this.trust.equals(that.trust)
                && this.shapeCorners.equals(that.shapeCorners)
                && Objects.equals(this.storageKey, that.storageKey)
                && deepEquals(this.extraFields, that.extraFields);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(
                this.snapshot,
                this.trust,
                this.shapeCorners,
                this.inheritNothing,
                this.inheritNothingForNewSubdivisions,
                this.explosivesAllowed,
                this.witherExplosionsAllowed,
                this.pvpEnabled,
                this.alertsEnabled,
                this.modifiedDate,
                this.storageKey,
                deepHashCode(this.extraFields)
        );
    }

    private static boolean deepEquals(@Nullable Object first, @Nullable Object second)
    {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first instanceof byte[] && second instanceof byte[])
        {
            return Arrays.equals((byte[]) first, (byte[]) second);
        }
        if (first instanceof Map && second instanceof Map)
        {
            Map<?, ?> firstMap = (Map<?, ?>) first;
            Map<?, ?> secondMap = (Map<?, ?>) second;
            if (firstMap.size() != secondMap.size()) return false;
            List<Map.Entry<?, ?>> unmatched = new ArrayList<>(secondMap.entrySet());
            for (Map.Entry<?, ?> firstEntry : firstMap.entrySet())
            {
                boolean found = false;
                for (int index = 0; index < unmatched.size(); index++)
                {
                    Map.Entry<?, ?> secondEntry = unmatched.get(index);
                    if (deepEquals(firstEntry.getKey(), secondEntry.getKey())
                            && deepEquals(firstEntry.getValue(), secondEntry.getValue()))
                    {
                        unmatched.remove(index);
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }
        if (first instanceof List && second instanceof List)
        {
            List<?> firstList = (List<?>) first;
            List<?> secondList = (List<?>) second;
            if (firstList.size() != secondList.size()) return false;
            for (int index = 0; index < firstList.size(); index++)
            {
                if (!deepEquals(firstList.get(index), secondList.get(index))) return false;
            }
            return true;
        }
        if (first instanceof Set && second instanceof Set)
        {
            Set<?> firstSet = (Set<?>) first;
            Set<?> secondSet = (Set<?>) second;
            if (firstSet.size() != secondSet.size()) return false;
            List<Object> unmatched = new ArrayList<Object>(secondSet);
            for (Object firstItem : firstSet)
            {
                boolean found = false;
                for (int index = 0; index < unmatched.size(); index++)
                {
                    if (deepEquals(firstItem, unmatched.get(index)))
                    {
                        unmatched.remove(index);
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }
        return first.equals(second);
    }

    private static int deepHashCode(@Nullable Object value)
    {
        if (value == null) return 0;
        if (value instanceof byte[]) return Arrays.hashCode((byte[]) value);
        if (value instanceof Map)
        {
            int hash = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                hash += deepHashCode(entry.getKey()) ^ deepHashCode(entry.getValue());
            }
            return hash;
        }
        if (value instanceof List)
        {
            int hash = 1;
            for (Object item : (List<?>) value)
            {
                hash = 31 * hash + deepHashCode(item);
            }
            return hash;
        }
        if (value instanceof Set)
        {
            int hash = 0;
            for (Object item : (Set<?>) value)
            {
                hash += deepHashCode(item);
            }
            return hash;
        }
        return value.hashCode();
    }

    @Override
    public String toString()
    {
        return "ClaimDocument[snapshot=" + this.snapshot
                + ", trust=" + this.trust
                + ", shapeCorners=" + this.shapeCorners
                + ", inheritNothing=" + this.inheritNothing
                + ", inheritNothingForNewSubdivisions=" + this.inheritNothingForNewSubdivisions
                + ", explosivesAllowed=" + this.explosivesAllowed
                + ", witherExplosionsAllowed=" + this.witherExplosionsAllowed
                + ", pvpEnabled=" + this.pvpEnabled
                + ", alertsEnabled=" + this.alertsEnabled
                + ", modifiedDate=" + this.modifiedDate
                + ", storageKey=" + this.storageKey
                + ", extraFields=" + this.extraFields
                + "]";
    }
}
