package com.griefprevention.persistence;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Safe YAML codec for the upstream GriefPrevention claim-file shape.
 *
 * <p>The codec normalizes known fields into {@link ClaimDocument} values and carries unknown fields
 * through semantic round trips. It deliberately does not retain comments or scalar formatting.
 */
@ApiStatus.Internal
public final class ClaimDocumentCodec
{
    private static final String CLAIM_ID = "Claim ID";
    private static final String LESSER_BOUNDARY = "Lesser Boundary Corner";
    private static final String GREATER_BOUNDARY = "Greater Boundary Corner";
    private static final String OWNER = "Owner";
    private static final String BUILDERS = "Builders";
    private static final String CONTAINERS = "Containers";
    private static final String ACCESSORS = "Accessors";
    private static final String MANAGERS = "Managers";
    private static final String PARENT_ID = "Parent Claim ID";
    private static final String INHERIT_NOTHING = "inheritNothing";
    private static final String INHERIT_NOTHING_FOR_NEW = "inheritNothingForNewSubdivisions";
    private static final String IS_3D = "Is3D";
    private static final String SHAPE_CORNERS = "Shape Corners";
    private static final String EXPLOSIVES_ALLOWED = "Explosives Allowed";
    private static final String WITHER_EXPLOSIONS_ALLOWED = "Wither Explosions Allowed";
    private static final String PVP_ENABLED = "PvP Enabled";
    private static final String ALERTS_ENABLED = "Alerts Enabled";
    private static final String MODIFIED_DATE = "Modified Date";
    private static final String CHILDREN = "Children";

    private static final Set<String> KNOWN_FIELDS;

    static
    {
        Set<String> fields = new LinkedHashSet<>();
        fields.add(CLAIM_ID);
        fields.add(LESSER_BOUNDARY);
        fields.add(GREATER_BOUNDARY);
        fields.add(OWNER);
        fields.add(BUILDERS);
        fields.add(CONTAINERS);
        fields.add(ACCESSORS);
        fields.add(MANAGERS);
        fields.add(PARENT_ID);
        fields.add(INHERIT_NOTHING);
        fields.add(INHERIT_NOTHING_FOR_NEW);
        fields.add(IS_3D);
        fields.add(SHAPE_CORNERS);
        fields.add(EXPLOSIVES_ALLOWED);
        fields.add(WITHER_EXPLOSIONS_ALLOWED);
        fields.add(PVP_ENABLED);
        fields.add(ALERTS_ENABLED);
        fields.add(MODIFIED_DATE);
        fields.add(CHILDREN);
        KNOWN_FIELDS = Collections.unmodifiableSet(fields);
    }

    private final Yaml yaml;

    public ClaimDocumentCodec()
    {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(50);
        loaderOptions.setNestingDepthLimit(100);
        loaderOptions.setCodePointLimit(16 * 1024 * 1024);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        dumperOptions.setIndent(2);
        dumperOptions.setIndicatorIndent(0);
        dumperOptions.setIndentWithIndicator(false);
        dumperOptions.setPrettyFlow(false);
        dumperOptions.setSplitLines(false);
        dumperOptions.setWidth(4096);

        this.yaml = new Yaml(
                new SafeConstructor(loaderOptions),
                new Representer(dumperOptions),
                dumperOptions,
                loaderOptions
        );
    }

    /**
     * Decode a root claim and all nested subdivisions into a flat document list.
     */
    public synchronized @NotNull List<ClaimDocument> decodeTree(
            @NotNull String input,
            @Nullable Long fallbackClaimId,
            long fallbackModifiedDate)
            throws ClaimDocumentFormatException
    {
        final Object loaded;
        try
        {
            loaded = this.yaml.load(input);
        }
        catch (YAMLException exception)
        {
            throw new ClaimDocumentFormatException("Invalid claim YAML: " + exception.getMessage(), exception);
        }

        Map<String, Object> root = claimSection(loaded, "root claim");
        List<ClaimDocument> documents = new ArrayList<>();
        parseSection(root, fallbackClaimId, null, fallbackModifiedDate, null, documents);
        validateUniqueIds(documents);
        return Collections.unmodifiableList(documents);
    }

