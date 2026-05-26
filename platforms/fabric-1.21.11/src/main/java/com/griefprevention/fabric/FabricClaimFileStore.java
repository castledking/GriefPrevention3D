package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FabricClaimFileStore
{
    private static final String CLAIM_DATA_FOLDER = "ClaimData";
    private static final String PLAYER_DATA_FOLDER = "PlayerData";
    private static final String NEXT_CLAIM_ID_FILE = "_nextClaimID";
    private static final String CLAIM_EXTENSION = ".yml";

    private FabricClaimFileStore()
    {
    }

    static @NotNull LoadedClaims load(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        Path claimDataFolder = claimDataFolder(dataFolder);
        try
        {
            Files.createDirectories(claimDataFolder);
            Files.createDirectories(playerDataFolder(dataFolder));

            List<ClaimSnapshot> snapshots = new ArrayList<>();
            Map<Long, ClaimTrustSnapshot> trustByClaimId = new LinkedHashMap<>();
            List<Path> claimFiles = claimFiles(claimDataFolder);
            for (Path claimFile : claimFiles)
            {
                Long fileClaimId = claimIdFromFileName(claimFile);
                List<String> lines = Files.readAllLines(claimFile, StandardCharsets.UTF_8);
                ParsedClaim parsed = parseClaimSection(lines, 0, 0, fileClaimId, null, logger);
                if (parsed.record() != null)
                {
                    addLoadedClaim(parsed.record(), snapshots, trustByClaimId, logger);
                }
            }

            long nextClaimId = Math.max(readNextClaimId(claimDataFolder), highestClaimId(snapshots) + 1L);
            return new LoadedClaims(snapshots, trustByClaimId, nextClaimId);
        }
        catch (IOException e)
        {
            logger.warn("Could not load Fabric claims from {}. Native Fabric protection will start empty.",
                    dataFolder, e);
            return LoadedClaims.empty();
        }
    }

    static void save(
            @NotNull Path dataFolder,
            @NotNull Collection<ClaimSnapshot> snapshots,
            @NotNull Map<Long, ClaimTrustSnapshot> trustByClaimId,
            long nextClaimId)
            throws IOException
    {
        Path claimDataFolder = claimDataFolder(dataFolder);
        Files.createDirectories(claimDataFolder);
        Files.createDirectories(playerDataFolder(dataFolder));

        Map<Long, ClaimSnapshot> snapshotsById = snapshotsById(snapshots);
        Map<Long, List<ClaimSnapshot>> childrenByParent = childrenByParent(snapshots, snapshotsById);
        List<ClaimSnapshot> topLevelClaims = topLevelClaims(snapshots, snapshotsById);
        topLevelClaims.sort(Comparator.comparingLong(snapshot -> snapshot.id() == null ? Long.MAX_VALUE : snapshot.id()));

        Set<String> expectedFiles = new LinkedHashSet<>();
        for (ClaimSnapshot snapshot : topLevelClaims)
        {
            Long id = snapshot.id();
            if (id != null)
            {
                expectedFiles.add(id + CLAIM_EXTENSION);
            }
        }

        deleteStaleClaimFiles(claimDataFolder, expectedFiles);
        writeAtomically(claimDataFolder.resolve(NEXT_CLAIM_ID_FILE),
                Math.max(1L, nextClaimId) + System.lineSeparator());

        long modifiedDate = System.currentTimeMillis();
        for (ClaimSnapshot snapshot : topLevelClaims)
        {
            Long id = snapshot.id();
            if (id == null)
            {
                continue;
            }

            StringBuilder yaml = new StringBuilder();
            appendClaim(yaml, snapshot, trustByClaimId, childrenByParent, 0, modifiedDate);
            writeAtomically(claimDataFolder.resolve(id + CLAIM_EXTENSION), yaml.toString());
        }
    }

    private static @NotNull Path claimDataFolder(@NotNull Path dataFolder)
    {
        return dataFolder.resolve(CLAIM_DATA_FOLDER);
    }

    private static @NotNull Path playerDataFolder(@NotNull Path dataFolder)
    {
        return dataFolder.resolve(PLAYER_DATA_FOLDER);
    }

    private static @NotNull List<Path> claimFiles(@NotNull Path claimDataFolder) throws IOException
    {
        List<Path> claimFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(claimDataFolder, "*" + CLAIM_EXTENSION))
        {
            for (Path claimFile : stream)
            {
                String fileName = claimFile.getFileName().toString();
                if (!fileName.startsWith("_") && Files.isRegularFile(claimFile))
                {
                    claimFiles.add(claimFile);
                }
            }
        }
        claimFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return claimFiles;
    }

    private static @Nullable Long claimIdFromFileName(@NotNull Path claimFile)
    {
        String fileName = claimFile.getFileName().toString();
        if (!fileName.endsWith(CLAIM_EXTENSION))
        {
            return null;
        }

        return parseLong(fileName.substring(0, fileName.length() - CLAIM_EXTENSION.length()));
    }

    private static long readNextClaimId(@NotNull Path claimDataFolder) throws IOException
    {
        Path nextClaimIdFile = claimDataFolder.resolve(NEXT_CLAIM_ID_FILE);
        if (!Files.isRegularFile(nextClaimIdFile))
        {
            return 1L;
        }

        Long nextClaimId = parseLong(Files.readString(nextClaimIdFile, StandardCharsets.UTF_8).trim());
        return nextClaimId == null || nextClaimId < 1L ? 1L : nextClaimId;
    }

    private static long highestClaimId(@NotNull Collection<ClaimSnapshot> snapshots)
    {
        long highest = 0L;
        for (ClaimSnapshot snapshot : snapshots)
        {
            Long id = snapshot.id();
            if (id != null && id > highest)
            {
                highest = id;
            }
        }
        return highest;
    }

    private static @NotNull Map<Long, ClaimSnapshot> snapshotsById(
            @NotNull Collection<ClaimSnapshot> snapshots)
    {
        Map<Long, ClaimSnapshot> snapshotsById = new LinkedHashMap<>();
        for (ClaimSnapshot snapshot : snapshots)
        {
            Long id = snapshot.id();
            if (id != null)
            {
                snapshotsById.put(id, snapshot);
            }
        }
        return snapshotsById;
    }

    private static @NotNull Map<Long, List<ClaimSnapshot>> childrenByParent(
            @NotNull Collection<ClaimSnapshot> snapshots,
            @NotNull Map<Long, ClaimSnapshot> snapshotsById)
    {
        Map<Long, List<ClaimSnapshot>> childrenByParent = new LinkedHashMap<>();
        for (ClaimSnapshot snapshot : snapshots)
        {
            Long id = snapshot.id();
            Long parentId = snapshot.parentId();
            if (id != null && parentId != null && snapshotsById.containsKey(parentId))
            {
                childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(snapshot);
            }
        }
        for (List<ClaimSnapshot> children : childrenByParent.values())
        {
            children.sort(Comparator.comparingLong(snapshot -> snapshot.id() == null ? Long.MAX_VALUE : snapshot.id()));
        }
        return childrenByParent;
    }

    private static @NotNull List<ClaimSnapshot> topLevelClaims(
            @NotNull Collection<ClaimSnapshot> snapshots,
            @NotNull Map<Long, ClaimSnapshot> snapshotsById)
    {
        List<ClaimSnapshot> topLevelClaims = new ArrayList<>();
        for (ClaimSnapshot snapshot : snapshots)
        {
            Long parentId = snapshot.parentId();
            if (snapshot.id() != null && (parentId == null || !snapshotsById.containsKey(parentId)))
            {
                topLevelClaims.add(snapshot);
            }
        }
        return topLevelClaims;
    }

    private static void deleteStaleClaimFiles(@NotNull Path claimDataFolder, @NotNull Set<String> expectedFiles)
            throws IOException
    {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(claimDataFolder, "*" + CLAIM_EXTENSION))
        {
            for (Path claimFile : stream)
            {
                String fileName = claimFile.getFileName().toString();
                if (!fileName.startsWith("_") && !expectedFiles.contains(fileName))
                {
                    Files.deleteIfExists(claimFile);
                }
            }
        }
    }

    private static void writeAtomically(@NotNull Path file, @NotNull String contents) throws IOException
    {
        Path parent = file.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }

        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tempFile, contents, StandardCharsets.UTF_8);
        try
        {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException atomicMoveFailure)
        {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static @NotNull ParsedClaim parseClaimSection(
            @NotNull List<String> lines,
            int startIndex,
            int indent,
            @Nullable Long fallbackId,
            @Nullable Long inheritedParentId,
            @NotNull Logger logger)
    {
        ClaimRecord record = new ClaimRecord();
        record.id = fallbackId;
        record.parentId = inheritedParentId;

        int index = startIndex;
        while (index < lines.size())
        {
            String line = lines.get(index);
            if (isIgnoredLine(line))
            {
                index++;
                continue;
            }

            int currentIndent = indentation(line);
            if (currentIndent < indent)
            {
                break;
            }
            if (currentIndent > indent)
            {
                index++;
                continue;
            }

            KeyValue keyValue = keyValue(line.trim());
            if (keyValue == null)
            {
                index++;
                continue;
            }

            switch (keyValue.key())
            {
                case "Claim ID":
                    record.id = parseLong(unquote(keyValue.value()));
                    break;
                case "Lesser Boundary Corner":
                    record.lesser = parseLocation(keyValue.value(), record.id, logger);
                    break;
                case "Greater Boundary Corner":
                    record.greater = parseLocation(keyValue.value(), record.id, logger);
                    break;
                case "Owner":
                    record.owner = emptyToNull(unquote(keyValue.value()));
                    break;
                case "Builders":
                    ListRead builders = readList(lines, index, indent, keyValue.value());
                    record.builders = builders.values();
                    index = builders.nextIndex();
                    continue;
                case "Containers":
                    ListRead containers = readList(lines, index, indent, keyValue.value());
                    record.containers = containers.values();
                    index = containers.nextIndex();
                    continue;
                case "Accessors":
                    ListRead accessors = readList(lines, index, indent, keyValue.value());
                    record.accessors = accessors.values();
                    index = accessors.nextIndex();
                    continue;
                case "Managers":
                    ListRead managers = readList(lines, index, indent, keyValue.value());
                    record.managers = managers.values();
                    index = managers.nextIndex();
                    continue;
                case "Parent Claim ID":
                    record.parentId = parseParentClaimId(keyValue.value());
                    break;
                case "Is3D":
                    record.threeDimensional = Boolean.parseBoolean(unquote(keyValue.value()));
                    break;
                case "Children":
                    ChildrenRead children = readChildren(lines, index + 1, indent + 2, indent + 4,
                            record.id, logger);
                    record.children.addAll(children.children());
                    index = children.nextIndex();
                    continue;
                default:
                    break;
            }

            index++;
        }

        return new ParsedClaim(record, index);
    }

    private static @NotNull ListRead readList(
            @NotNull List<String> lines,
            int listKeyIndex,
            int listKeyIndent,
            @NotNull String value)
    {
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty())
        {
            List<String> values = new ArrayList<>();
            int index = listKeyIndex + 1;
            while (index < lines.size())
            {
                String line = lines.get(index);
                if (isIgnoredLine(line))
                {
                    index++;
                    continue;
                }

                int currentIndent = indentation(line);
                String trimmed = line.trim();
                if (currentIndent < listKeyIndent || (currentIndent == listKeyIndent && !trimmed.startsWith("-")))
                {
                    break;
                }
                if ((currentIndent == listKeyIndent || currentIndent == listKeyIndent + 2)
                        && trimmed.startsWith("-"))
                {
                    values.add(unquote(trimmed.substring(1).trim()));
                    index++;
                    continue;
                }
                if (currentIndent <= listKeyIndent)
                {
                    break;
                }

                index++;
            }
            return new ListRead(values, index);
        }

        if ("[]".equals(normalizedValue))
        {
            return new ListRead(Collections.emptyList(), listKeyIndex + 1);
        }

        if (normalizedValue.startsWith("[") && normalizedValue.endsWith("]"))
        {
            String inner = normalizedValue.substring(1, normalizedValue.length() - 1).trim();
            if (inner.isEmpty())
            {
                return new ListRead(Collections.emptyList(), listKeyIndex + 1);
            }

            List<String> values = new ArrayList<>();
            for (String item : inner.split(","))
            {
                values.add(unquote(item.trim()));
            }
            return new ListRead(values, listKeyIndex + 1);
        }

        return new ListRead(List.of(unquote(normalizedValue)), listKeyIndex + 1);
    }

    private static @NotNull ChildrenRead readChildren(
            @NotNull List<String> lines,
            int startIndex,
            int childKeyIndent,
            int childSectionIndent,
            @Nullable Long parentId,
            @NotNull Logger logger)
    {
        List<ClaimRecord> children = new ArrayList<>();
        int index = startIndex;
        while (index < lines.size())
        {
            String line = lines.get(index);
            if (isIgnoredLine(line))
            {
                index++;
                continue;
            }

            int currentIndent = indentation(line);
            if (currentIndent < childKeyIndent)
            {
                break;
            }

            if (currentIndent == childKeyIndent)
            {
                KeyValue childKey = keyValue(line.trim());
                if (childKey == null)
                {
                    index++;
                    continue;
                }

                ParsedClaim parsed = parseClaimSection(lines, index + 1, childSectionIndent,
                        parseLong(unquote(childKey.key())), parentId, logger);
                children.add(parsed.record());
                index = parsed.nextIndex();
                continue;
            }

            index++;
        }

        return new ChildrenRead(children, index);
    }

    private static void addLoadedClaim(
            @NotNull ClaimRecord record,
            @NotNull List<ClaimSnapshot> snapshots,
            @NotNull Map<Long, ClaimTrustSnapshot> trustByClaimId,
            @NotNull Logger logger)
    {
        LoadedClaim loaded = toLoadedClaim(record, logger);
        if (loaded != null)
        {
            snapshots.add(loaded.snapshot());
            trustByClaimId.put(loaded.snapshot().id(), loaded.trust());
        }

        for (ClaimRecord child : record.children)
        {
            addLoadedClaim(child, snapshots, trustByClaimId, logger);
        }
    }

    private static @Nullable LoadedClaim toLoadedClaim(@NotNull ClaimRecord record, @NotNull Logger logger)
    {
        if (record.id == null)
        {
            logger.warn("Skipping Fabric claim without an id.");
            return null;
        }

        if (record.lesser == null || record.greater == null)
        {
            logger.warn("Skipping Fabric claim {} without both boundary corners.", record.id);
            return null;
        }

        if (!record.lesser.world().equals(record.greater.world()))
        {
            logger.warn("Skipping Fabric claim {} with mismatched boundary worlds: {} and {}.",
                    record.id, record.lesser.world(), record.greater.world());
            return null;
        }

        UUID ownerId = parseUuid(record.owner, "owner", record.id, logger);
        ClaimBounds bounds = ClaimBounds.rectangle(
                record.lesser.x(),
                record.lesser.y(),
                record.lesser.z(),
                record.greater.x(),
                record.greater.y(),
                record.greater.z()
        );
        ClaimSnapshot snapshot = new ClaimSnapshot(
                record.id,
                record.lesser.world(),
                ownerId,
                record.parentId,
                bounds,
                record.threeDimensional,
                record.parentId != null
        );

        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                ownerId,
                toTrustMap(record),
                record.managers,
                Collections.emptyList()
        );
        return new LoadedClaim(snapshot, trust);
    }

    private static @NotNull Map<String, ClaimTrustLevel> toTrustMap(@NotNull ClaimRecord record)
    {
        Map<String, ClaimTrustLevel> trust = new LinkedHashMap<>();
        putTrust(trust, record.builders, ClaimTrustLevel.BUILD);
        putTrust(trust, record.containers, ClaimTrustLevel.CONTAINER);
        putTrust(trust, record.accessors, ClaimTrustLevel.ACCESS);
        return trust;
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

            ClaimTrustLevel existing = trust.get(normalized);
            if (existing == null || existing.isGrantedBy(level))
            {
                trust.put(normalized, level);
            }
        }
    }

    private static @Nullable UUID parseUuid(
            @Nullable String value,
            @NotNull String fieldName,
            long claimId,
            @NotNull Logger logger)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        try
        {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException e)
        {
            logger.warn("Ignoring invalid {} UUID '{}' in Fabric claim {}.", fieldName, value, claimId);
            return null;
        }
    }

    private static @Nullable BoundaryLocation parseLocation(
            @NotNull String value,
            @Nullable Long claimId,
            @NotNull Logger logger)
    {
        String[] parts = unquote(value).split(";");
        if (parts.length != 4)
        {
            logger.warn("Ignoring invalid boundary location '{}' in Fabric claim {}.", value,
                    claimId == null ? "unknown" : claimId);
            return null;
        }

        Integer x = parseInt(parts[1]);
        Integer y = parseInt(parts[2]);
        Integer z = parseInt(parts[3]);
        if (x == null || y == null || z == null || parts[0].isBlank())
        {
            logger.warn("Ignoring invalid boundary location '{}' in Fabric claim {}.", value,
                    claimId == null ? "unknown" : claimId);
            return null;
        }

        return new BoundaryLocation(parts[0].trim(), x, y, z);
    }

    private static @Nullable Long parseParentClaimId(@NotNull String value)
    {
        Long parentId = parseLong(unquote(value));
        return parentId == null || parentId < 0L ? null : parentId;
    }

    private static @Nullable Long parseLong(@Nullable String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        try
        {
            return Long.parseLong(value.trim());
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static @Nullable Integer parseInt(@Nullable String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static void appendClaim(
            @NotNull StringBuilder yaml,
            @NotNull ClaimSnapshot snapshot,
            @NotNull Map<Long, ClaimTrustSnapshot> trustByClaimId,
            @NotNull Map<Long, List<ClaimSnapshot>> childrenByParent,
            int indent,
            long modifiedDate)
    {
        String prefix = " ".repeat(indent);
        ClaimTrustSnapshot trust = snapshot.id() == null ? null : trustByClaimId.get(snapshot.id());
        TrustLists trustLists = trustLists(trust);

        yaml.append(prefix).append("Claim ID: '").append(snapshot.id()).append("'\n");
        yaml.append(prefix).append("Lesser Boundary Corner: ")
                .append(location(snapshot.worldKey(), snapshot.bounds().minX(), snapshot.bounds().minY(),
                        snapshot.bounds().minZ()))
                .append('\n');
        yaml.append(prefix).append("Greater Boundary Corner: ")
                .append(location(snapshot.worldKey(), snapshot.bounds().maxX(), snapshot.bounds().maxY(),
                        snapshot.bounds().maxZ()))
                .append('\n');
        yaml.append(prefix).append("Owner: ")
                .append(snapshot.ownerId() == null ? "''" : snapshot.ownerId())
                .append('\n');
        appendList(yaml, "Builders", trustLists.builders(), indent);
        appendList(yaml, "Containers", trustLists.containers(), indent);
        appendList(yaml, "Accessors", trustLists.accessors(), indent);
        appendList(yaml, "Managers", trustLists.managers(), indent);
        yaml.append(prefix).append("Parent Claim ID: ")
                .append(snapshot.parentId() == null ? "-1" : snapshot.parentId())
                .append('\n');
        yaml.append(prefix).append("inheritNothing: false\n");
        yaml.append(prefix).append("inheritNothingForNewSubdivisions: false\n");
        yaml.append(prefix).append("Is3D: ").append(snapshot.threeDimensional()).append('\n');
        yaml.append(prefix).append("Explosives Allowed: false\n");
        yaml.append(prefix).append("Wither Explosions Allowed: false\n");
        yaml.append(prefix).append("Modified Date: ").append(modifiedDate).append('\n');

        List<ClaimSnapshot> children = snapshot.id() == null
                ? Collections.emptyList()
                : childrenByParent.getOrDefault(snapshot.id(), Collections.emptyList());
        if (!children.isEmpty())
        {
            yaml.append(prefix).append("Children:\n");
            for (ClaimSnapshot child : children)
            {
                yaml.append(" ".repeat(indent + 2)).append("'").append(child.id()).append("':\n");
                appendClaim(yaml, child, trustByClaimId, childrenByParent, indent + 4, modifiedDate);
            }
        }
    }

    private static void appendList(
            @NotNull StringBuilder yaml,
            @NotNull String key,
            @NotNull Collection<String> values,
            int indent)
    {
        String prefix = " ".repeat(indent);
        if (values.isEmpty())
        {
            yaml.append(prefix).append(key).append(": []\n");
            return;
        }

        yaml.append(prefix).append(key).append(":\n");
        for (String value : values)
        {
            yaml.append(prefix).append("- ").append(value).append('\n');
        }
    }

    private static @NotNull String location(@NotNull String worldKey, int x, int y, int z)
    {
        return worldKey + ";" + x + ";" + y + ";" + z;
    }

    private static @NotNull TrustLists trustLists(@Nullable ClaimTrustSnapshot trust)
    {
        if (trust == null)
        {
            return new TrustLists(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        List<String> builders = new ArrayList<>();
        List<String> containers = new ArrayList<>();
        List<String> accessors = new ArrayList<>();
        List<String> managers = new ArrayList<>(trust.managerIdentifiers());
        for (Map.Entry<String, ClaimTrustLevel> entry : trust.permissionsByIdentifier().entrySet())
        {
            ClaimTrustLevel level = entry.getValue();
            if (level == ClaimTrustLevel.BUILD)
            {
                builders.add(entry.getKey());
            }
            else if (level == ClaimTrustLevel.CONTAINER)
            {
                containers.add(entry.getKey());
            }
            else if (level == ClaimTrustLevel.ACCESS)
            {
                accessors.add(entry.getKey());
            }
            else if (level == ClaimTrustLevel.MANAGE)
            {
                managers.add(entry.getKey());
            }
        }
        return new TrustLists(builders, containers, accessors, managers);
    }

    private static @Nullable KeyValue keyValue(@NotNull String line)
    {
        int colon = line.indexOf(':');
        if (colon < 0)
        {
            return null;
        }

        return new KeyValue(unquote(line.substring(0, colon).trim()), line.substring(colon + 1).trim());
    }

    private static boolean isIgnoredLine(@NotNull String line)
    {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static int indentation(@NotNull String line)
    {
        int indentation = 0;
        while (indentation < line.length() && line.charAt(indentation) == ' ')
        {
            indentation++;
        }
        return indentation;
    }

    private static @Nullable String emptyToNull(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value;
    }

    private static @NotNull String unquote(@NotNull String value)
    {
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))))
        {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    static final class LoadedClaims
    {
        private final @NotNull List<ClaimSnapshot> snapshots;
        private final @NotNull Map<Long, ClaimTrustSnapshot> trustByClaimId;
        private final long nextClaimId;

        private LoadedClaims(
                @NotNull List<ClaimSnapshot> snapshots,
                @NotNull Map<Long, ClaimTrustSnapshot> trustByClaimId,
                long nextClaimId)
        {
            this.snapshots = Collections.unmodifiableList(new ArrayList<>(snapshots));
            this.trustByClaimId = Collections.unmodifiableMap(new LinkedHashMap<>(trustByClaimId));
            this.nextClaimId = nextClaimId;
        }

        static @NotNull LoadedClaims empty()
        {
            return new LoadedClaims(Collections.emptyList(), Collections.emptyMap(), 1L);
        }

        @NotNull List<ClaimSnapshot> snapshots()
        {
            return this.snapshots;
        }

        @NotNull Map<Long, ClaimTrustSnapshot> trustByClaimId()
        {
            return this.trustByClaimId;
        }

        long nextClaimId()
        {
            return this.nextClaimId;
        }
    }

    private static final class ClaimRecord
    {
        private @Nullable Long id;
        private @Nullable BoundaryLocation lesser;
        private @Nullable BoundaryLocation greater;
        private @Nullable String owner;
        private @Nullable Long parentId;
        private boolean threeDimensional;
        private @NotNull List<String> builders = Collections.emptyList();
        private @NotNull List<String> containers = Collections.emptyList();
        private @NotNull List<String> accessors = Collections.emptyList();
        private @NotNull List<String> managers = Collections.emptyList();
        private final @NotNull List<ClaimRecord> children = new ArrayList<>();
    }

    private static final class LoadedClaim
    {
        private final @NotNull ClaimSnapshot snapshot;
        private final @NotNull ClaimTrustSnapshot trust;

        private LoadedClaim(@NotNull ClaimSnapshot snapshot, @NotNull ClaimTrustSnapshot trust)
        {
            this.snapshot = snapshot;
            this.trust = trust;
        }

        private @NotNull ClaimSnapshot snapshot()
        {
            return this.snapshot;
        }

        private @NotNull ClaimTrustSnapshot trust()
        {
            return this.trust;
        }
    }

    private static final class ParsedClaim
    {
        private final @NotNull ClaimRecord record;
        private final int nextIndex;

        private ParsedClaim(@NotNull ClaimRecord record, int nextIndex)
        {
            this.record = record;
            this.nextIndex = nextIndex;
        }

        private @NotNull ClaimRecord record()
        {
            return this.record;
        }

        private int nextIndex()
        {
            return this.nextIndex;
        }
    }

    private static final class ListRead
    {
        private final @NotNull List<String> values;
        private final int nextIndex;

        private ListRead(@NotNull List<String> values, int nextIndex)
        {
            this.values = values;
            this.nextIndex = nextIndex;
        }

        private @NotNull List<String> values()
        {
            return this.values;
        }

        private int nextIndex()
        {
            return this.nextIndex;
        }
    }

    private static final class ChildrenRead
    {
        private final @NotNull List<ClaimRecord> children;
        private final int nextIndex;

        private ChildrenRead(@NotNull List<ClaimRecord> children, int nextIndex)
        {
            this.children = children;
            this.nextIndex = nextIndex;
        }

        private @NotNull List<ClaimRecord> children()
        {
            return this.children;
        }

        private int nextIndex()
        {
            return this.nextIndex;
        }
    }

    private static final class KeyValue
    {
        private final @NotNull String key;
        private final @NotNull String value;

        private KeyValue(@NotNull String key, @NotNull String value)
        {
            this.key = key;
            this.value = value;
        }

        private @NotNull String key()
        {
            return this.key;
        }

        private @NotNull String value()
        {
            return this.value;
        }
    }

    private static final class BoundaryLocation
    {
        private final @NotNull String world;
        private final int x;
        private final int y;
        private final int z;

        private BoundaryLocation(@NotNull String world, int x, int y, int z)
        {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private @NotNull String world()
        {
            return this.world;
        }

        private int x()
        {
            return this.x;
        }

        private int y()
        {
            return this.y;
        }

        private int z()
        {
            return this.z;
        }
    }

    private static final class TrustLists
    {
        private final @NotNull List<String> builders;
        private final @NotNull List<String> containers;
        private final @NotNull List<String> accessors;
        private final @NotNull List<String> managers;

        private TrustLists(
                @NotNull List<String> builders,
                @NotNull List<String> containers,
                @NotNull List<String> accessors,
                @NotNull List<String> managers)
        {
            this.builders = builders;
            this.containers = containers;
            this.accessors = accessors;
            this.managers = managers;
        }

        private @NotNull List<String> builders()
        {
            return this.builders;
        }

        private @NotNull List<String> containers()
        {
            return this.containers;
        }

        private @NotNull List<String> accessors()
        {
            return this.accessors;
        }

        private @NotNull List<String> managers()
        {
            return this.managers;
        }
    }
}
