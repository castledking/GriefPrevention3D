package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.persistence.ClaimDataSchema;
import com.griefprevention.persistence.ClaimDocument;
import com.griefprevention.persistence.ClaimDocumentCodec;
import com.griefprevention.persistence.ClaimDocumentFormatException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
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

/**
 * Fabric adapter for GriefPrevention's flat-file claim datastore.
 *
 * <p>All claim semantics are decoded and encoded by {@link ClaimDocumentCodec}. This class owns
 * filesystem layout, graph validation, migration backups, and atomic ClaimData promotion only.
 */
final class FabricClaimFileStore
{
    private static final String CLAIM_DATA_FOLDER = "ClaimData";
    private static final String PLAYER_DATA_FOLDER = "PlayerData";
    private static final String NEXT_CLAIM_ID_FILE = "_nextClaimID";
    private static final String SCHEMA_VERSION_FILE = "_schemaVersion";
    private static final String CLAIM_EXTENSION = ".yml";
    private static final String MIGRATION_BACKUP_FOLDER = "MigrationBackups";
    private static final ClaimDocumentCodec CODEC = new ClaimDocumentCodec();

    private FabricClaimFileStore()
    {
    }

    static @NotNull LoadedClaims load(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        Path claimDataFolder = claimDataFolder(dataFolder);
        Path playerDataFolder = playerDataFolder(dataFolder);
        try
        {
            Files.createDirectories(claimDataFolder);
            Files.createDirectories(playerDataFolder);

            SchemaState schema = readSchemaState(dataFolder);
            ClaimReadResult readResult = readClaimDocuments(claimDataFolder);
            List<ClaimDocument> documents = validateAndOrder(readResult.documents());
            long nextClaimId = nextClaimId(claimDataFolder, documents);

            boolean unversionedData = schema.version() == null
                    && (!documents.isEmpty()
                    || Files.isRegularFile(claimDataFolder.resolve(NEXT_CLAIM_ID_FILE))
                    || hasEntries(playerDataFolder));
            boolean olderSchema = schema.version() != null
                    && schema.version() < ClaimDataSchema.CURRENT_VERSION;
            boolean needsMigration = unversionedData || olderSchema || readResult.needsNormalization();
            if (needsMigration)
            {
                String source = schema.version() == null ? "unversioned" : "schema-" + schema.version();
                if (readResult.needsNormalization())
                {
                    source += "-claim-layout";
                }
                Path backup = createMigrationBackup(dataFolder, source);
                save(dataFolder, documents, nextClaimId);
                logger.info(
                        "Migrated Fabric claim data to schema {} after validation; backup: {}.",
                        ClaimDataSchema.CURRENT_VERSION,
                        backup
                );
            }

            return new LoadedClaims(documents, nextClaimId);
        }
        catch (IOException | ClaimDocumentFormatException exception)
        {
            throw new IllegalStateException(
                    "Could not safely load GriefPrevention claim data from " + dataFolder
                            + "; Fabric protection was not started.",
                    exception
            );
        }
    }

    static void save(
            @NotNull Path dataFolder,
            @NotNull Collection<ClaimDocument> sourceDocuments,
            long requestedNextClaimId)
            throws IOException
    {
        final List<ClaimDocument> documents;
        try
        {
            documents = validateAndOrder(sourceDocuments);
        }
        catch (ClaimDocumentFormatException exception)
        {
            throw new IOException("Refusing to save an invalid claim graph: " + exception.getMessage(), exception);
        }

        Files.createDirectories(dataFolder);
        Files.createDirectories(playerDataFolder(dataFolder));

        Map<Long, ClaimDocument> byId = documentsById(documents);
        Map<Long, List<ClaimDocument>> childrenByParent = childrenByParent(documents);
        Map<String, String> encodedFiles = new LinkedHashMap<>();
        for (ClaimDocument document : documents)
        {
            if (document.snapshot().parentId() != null)
            {
                continue;
            }

            Long rootId = java.util.Objects.requireNonNull(document.snapshot().id());
            List<ClaimDocument> tree = new ArrayList<>();
            collectTree(document, childrenByParent, tree);
            final String encoded;
            try
            {
                encoded = CODEC.encodeTree(document, tree);
                validateEncodedTree(encoded, rootId, tree);
            }
            catch (ClaimDocumentFormatException exception)
            {
                throw new IOException("Could not encode claim tree " + rootId + ".", exception);
            }
            encodedFiles.put(rootId + CLAIM_EXTENSION, encoded);
        }

        final long nextClaimId;
        try
        {
            nextClaimId = safeNextClaimId(byId.keySet(), requestedNextClaimId);
        }
        catch (ClaimDocumentFormatException exception)
        {
            throw new IOException("Refusing to save an invalid next claim id.", exception);
        }
        encodedFiles.put(NEXT_CLAIM_ID_FILE, String.valueOf(nextClaimId));
        promoteClaimDataDirectory(dataFolder, encodedFiles);
        writeAtomically(
                dataFolder.resolve(SCHEMA_VERSION_FILE),
                String.valueOf(ClaimDataSchema.CURRENT_VERSION)
        );
    }