    /**
     * Encode one root claim and every supplied descendant into upstream's nested YAML shape.
     */
    public synchronized @NotNull String encodeTree(
            @NotNull ClaimDocument root,
            @NotNull Collection<ClaimDocument> documents)
            throws ClaimDocumentFormatException
    {
        Long rootId = requireId(root, "root claim");
        if (root.snapshot().parentId() != null)
        {
            throw new ClaimDocumentFormatException("Claim " + rootId + " is not a root claim.");
        }

        Map<Long, List<ClaimDocument>> childrenByParent = new LinkedHashMap<>();
        Map<Long, ClaimDocument> byId = new LinkedHashMap<>();
        byId.put(rootId, root);
        for (ClaimDocument document : documents)
        {
            Long id = requireId(document, "claim");
            ClaimDocument existing = byId.get(id);
            if (existing != null && existing != document && !existing.equals(document))
            {
                throw new ClaimDocumentFormatException("Duplicate claim id " + id + ".");
            }
            byId.put(id, document);

            Long parentId = document.snapshot().parentId();
            if (parentId != null)
            {
                List<ClaimDocument> children = childrenByParent.get(parentId);
                if (children == null)
                {
                    children = new ArrayList<>();
                    childrenByParent.put(parentId, children);
                }
                children.add(document);
            }
        }

        for (ClaimDocument document : byId.values())
        {
            Long id = requireId(document, "claim");
            Long parentId = document.snapshot().parentId();
            if (id.equals(rootId))
            {
                if (parentId != null)
                {
                    throw new ClaimDocumentFormatException("Claim " + rootId + " is not a root claim.");
                }
            }
            else if (parentId == null)
            {
                throw new ClaimDocumentFormatException(
                        "Claim " + id + " is outside root claim " + rootId + "."
                );
            }
            else if (!byId.containsKey(parentId))
            {
                throw new ClaimDocumentFormatException(
                        "Claim " + id + " references missing parent " + parentId + "."
                );
            }
        }

        for (List<ClaimDocument> children : childrenByParent.values())
        {
            Collections.sort(children, new Comparator<ClaimDocument>()
            {
                @Override
                public int compare(ClaimDocument first, ClaimDocument second)
                {
                    return Long.compare(first.snapshot().id(), second.snapshot().id());
                }
            });
        }

        Set<Long> encodedIds = new HashSet<>();
        Map<String, Object> encoded = encodeSection(root, childrenByParent, new HashSet<Long>(), encodedIds);
        if (!encodedIds.equals(byId.keySet()))
        {
            Set<Long> unreachable = new LinkedHashSet<>(byId.keySet());
            unreachable.removeAll(encodedIds);
            throw new ClaimDocumentFormatException(
                    "Claims " + unreachable + " are not descendants of root claim " + rootId + "."
            );
        }
        return this.yaml.dump(encoded);
    }

    private void parseSection(
            @NotNull Map<String, Object> section,
            @Nullable Long fallbackId,
            @Nullable Long inheritedParentId,
            long fallbackModifiedDate,
            @Nullable String storageKey,
            @NotNull List<ClaimDocument> output)
            throws ClaimDocumentFormatException
    {
        Long declaredId = optionalLong(section.get(CLAIM_ID), CLAIM_ID);
        if (declaredId != null && fallbackId != null && !declaredId.equals(fallbackId))
        {
            throw new ClaimDocumentFormatException(
                    "Claim id " + declaredId + " does not match its storage id " + fallbackId + "."
            );
        }
        Long id = declaredId == null ? fallbackId : declaredId;
        if (id == null || id < 0L)
        {
            throw new ClaimDocumentFormatException("Claim section is missing a valid claim id.");
        }

        boolean declaresParent = section.containsKey(PARENT_ID);
        Long declaredParentId = parentId(section.get(PARENT_ID));
        if (inheritedParentId != null && declaresParent && !inheritedParentId.equals(declaredParentId))
        {
            throw new ClaimDocumentFormatException(
                    "Claim " + id + " declares parent " + declaredParentId
                            + " but is nested under claim " + inheritedParentId + "."
            );
        }
        Long effectiveParentId = inheritedParentId == null ? declaredParentId : inheritedParentId;

        BoundaryLocation lesser = boundary(section.get(LESSER_BOUNDARY), LESSER_BOUNDARY, id);
        BoundaryLocation greater = boundary(section.get(GREATER_BOUNDARY), GREATER_BOUNDARY, id);
        if (!lesser.world.equals(greater.world))
        {
            throw new ClaimDocumentFormatException(
                    "Claim " + id + " has boundary corners in different worlds."
            );
        }

        UUID ownerId = owner(section.get(OWNER), id);
        boolean threeDimensional = bool(section.get(IS_3D), false, IS_3D);
        List<OrthogonalPoint2i> shapeCorners = shapeCorners(section.get(SHAPE_CORNERS), id);
        ClaimBounds rectangle = ClaimBounds.rectangle(
                lesser.x,
                lesser.y,
                lesser.z,
                greater.x,
                greater.y,
                greater.z
        );
        ClaimBounds bounds = rectangle;
        if (effectiveParentId == null && !threeDimensional && !shapeCorners.isEmpty())
        {
            List<OrthogonalPoint2i> closedPath = new ArrayList<>(shapeCorners);
            if (!closedPath.get(0).equals(closedPath.get(closedPath.size() - 1)))
            {
                closedPath.add(closedPath.get(0));
            }
            try
            {
                bounds = ClaimBounds.shaped(
                        OrthogonalPolygon.fromClosedPath(closedPath),
                        rectangle.minY(),
                        rectangle.maxY()
                );
            }
            catch (IllegalArgumentException exception)
            {
                throw new ClaimDocumentFormatException(
                        "Claim " + id + " has invalid shaped corners: " + exception.getMessage(),
                        exception
                );
            }
        }

        ClaimSnapshot snapshot = new ClaimSnapshot(
                id,
                lesser.world,
                ownerId,
                effectiveParentId,
                bounds,
                threeDimensional,
                effectiveParentId != null
        );
        ClaimTrustSnapshot trust = trust(section, ownerId);
        long modifiedDate = longValue(section.get(MODIFIED_DATE), fallbackModifiedDate, MODIFIED_DATE);
        ClaimDocument document = new ClaimDocument(
                snapshot,
                trust,
                shapeCorners,
                bool(section.get(INHERIT_NOTHING), false, INHERIT_NOTHING),
                bool(section.get(INHERIT_NOTHING_FOR_NEW), false, INHERIT_NOTHING_FOR_NEW),
                bool(section.get(EXPLOSIVES_ALLOWED), false, EXPLOSIVES_ALLOWED),
                bool(section.get(WITHER_EXPLOSIONS_ALLOWED), false, WITHER_EXPLOSIONS_ALLOWED),
                bool(section.get(PVP_ENABLED), true, PVP_ENABLED),
                bool(section.get(ALERTS_ENABLED), true, ALERTS_ENABLED),
                modifiedDate,
                storageKey == null ? String.valueOf(id) : storageKey,
                extraFields(section)
        );
        output.add(document);

        Object rawChildren = section.get(CHILDREN);
        if (rawChildren == null)
        {
            return;
        }
        if (!(rawChildren instanceof Map))
        {
            throw new ClaimDocumentFormatException("Children for claim " + id + " must be a mapping.");
        }

        for (Map.Entry<?, ?> childEntry : ((Map<?, ?>) rawChildren).entrySet())
        {
            String childStorageKey = String.valueOf(childEntry.getKey());
            Map<String, Object> childSection = claimSection(
                    childEntry.getValue(),
                    "child " + childStorageKey + " of claim " + id
            );
            parseSection(
                    childSection,
                    null,
                    id,
                    modifiedDate,
                    childStorageKey,
                    output
            );
        }
    }