    private static @NotNull ClaimReadResult readClaimDocuments(@NotNull Path claimDataFolder)
            throws IOException, ClaimDocumentFormatException
    {
        List<Path> claimFiles = claimFiles(claimDataFolder);
        List<ClaimDocument> documents = new ArrayList<>();
        boolean needsNormalization = false;
        for (Path claimFile : claimFiles)
        {
            long fileClaimId = claimIdFromFileName(claimFile);
            String input = Files.readString(claimFile, StandardCharsets.UTF_8);
            List<ClaimDocument> decoded = CODEC.decodeTree(
                    input,
                    fileClaimId,
                    Files.getLastModifiedTime(claimFile).toMillis()
            );
            if (!decoded.isEmpty() && decoded.get(0).snapshot().parentId() != null)
            {
                needsNormalization = true;
            }
            documents.addAll(decoded);
        }
        return new ClaimReadResult(documents, needsNormalization);
    }

    private static @NotNull List<Path> claimFiles(@NotNull Path claimDataFolder)
            throws IOException, ClaimDocumentFormatException
    {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(claimDataFolder))
        {
            for (Path entry : stream)
            {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                {
                    continue;
                }

                String name = entry.getFileName().toString();
                if (name.startsWith("_") || name.startsWith("."))
                {
                    continue;
                }
                if (!name.endsWith(CLAIM_EXTENSION))
                {
                    throw new ClaimDocumentFormatException(
                            "Unrecognized claim data file " + name
                                    + "; refusing to delete or ignore possible legacy data."
                    );
                }

                claimIdFromFileName(entry);
                result.add(entry);
            }
        }
        result.sort(Comparator.comparingLong(path -> {
            try
            {
                return claimIdFromFileName(path);
            }
            catch (ClaimDocumentFormatException exception)
            {
                throw new IllegalStateException(exception);
            }
        }));
        return result;
    }

    private static long claimIdFromFileName(@NotNull Path claimFile) throws ClaimDocumentFormatException
    {
        String name = claimFile.getFileName().toString();
        String rawId = name.substring(0, name.length() - CLAIM_EXTENSION.length());
        try
        {
            long id = Long.parseLong(rawId);
            if (id < 0L)
            {
                throw new NumberFormatException("negative id");
            }
            return id;
        }
        catch (NumberFormatException exception)
        {
            throw new ClaimDocumentFormatException(
                    "Claim filename " + name + " does not contain a valid numeric claim id.",
                    exception
            );
        }
    }

    private static @NotNull SchemaState readSchemaState(@NotNull Path dataFolder)
            throws IOException, ClaimDocumentFormatException
    {
        Path schemaFile = dataFolder.resolve(SCHEMA_VERSION_FILE);
        if (!Files.exists(schemaFile))
        {
            return new SchemaState(null);
        }
        if (!Files.isRegularFile(schemaFile, LinkOption.NOFOLLOW_LINKS))
        {
            throw new ClaimDocumentFormatException(SCHEMA_VERSION_FILE + " is not a regular file.");
        }

        String value = Files.readString(schemaFile, StandardCharsets.UTF_8).trim();
        final int version;
        try
        {
            version = Integer.parseInt(value);
        }
        catch (NumberFormatException exception)
        {
            throw new ClaimDocumentFormatException("Invalid datastore schema version '" + value + "'.", exception);
        }
        if (version < 0)
        {
            throw new ClaimDocumentFormatException("Datastore schema version cannot be negative.");
        }
        if (version > ClaimDataSchema.CURRENT_VERSION)
        {
            throw new ClaimDocumentFormatException(
                    "Datastore schema " + version + " is newer than supported schema "
                            + ClaimDataSchema.CURRENT_VERSION + "."
            );
        }
        return new SchemaState(version);
    }

    private static long nextClaimId(
            @NotNull Path claimDataFolder,
            @NotNull Collection<ClaimDocument> documents)
            throws IOException, ClaimDocumentFormatException
    {
        long stored = 0L;
        Path nextClaimIdFile = claimDataFolder.resolve(NEXT_CLAIM_ID_FILE);
        if (Files.exists(nextClaimIdFile))
        {
            if (!Files.isRegularFile(nextClaimIdFile, LinkOption.NOFOLLOW_LINKS))
            {
                throw new ClaimDocumentFormatException(NEXT_CLAIM_ID_FILE + " is not a regular file.");
            }
            String raw = Files.readString(nextClaimIdFile, StandardCharsets.UTF_8).trim();
            try
            {
                stored = Long.parseLong(raw);
            }
            catch (NumberFormatException exception)
            {
                throw new ClaimDocumentFormatException("Invalid next claim id '" + raw + "'.", exception);
            }
            if (stored < 0L)
            {
                throw new ClaimDocumentFormatException("Next claim id cannot be negative.");
            }
        }

        return safeNextClaimId(documentsById(documents).keySet(), stored);
    }

    private static long safeNextClaimId(@NotNull Collection<Long> ids, long requested)
            throws ClaimDocumentFormatException
    {
        if (requested < 0L)
        {
            throw new ClaimDocumentFormatException("Next claim id cannot be negative.");
        }

        long highest = -1L;
        for (Long id : ids)
        {
            highest = Math.max(highest, id);
        }
        if (highest == Long.MAX_VALUE)
        {
            throw new ClaimDocumentFormatException("No claim id remains after " + Long.MAX_VALUE + ".");
        }
        return Math.max(requested, highest + 1L);
    }

    private static @NotNull List<ClaimDocument> validateAndOrder(
            @NotNull Collection<ClaimDocument> source)
            throws ClaimDocumentFormatException
    {
        Map<Long, ClaimDocument> byId = new LinkedHashMap<>();
        for (ClaimDocument document : source)
        {
            Long id = requireId(document);
            if (byId.put(id, document) != null)
            {
                throw new ClaimDocumentFormatException("Duplicate claim id " + id + ".");
            }
            if (document.snapshot().subdivision() != (document.snapshot().parentId() != null))
            {
                throw new ClaimDocumentFormatException(
                        "Claim " + id + " has inconsistent subdivision and parent state."
                );
            }
            if (!java.util.Objects.equals(document.snapshot().ownerId(), document.trust().ownerId()))
            {
                throw new ClaimDocumentFormatException("Claim " + id + " has trust for a different owner.");
            }
        }

        Map<Long, List<ClaimDocument>> children = new LinkedHashMap<>();
        List<ClaimDocument> roots = new ArrayList<>();
        for (ClaimDocument document : byId.values())
        {
            Long id = requireId(document);
            Long parentId = document.snapshot().parentId();
            if (parentId == null)
            {
                roots.add(document);
                continue;
            }

            ClaimDocument parent = byId.get(parentId);
            if (parent == null)
            {
                throw new ClaimDocumentFormatException(
                        "Claim " + id + " references missing parent " + parentId + "."
                );
            }
            if (!document.snapshot().worldKey().equals(parent.snapshot().worldKey()))
            {
                throw new ClaimDocumentFormatException(
                        "Claim " + id + " is in a different world from parent " + parentId + "."
                );
            }
            children.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(document);
        }

        Comparator<ClaimDocument> byClaimId = Comparator.comparingLong(document -> {
            try
            {
                return requireId(document);
            }
            catch (ClaimDocumentFormatException exception)
            {
                throw new IllegalStateException(exception);
            }
        });
        roots.sort(byClaimId);
        for (List<ClaimDocument> childList : children.values())
        {
            childList.sort(byClaimId);
        }

        List<ClaimDocument> ordered = new ArrayList<>();
        Set<Long> visiting = new LinkedHashSet<>();
        Set<Long> visited = new LinkedHashSet<>();
        for (ClaimDocument root : roots)
        {
            appendValidatedTree(root, children, visiting, visited, ordered);
        }
        if (visited.size() != byId.size())
        {
            Set<Long> unreachable = new LinkedHashSet<>(byId.keySet());
            unreachable.removeAll(visited);
            throw new ClaimDocumentFormatException(
                    "Claim graph contains a cycle or unreachable claims: " + unreachable + "."
            );
        }
        return Collections.unmodifiableList(ordered);
    }

    private static void appendValidatedTree(
            @NotNull ClaimDocument document,
            @NotNull Map<Long, List<ClaimDocument>> children,
            @NotNull Set<Long> visiting,
            @NotNull Set<Long> visited,
            @NotNull List<ClaimDocument> ordered)
            throws ClaimDocumentFormatException
    {
        Long id = requireId(document);
        if (!visiting.add(id))
        {
            throw new ClaimDocumentFormatException("Claim graph contains a cycle at claim " + id + ".");
        }
        if (!visited.add(id))
        {
            throw new ClaimDocumentFormatException("Claim graph reaches claim " + id + " more than once.");
        }

        ordered.add(document);
        for (ClaimDocument child : children.getOrDefault(id, Collections.emptyList()))
        {
            appendValidatedTree(child, children, visiting, visited, ordered);
        }
        visiting.remove(id);
    }

    private static @NotNull Long requireId(@NotNull ClaimDocument document)
            throws ClaimDocumentFormatException
    {
        Long id = document.snapshot().id();
        if (id == null || id < 0L)
        {
            throw new ClaimDocumentFormatException("Claim is missing a valid id.");
        }
        return id;
    }

    private static @NotNull Map<Long, ClaimDocument> documentsById(
            @NotNull Collection<ClaimDocument> documents)
    {
        Map<Long, ClaimDocument> result = new LinkedHashMap<>();
        for (ClaimDocument document : documents)
        {
            result.put(document.snapshot().id(), document);
        }
        return result;
    }

    private static @NotNull Map<Long, List<ClaimDocument>> childrenByParent(
            @NotNull Collection<ClaimDocument> documents)
    {
        Map<Long, List<ClaimDocument>> result = new LinkedHashMap<>();
        for (ClaimDocument document : documents)
        {
            Long parentId = document.snapshot().parentId();
            if (parentId != null)
            {
                result.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(document);
            }
        }
        return result;
    }

    private static void collectTree(
            @NotNull ClaimDocument document,
            @NotNull Map<Long, List<ClaimDocument>> children,
            @NotNull List<ClaimDocument> output)
    {
        output.add(document);
        Long id = document.snapshot().id();
        for (ClaimDocument child : children.getOrDefault(id, Collections.emptyList()))
        {
            collectTree(child, children, output);
        }
    }

    private static void validateEncodedTree(
            @NotNull String encoded,
            long rootId,
            @NotNull Collection<ClaimDocument> expected)
            throws ClaimDocumentFormatException
    {
        List<ClaimDocument> decoded = CODEC.decodeTree(encoded, rootId, 0L);
        if (!documentsById(decoded).equals(documentsById(expected)))
        {
            throw new ClaimDocumentFormatException(
                    "Claim tree " + rootId + " changed semantics during serialization."
            );
        }
    }

    private static void promoteClaimDataDirectory(
            @NotNull Path dataFolder,
            @NotNull Map<String, String> encodedFiles)
            throws IOException
    {
        Path live = claimDataFolder(dataFolder);
        String token = UUID.randomUUID().toString();
        Path staged = dataFolder.resolve("." + CLAIM_DATA_FOLDER + ".stage-" + token);
        Path previous = dataFolder.resolve("." + CLAIM_DATA_FOLDER + ".previous-" + token);
        boolean liveMoved = false;
        boolean promoted = false;
        try
        {
            Files.createDirectory(staged);
            copyPreservedClaimData(live, staged);
            for (Map.Entry<String, String> entry : encodedFiles.entrySet())
            {
                Files.writeString(
                        staged.resolve(entry.getKey()),
                        entry.getValue(),
                        StandardCharsets.UTF_8
                );
            }

            if (Files.exists(live))
            {
                moveDirectory(live, previous);
                liveMoved = true;
            }
            try
            {
                moveDirectory(staged, live);
                promoted = true;
            }
            catch (IOException promotionFailure)
            {
                if (liveMoved && !Files.exists(live))
                {
                    try
                    {
                        moveDirectory(previous, live);
                        liveMoved = false;
                    }
                    catch (IOException restoreFailure)
                    {
                        promotionFailure.addSuppressed(restoreFailure);
                    }
                }
                throw promotionFailure;
            }
        }
        finally
        {
            if (!promoted)
            {
                deleteRecursivelyQuietly(staged);
            }
            if (promoted && liveMoved)
            {
                deleteRecursivelyQuietly(previous);
            }
        }
    }

    private static void copyPreservedClaimData(@NotNull Path source, @NotNull Path target)
            throws IOException
    {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
        {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source))
        {
            for (Path entry : stream)
            {
                String name = entry.getFileName().toString();
                boolean generatedClaim = name.endsWith(CLAIM_EXTENSION) && numericClaimFileName(name);
                if (generatedClaim || NEXT_CLAIM_ID_FILE.equals(name))
                {
                    continue;
                }
                copyRecursively(entry, target.resolve(name));
            }
        }
    }

    private static boolean numericClaimFileName(@NotNull String name)
    {
        try
        {
            Long.parseLong(name.substring(0, name.length() - CLAIM_EXTENSION.length()));
            return true;
        }
        catch (NumberFormatException exception)
        {
            return false;
        }
    }

    private static void moveDirectory(@NotNull Path source, @NotNull Path target) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception)
        {
            Files.move(source, target);
        }
    }

    private static void writeAtomically(@NotNull Path target, @NotNull String contents) throws IOException
    {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName(), ".tmp");
        try
        {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try
            {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    private static @NotNull Path createMigrationBackup(
            @NotNull Path dataFolder,
            @NotNull String sourceLabel)
            throws IOException
    {
        Path backups = dataFolder.resolve(MIGRATION_BACKUP_FOLDER);
        Files.createDirectories(backups);
        String baseName = sourceLabel + "-" + System.currentTimeMillis();
        Path backup = backups.resolve(baseName);
        int suffix = 1;
        while (Files.exists(backup))
        {
            backup = backups.resolve(baseName + "-" + suffix++);
        }
        Files.createDirectory(backup);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataFolder))
        {
            for (Path entry : stream)
            {
                String name = entry.getFileName().toString();
                if (MIGRATION_BACKUP_FOLDER.equals(name)
                        || name.startsWith("." + CLAIM_DATA_FOLDER + ".stage-")
                        || name.startsWith("." + CLAIM_DATA_FOLDER + ".previous-"))
                {
                    continue;
                }
                copyRecursively(entry, backup.resolve(name));
            }
        }
        return backup;
    }

    static void copyRecursively(@NotNull Path source, @NotNull Path target) throws IOException
    {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
        {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }

        Files.walkFileTree(source, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException
            {
                Path relative = source.relativize(directory);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
            {
                Path relative = source.relativize(file);
                Files.copy(
                        file,
                        target.resolve(relative),
                        LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.COPY_ATTRIBUTES
                );
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteRecursivelyQuietly(@NotNull Path path)
    {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
        {
            return;
        }
        try
        {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
                {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                        throws IOException
                {
                    if (exception != null)
                    {
                        throw exception;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException ignored)
        {
            // A uniquely named previous/staging directory is safe to leave for manual recovery.
        }
    }

    private static boolean hasEntries(@NotNull Path directory) throws IOException
    {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory))
        {
            return stream.iterator().hasNext();
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

    static final class LoadedClaims
    {
        private final @NotNull List<ClaimDocument> documents;
        private final @NotNull List<ClaimSnapshot> snapshots;
        private final @NotNull Map<Long, ClaimTrustSnapshot> trustByClaimId;
        private final long nextClaimId;

        private LoadedClaims(@NotNull List<ClaimDocument> documents, long nextClaimId)
        {
            this.documents = Collections.unmodifiableList(new ArrayList<>(documents));
            List<ClaimSnapshot> snapshots = new ArrayList<>();
            Map<Long, ClaimTrustSnapshot> trust = new LinkedHashMap<>();
            for (ClaimDocument document : documents)
            {
                snapshots.add(document.snapshot());
                trust.put(document.snapshot().id(), document.trust());
            }
            this.snapshots = Collections.unmodifiableList(snapshots);
            this.trustByClaimId = Collections.unmodifiableMap(trust);
            this.nextClaimId = nextClaimId;
        }

        @NotNull List<ClaimDocument> documents()
        {
            return this.documents;
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

    private static final class ClaimReadResult
    {
        private final @NotNull List<ClaimDocument> documents;
        private final boolean needsNormalization;

        private ClaimReadResult(@NotNull List<ClaimDocument> documents, boolean needsNormalization)
        {
            this.documents = documents;
            this.needsNormalization = needsNormalization;
        }

        private @NotNull List<ClaimDocument> documents()
        {
            return this.documents;
        }

        private boolean needsNormalization()
        {
            return this.needsNormalization;
        }
    }

    private static final class SchemaState
    {
        private final @Nullable Integer version;

        private SchemaState(@Nullable Integer version)
        {
            this.version = version;
        }

        private @Nullable Integer version()
        {
            return this.version;
        }
    }
}