    private @NotNull Map<String, Object> encodeSection(
            @NotNull ClaimDocument document,
            @NotNull Map<Long, List<ClaimDocument>> childrenByParent,
            @NotNull Set<Long> ancestors,
            @NotNull Set<Long> encodedIds)
            throws ClaimDocumentFormatException
    {
        ClaimSnapshot snapshot = document.snapshot();
        Long id = requireId(document, "claim");
        if (!ancestors.add(id))
        {
            throw new ClaimDocumentFormatException("Claim graph contains a cycle at claim " + id + ".");
        }
        if (!encodedIds.add(id))
        {
            throw new ClaimDocumentFormatException("Claim graph reaches claim " + id + " more than once.");
        }

        ClaimBounds bounds = snapshot.bounds();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put(CLAIM_ID, String.valueOf(id));
        section.put(LESSER_BOUNDARY, location(snapshot.worldKey(), bounds.minX(), bounds.minY(), bounds.minZ()));
        section.put(GREATER_BOUNDARY, location(snapshot.worldKey(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        section.put(OWNER, snapshot.ownerId() == null ? "" : snapshot.ownerId().toString());

        TrustLists trustLists = trustLists(document.trust());
        section.put(BUILDERS, trustLists.builders);
        section.put(CONTAINERS, trustLists.containers);
        section.put(ACCESSORS, trustLists.accessors);
        section.put(MANAGERS, trustLists.managers);
        section.put(PARENT_ID, snapshot.parentId() == null ? Long.valueOf(-1L) : snapshot.parentId());
        section.put(INHERIT_NOTHING, document.inheritNothing());
        section.put(INHERIT_NOTHING_FOR_NEW, document.inheritNothingForNewSubdivisions());
        section.put(IS_3D, snapshot.threeDimensional());
        if (!document.shapeCorners().isEmpty())
        {
            List<String> encodedCorners = new ArrayList<>();
            for (OrthogonalPoint2i corner : document.shapeCorners())
            {
                encodedCorners.add(corner.x() + "," + corner.z());
            }
            section.put(SHAPE_CORNERS, encodedCorners);
        }
        section.put(EXPLOSIVES_ALLOWED, document.explosivesAllowed());
        section.put(WITHER_EXPLOSIONS_ALLOWED, document.witherExplosionsAllowed());
        section.put(PVP_ENABLED, document.pvpEnabled());
        section.put(ALERTS_ENABLED, document.alertsEnabled());
        section.put(MODIFIED_DATE, document.modifiedDate());

        for (Map.Entry<String, Object> extra : document.extraFields().entrySet())
        {
            section.put(extra.getKey(), mutableValue(extra.getValue()));
        }

        List<ClaimDocument> children = childrenByParent.get(id);
        if (children != null && !children.isEmpty())
        {
            Map<String, Object> encodedChildren = new LinkedHashMap<>();
            for (ClaimDocument child : children)
            {
                Long childId = requireId(child, "child claim");
                String childKey = child.storageKey();
                if (childKey == null || childKey.trim().isEmpty())
                {
                    childKey = String.valueOf(childId);
                }
                if (encodedChildren.containsKey(childKey))
                {
                    throw new ClaimDocumentFormatException(
                            "Claim " + id + " has duplicate child storage key " + childKey + "."
                    );
                }
                encodedChildren.put(childKey, encodeSection(child, childrenByParent, ancestors, encodedIds));
            }
            section.put(CHILDREN, encodedChildren);
        }

        ancestors.remove(id);
        return section;
    }

    private static @NotNull ClaimTrustSnapshot trust(
            @NotNull Map<String, Object> section,
            @Nullable UUID ownerId)
            throws ClaimDocumentFormatException
    {
        Map<String, ClaimTrustLevel> permissions = new LinkedHashMap<>();
        putTrust(permissions, stringList(section.get(BUILDERS), BUILDERS), ClaimTrustLevel.BUILD);
        putTrust(permissions, stringList(section.get(CONTAINERS), CONTAINERS), ClaimTrustLevel.CONTAINER);
        putTrust(permissions, stringList(section.get(ACCESSORS), ACCESSORS), ClaimTrustLevel.ACCESS);

        List<String> managers = stringList(section.get(MANAGERS), MANAGERS);
        for (String manager : managers)
        {
            permissions.remove(ClaimTrustSnapshot.normalizeIdentifier(manager));
        }
        return new ClaimTrustSnapshot(ownerId, permissions, managers, Collections.<String>emptyList());
    }

    private static void putTrust(
            @NotNull Map<String, ClaimTrustLevel> trust,
            @NotNull Collection<String> identifiers,
            @NotNull ClaimTrustLevel level)
    {
        for (String identifier : identifiers)
        {
            String normalized = ClaimTrustSnapshot.normalizeIdentifier(identifier);
            if (normalized.isEmpty())
            {
                continue;
            }

            // Match Claim's constructor exactly: Builders, Containers, then Accessors are applied
            // in order, so a later list wins even when it grants a weaker permission.
            trust.put(normalized, level);
        }
    }

    private static @NotNull TrustLists trustLists(@NotNull ClaimTrustSnapshot trust)
    {
        List<String> builders = new ArrayList<>();
        List<String> containers = new ArrayList<>();
        List<String> accessors = new ArrayList<>();
        List<String> managers = new ArrayList<>(trust.managerIdentifiers());
        for (Map.Entry<String, ClaimTrustLevel> entry : trust.permissionsByIdentifier().entrySet())
        {
            if (entry.getValue() == ClaimTrustLevel.BUILD)
            {
                builders.add(entry.getKey());
            }
            else if (entry.getValue() == ClaimTrustLevel.CONTAINER)
            {
                containers.add(entry.getKey());
            }
            else if (entry.getValue() == ClaimTrustLevel.ACCESS)
            {
                accessors.add(entry.getKey());
            }
            else if (entry.getValue() == ClaimTrustLevel.MANAGE)
            {
                managers.add(entry.getKey());
            }
        }
        return new TrustLists(builders, containers, accessors, managers);
    }

    private static @NotNull Map<String, Object> claimSection(@Nullable Object value, @NotNull String context)
            throws ClaimDocumentFormatException
    {
        if (!(value instanceof Map))
        {
            throw new ClaimDocumentFormatException("The " + context + " must be a YAML mapping.");
        }

        Map<String, Object> section = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
        {
            if (!(entry.getKey() instanceof String))
            {
                throw new ClaimDocumentFormatException(
                        "The " + context + " contains a non-string field name: " + entry.getKey() + "."
                );
            }
            section.put((String) entry.getKey(), entry.getValue());
        }
        return section;
    }

    private static @NotNull Map<String, Object> extraFields(@NotNull Map<String, Object> section)
    {
        Map<String, Object> extras = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.entrySet())
        {
            if (!KNOWN_FIELDS.contains(entry.getKey()))
            {
                extras.put(entry.getKey(), entry.getValue());
            }
        }
        return extras;
    }

    private static @NotNull BoundaryLocation boundary(
            @Nullable Object raw,
            @NotNull String field,
            long claimId)
            throws ClaimDocumentFormatException
    {
        if (!(raw instanceof String))
        {
            throw new ClaimDocumentFormatException("Claim " + claimId + " is missing " + field + ".");
        }
        String[] parts = ((String) raw).split(";", -1);
        if (parts.length != 4 || parts[0].trim().isEmpty())
        {
            throw new ClaimDocumentFormatException("Claim " + claimId + " has invalid " + field + ".");
        }
        try
        {
            return new BoundaryLocation(
                    parts[0].trim(),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim())
            );
        }
        catch (NumberFormatException exception)
        {
            throw new ClaimDocumentFormatException("Claim " + claimId + " has invalid " + field + ".", exception);
        }
    }

    private static @Nullable UUID owner(@Nullable Object raw, long claimId)
            throws ClaimDocumentFormatException
    {
        if (raw == null)
        {
            return null;
        }
        if (!(raw instanceof String))
        {
            throw new ClaimDocumentFormatException("Claim " + claimId + " has a non-string owner.");
        }
        String owner = ((String) raw).trim();
        if (owner.isEmpty())
        {
            return null;
        }
        try
        {
            return UUID.fromString(owner);
        }
        catch (IllegalArgumentException exception)
        {
            throw new ClaimDocumentFormatException("Claim " + claimId + " has invalid owner UUID " + owner + ".", exception);
        }
    }

    private static @NotNull List<OrthogonalPoint2i> shapeCorners(@Nullable Object raw, long claimId)
            throws ClaimDocumentFormatException
    {
        List<String> serialized = stringList(raw, SHAPE_CORNERS);
        if (serialized.isEmpty())
        {
            return Collections.emptyList();
        }

        List<OrthogonalPoint2i> corners = new ArrayList<>();
        for (String item : serialized)
        {
            String[] parts = item.split(",", -1);
            if (parts.length != 2)
            {
                throw new ClaimDocumentFormatException("Claim " + claimId + " has invalid shaped corner " + item + ".");
            }
            try
            {
                corners.add(new OrthogonalPoint2i(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim())
                ));
            }
            catch (NumberFormatException exception)
            {
                throw new ClaimDocumentFormatException(
                        "Claim " + claimId + " has invalid shaped corner " + item + ".",
                        exception
                );
            }
        }
        return corners;
    }

    private static @NotNull List<String> stringList(@Nullable Object raw, @NotNull String field)
            throws ClaimDocumentFormatException
    {
        if (raw == null)
        {
            return Collections.emptyList();
        }
        if (!(raw instanceof List))
        {
            throw new ClaimDocumentFormatException(field + " must be a YAML list.");
        }

        List<String> values = new ArrayList<>();
        for (Object item : (List<?>) raw)
        {
            if (!(item instanceof String))
            {
                throw new ClaimDocumentFormatException(field + " contains a non-string value.");
            }
            values.add((String) item);
        }
        return values;
    }

    private static boolean bool(@Nullable Object raw, boolean defaultValue, @NotNull String field)
            throws ClaimDocumentFormatException
    {
        if (raw == null)
        {
            return defaultValue;
        }
        if (raw instanceof Boolean)
        {
            return (Boolean) raw;
        }
        if (raw instanceof String)
        {
            String value = ((String) raw).trim();
            if ("true".equalsIgnoreCase(value)) return true;
            if ("false".equalsIgnoreCase(value)) return false;
        }
        throw new ClaimDocumentFormatException(field + " must be true or false.");
    }

    private static long longValue(@Nullable Object raw, long defaultValue, @NotNull String field)
            throws ClaimDocumentFormatException
    {
        Long value = optionalLong(raw, field);
        return value == null ? defaultValue : value;
    }

    private static @Nullable Long parentId(@Nullable Object raw) throws ClaimDocumentFormatException
    {
        Long value = optionalLong(raw, PARENT_ID);
        return value == null || value < 0L ? null : value;
    }

    private static @Nullable Long optionalLong(@Nullable Object raw, @NotNull String field)
            throws ClaimDocumentFormatException
    {
        if (raw == null)
        {
            return null;
        }
        if (raw instanceof Number)
        {
            try
            {
                return Long.parseLong(raw.toString());
            }
            catch (NumberFormatException exception)
            {
                throw new ClaimDocumentFormatException(field + " must be an integer.", exception);
            }
        }
        if (raw instanceof String)
        {
            String value = ((String) raw).trim();
            if (value.isEmpty())
            {
                return null;
            }
            try
            {
                return Long.parseLong(value);
            }
            catch (NumberFormatException exception)
            {
                throw new ClaimDocumentFormatException(field + " must be an integer.", exception);
            }
        }
        throw new ClaimDocumentFormatException(field + " must be an integer.");
    }

    private static @NotNull String location(@NotNull String world, int x, int y, int z)
    {
        return world + ";" + x + ";" + y + ";" + z;
    }

    private static @NotNull Long requireId(@NotNull ClaimDocument document, @NotNull String context)
            throws ClaimDocumentFormatException
    {
        Long id = document.snapshot().id();
        if (id == null || id < 0L)
        {
            throw new ClaimDocumentFormatException("The " + context + " is missing a valid id.");
        }
        return id;
    }

    private static void validateUniqueIds(@NotNull List<ClaimDocument> documents)
            throws ClaimDocumentFormatException
    {
        Set<Long> ids = new HashSet<>();
        for (ClaimDocument document : documents)
        {
            Long id = requireId(document, "claim");
            if (!ids.add(id))
            {
                throw new ClaimDocumentFormatException("Duplicate claim id " + id + ".");
            }
        }
    }

    private static @Nullable Object mutableValue(@Nullable Object value)
    {
        if (value instanceof Map)
        {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                copy.put(mutableValue(entry.getKey()), mutableValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List)
        {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value)
            {
                copy.add(mutableValue(item));
            }
            return copy;
        }
        if (value instanceof Set)
        {
            Set<Object> copy = new LinkedHashSet<>();
            for (Object item : (Set<?>) value)
            {
                copy.add(mutableValue(item));
            }
            return copy;
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

    private static final class BoundaryLocation
    {
        private final String world;
        private final int x;
        private final int y;
        private final int z;

        private BoundaryLocation(String world, int x, int y, int z)
        {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class TrustLists
    {
        private final List<String> builders;
        private final List<String> containers;
        private final List<String> accessors;
        private final List<String> managers;

        private TrustLists(
                List<String> builders,
                List<String> containers,
                List<String> accessors,
                List<String> managers)
        {
            this.builders = builders;
            this.containers = containers;
            this.accessors = accessors;
            this.managers = managers;
        }
    }
}
