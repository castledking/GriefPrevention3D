/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import com.griefprevention.geometry.OrthogonalPoint2i;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Folia-safe, pooled, transactional JDBC data store supporting MySQL, MariaDB, and SQLite.
 *
 * <p>All mutations capture immutable snapshots on the calling thread and execute asynchronously on
 * an ordered background writer thread inside JDBC transactions so Folia region threads and global
 * tick threads never block on network I/O. Concurrent reads (such as async player pre-login and
 * background tasks) borrow dedicated connections from {@link DatabaseConnectionPool}.</p>
 */
public class DatabaseDataStore extends DataStore
{
    private static final String TABLE_CLAIM_DATA = "griefprevention_claimdata";
    private static final String TABLE_PLAYER_DATA = "griefprevention_playerdata";
    private static final String TABLE_NEXT_CLAIM_ID = "griefprevention_nextclaimid";
    private static final String TABLE_SCHEMA_VERSION = "griefprevention_schemaversion";

    // A brand new database is stamped at the latest schema, which skips every versioned migration
    // in initialize(). This has to list the full current column set or the first claim insert fails
    // against columns that were never added.
    static final String SQL_CREATE_CLAIM_TABLE =
            "CREATE TABLE IF NOT EXISTS griefprevention_claimdata ("
                    + "id BIGINT PRIMARY KEY, "
                    + "owner VARCHAR(50), "
                    + "lessercorner VARCHAR(100), "
                    + "greatercorner VARCHAR(100), "
                    + "builders TEXT, "
                    + "containers TEXT, "
                    + "accessors TEXT, "
                    + "managers TEXT, "
                    + "denied TEXT, "
                    + "inheritnothing BOOLEAN, "
                    + "parentid BIGINT, "
                    + "expiration BIGINT, "
                    + "explosivesallowed BOOLEAN, "
                    + "inheritnothingfornewsubdivisions BOOLEAN, "
                    + "witherexplosionsallowed BOOLEAN, "
                    + "is3d BOOLEAN DEFAULT 0, "
                    + "shapecorners TEXT, "
                    + "modifieddate BIGINT DEFAULT 0, "
                    + "pvpenabled BOOLEAN DEFAULT 1, "
                    + "alertsenabled BOOLEAN DEFAULT 1, "
                    + "adminsubdivision BOOLEAN DEFAULT 0, "
                    + "pvptrusted TEXT, "
                    + "pvetrusted TEXT, "
                    + "pvptoggled BOOLEAN DEFAULT 0, "
                    + "neighbors TEXT, "
                    + "allowallneighbors BOOLEAN DEFAULT 0"
                    + ")";

    static final String SQL_INSERT_CLAIM =
            "INSERT INTO griefprevention_claimdata ("
                    + "id, owner, lessercorner, greatercorner, builders, containers, accessors, managers, denied, "
                    + "inheritnothing, inheritnothingfornewsubdivisions, parentid, expiration, explosivesallowed, "
                    + "witherexplosionsallowed, is3d, shapecorners, modifieddate, pvpenabled, alertsenabled, "
                    + "adminsubdivision, pvptrusted, pvetrusted, pvptoggled, neighbors, allowallneighbors"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_CREATE_PLAYER_TABLE =
            "CREATE TABLE IF NOT EXISTS griefprevention_playerdata ("
                    + "name VARCHAR(50) PRIMARY KEY, "
                    + "lastlogin DATETIME, "
                    + "accruedblocks INTEGER, "
                    + "bonusblocks INTEGER, "
                    + "glowenabled BOOLEAN DEFAULT 1"
                    + ")";

    private static final String SQL_CREATE_NEXT_CLAIM_ID_TABLE =
            "CREATE TABLE IF NOT EXISTS griefprevention_nextclaimid (nextid BIGINT)";

    private static final String SQL_CREATE_SCHEMA_VERSION_TABLE =
            "CREATE TABLE IF NOT EXISTS griefprevention_schemaversion (version INTEGER)";

    private static final String SQL_DELETE_CLAIM =
            "DELETE FROM griefprevention_claimdata WHERE id = ?";
    private static final String SQL_SELECT_PLAYER_DATA =
            "SELECT * FROM griefprevention_playerdata WHERE name = ?";
    private static final String SQL_DELETE_PLAYER_DATA =
            "DELETE FROM griefprevention_playerdata WHERE name = ?";
    private static final String SQL_INSERT_PLAYER_DATA =
            "INSERT INTO griefprevention_playerdata (name, lastlogin, accruedblocks, bonusblocks, glowenabled) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_SET_NEXT_CLAIM_ID =
            "INSERT INTO griefprevention_nextclaimid VALUES (?)";
    private static final String SQL_DELETE_GROUP_DATA =
            "DELETE FROM griefprevention_playerdata WHERE name = ?";
    private static final String SQL_INSERT_SCHEMA_VERSION =
            "INSERT INTO griefprevention_schemaversion VALUES (?)";
    private static final String SQL_DELETE_NEXT_CLAIM_ID =
            "DELETE FROM griefprevention_nextclaimid";
    private static final String SQL_DELETE_SCHEMA_VERSION =
            "DELETE FROM griefprevention_schemaversion";
    private static final String SQL_SELECT_SCHEMA_VERSION =
            "SELECT * FROM griefprevention_schemaversion";

    private static final class ClaimRecord
    {
        final long id;
        final String owner;
        final String lesserCorner;
        final String greaterCorner;
        final String builders;
        final String containers;
        final String accessors;
        final String managers;
        final String denied;
        final boolean inheritNothing;
        final boolean inheritNothingForNewSubdivisions;
        final long parentId;
        final long expirationDate;
        final boolean explosivesAllowed;
        final boolean witherExplosionsAllowed;
        final boolean is3d;
        final String shapeCorners;
        final long modifiedDate;
        final boolean pvpEnabled;
        final boolean alertsEnabled;
        final boolean adminSubdivision;
        final String pvpTrusted;
        final String pveTrusted;
        final boolean pvpToggleSet;
        final String neighbors;
        final boolean allowAllNeighbors;

        ClaimRecord(
                long id,
                String owner,
                String lesserCorner,
                String greaterCorner,
                String builders,
                String containers,
                String accessors,
                String managers,
                String denied,
                boolean inheritNothing,
                boolean inheritNothingForNewSubdivisions,
                long parentId,
                long expirationDate,
                boolean explosivesAllowed,
                boolean witherExplosionsAllowed,
                boolean is3d,
                String shapeCorners,
                long modifiedDate,
                boolean pvpEnabled,
                boolean alertsEnabled,
                boolean adminSubdivision,
                String pvpTrusted,
                String pveTrusted,
                boolean pvpToggleSet,
                String neighbors,
                boolean allowAllNeighbors)
        {
            this.id = id;
            this.owner = owner;
            this.lesserCorner = lesserCorner;
            this.greaterCorner = greaterCorner;
            this.builders = builders;
            this.containers = containers;
            this.accessors = accessors;
            this.managers = managers;
            this.denied = denied;
            this.inheritNothing = inheritNothing;
            this.inheritNothingForNewSubdivisions = inheritNothingForNewSubdivisions;
            this.parentId = parentId;
            this.expirationDate = expirationDate;
            this.explosivesAllowed = explosivesAllowed;
            this.witherExplosionsAllowed = witherExplosionsAllowed;
            this.is3d = is3d;
            this.shapeCorners = shapeCorners;
            this.modifiedDate = modifiedDate;
            this.pvpEnabled = pvpEnabled;
            this.alertsEnabled = alertsEnabled;
            this.adminSubdivision = adminSubdivision;
            this.pvpTrusted = pvpTrusted;
            this.pveTrusted = pveTrusted;
            this.pvpToggleSet = pvpToggleSet;
            this.neighbors = neighbors;
            this.allowAllNeighbors = allowAllNeighbors;
        }
    }

    private static final class PlayerRecord
    {
        final UUID playerId;
        final long lastLoginMillis;
        final int accruedBlocks;
        final int bonusBlocks;
        final boolean glowEnabled;

        PlayerRecord(UUID playerId, long lastLoginMillis, int accruedBlocks, int bonusBlocks, boolean glowEnabled)
        {
            this.playerId = playerId;
            this.lastLoginMillis = lastLoginMillis;
            this.accruedBlocks = accruedBlocks;
            this.bonusBlocks = bonusBlocks;
            this.glowEnabled = glowEnabled;
        }
    }

    private final @NotNull DatabaseConfig config;
    private final @NotNull DatabaseConnectionPool connectionPool;

    // Coalescing write queues so rapid mutations to the same claim/player/group collapse to one DB
    // write and never stall a Folia region thread.
    private final ConcurrentHashMap<Long, ClaimRecord> pendingClaimWrites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerRecord> pendingPlayerWrites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> pendingGroupWrites = new ConcurrentHashMap<>();
    private final AtomicReference<Long> pendingNextClaimId = new AtomicReference<>();

    // Single ordered writer thread preserves causal ordering of creates, updates, and deletes
    // without blocking Folia region threads or Bukkit's main thread.
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GriefPrevention Database Writer");
        thread.setDaemon(true);
        return thread;
    });

    DatabaseDataStore(String url, String userName, String password) throws Exception
    {
        this(DatabaseConfig.of(url, userName, password), true);
    }

    DatabaseDataStore(@NotNull DatabaseConfig config) throws Exception
    {
        this(config, true);
    }

    // Test seam allowing real JDBC schema/query/pool tests without a live Bukkit server.
    DatabaseDataStore(@NotNull DatabaseConfig config, boolean loadServerState) throws Exception
    {
        this.config = config;
        this.connectionPool = new DatabaseConnectionPool(config);

        if (loadServerState)
        {
            this.initialize();
        }
        else
        {
            this.initializeSchemaOnly();
        }
    }

    @NotNull DatabaseConnectionPool connectionPool()
    {
        return this.connectionPool;
    }

    void initializeSchemaOnly() throws Exception
    {
        try
        {
            this.connectionPool.verifyConnectivity();
        }
        catch (Exception e)
        {
            logEntry("ERROR: Unable to connect to database.  Check your database configuration settings.");
            this.connectionPool.close();
            this.writeExecutor.shutdownNow();
            throw e;
        }

        try
        {
            boolean freshDatabase = this.connectionPool.withConnection(connection -> {
                boolean isFresh = this.ensureSchema(connection);
                this.loadNextClaimId(connection);
                return isFresh;
            });
            if (freshDatabase)
            {
                this.setSchemaVersion(latestSchemaVersion);
            }
        }
        catch (Exception e)
        {
            this.connectionPool.close();
            this.writeExecutor.shutdownNow();
            throw e;
        }
    }

    @Override
    void initialize() throws Exception
    {
        try
        {
            this.connectionPool.verifyConnectivity();
        }
        catch (Exception e2)
        {
            logEntry("ERROR: Unable to connect to database.  Check your database configuration settings.");
            this.connectionPool.close();
            this.writeExecutor.shutdownNow();
            throw e2;
        }

        try
        {
            boolean freshDatabase = this.connectionPool.withConnection(connection -> {
                boolean isFresh = this.ensureSchema(connection);
                this.loadGroupBonusBlocks(connection);
                this.loadNextClaimId(connection);
                return isFresh;
            });
            if (freshDatabase)
            {
                this.setSchemaVersion(latestSchemaVersion);
            }

            ClaimLoadResult loadResult = this.connectionPool.withConnection(this::loadClaimsFromDatabase);

            for (Claim claim : loadResult.claimsToRemove)
            {
                this.deleteClaimFromSecondaryStorage(claim);
            }
            if (loadResult.maxObservedId >= this.nextClaimID)
            {
                this.setNextClaimID(loadResult.maxObservedId + 1);
            }

            if (this.getSchemaVersion() <= 2)
            {
                this.connectionPool.withConnection(connection -> {
                    try (Statement statement = connection.createStatement())
                    {
                        statement.execute("DELETE FROM " + TABLE_CLAIM_DATA + " WHERE id = -1");
                    }
                    return null;
                });
            }
        }
        catch (Exception e3)
        {
            logEntry("ERROR: Unable to initialize database storage.  Details:");
            logEntry(String.valueOf(e3.getMessage()));
            e3.printStackTrace();
            this.connectionPool.close();
            this.writeExecutor.shutdownNow();
            throw e3;
        }

        super.initialize();
    }

    private boolean ensureSchema(@NotNull Connection connection) throws SQLException
    {
        try (Statement statement = connection.createStatement())
        {
            statement.execute(SQL_CREATE_NEXT_CLAIM_ID_TABLE);
            statement.execute(SQL_CREATE_CLAIM_TABLE);
            statement.execute(SQL_CREATE_PLAYER_TABLE);
            statement.execute(SQL_CREATE_SCHEMA_VERSION_TABLE);

            // Only needed to upgrade very old MySQL installations whose trust columns were VARCHAR.
            if (this.config.isMySql())
            {
                statement.execute("ALTER TABLE " + TABLE_CLAIM_DATA + " MODIFY builders TEXT");
                statement.execute("ALTER TABLE " + TABLE_CLAIM_DATA + " MODIFY containers TEXT");
                statement.execute("ALTER TABLE " + TABLE_CLAIM_DATA + " MODIFY accessors TEXT");
                statement.execute("ALTER TABLE " + TABLE_CLAIM_DATA + " MODIFY managers TEXT");
            }

            // Ensure every column exists using JDBC metadata rather than MariaDB-only
            // ADD COLUMN IF NOT EXISTS or MySQL-illegal TEXT DEFAULT ''.
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "denied", "TEXT");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "inheritnothing", "BOOLEAN DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "expiration", "BIGINT DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "explosivesallowed", "BOOLEAN DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "inheritnothingfornewsubdivisions", "BOOLEAN DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "witherexplosionsallowed", "BOOLEAN DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "is3d", "BOOLEAN DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "shapecorners", "TEXT");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "modifieddate", "BIGINT DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "pvpenabled", "BOOLEAN DEFAULT 1");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "alertsenabled", "BOOLEAN DEFAULT 1");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "adminsubdivision", "BOOLEAN DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "pvptrusted", "TEXT");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "pvetrusted", "TEXT");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "pvptoggled", "BOOLEAN DEFAULT 0");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "neighbors", "TEXT");
            this.addColumnIfMissing(connection, statement, TABLE_CLAIM_DATA, "allowallneighbors", "BOOLEAN DEFAULT 0");

            this.addColumnIfMissing(connection, statement, TABLE_PLAYER_DATA, "glowenabled", "BOOLEAN DEFAULT 1");

            // If nextclaimid is empty, this is a brand new database already at the latest schema.
            try (ResultSet results = statement.executeQuery("SELECT * FROM " + TABLE_NEXT_CLAIM_ID))
            {
                return !results.next();
            }
        }
    }

    private void loadGroupBonusBlocks(@NotNull Connection connection) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT * FROM " + TABLE_PLAYER_DATA))
        {
            while (results.next())
            {
                String name = results.getString("name");
                if (name == null || !name.startsWith("$")) continue;

                String groupName = name.substring(1);
                if (groupName.isEmpty()) continue;

                int groupBonusBlocks = results.getInt("bonusblocks");
                this.permissionToBonusBlocksMap.put(groupName, groupBonusBlocks);
            }
        }
    }

    private void loadNextClaimId(@NotNull Connection connection) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT * FROM " + TABLE_NEXT_CLAIM_ID))
        {
            if (!results.next())
            {
                statement.execute("INSERT INTO " + TABLE_NEXT_CLAIM_ID + " VALUES (0)");
                this.nextClaimID = 0L;
            }
            else
            {
                this.nextClaimID = results.getLong("nextid");
            }
        }
    }

    private static final class ClaimLoadResult
    {
        final List<Claim> claimsToRemove;
        final long maxObservedId;

        ClaimLoadResult(List<Claim> claimsToRemove, long maxObservedId)
        {
            this.claimsToRemove = claimsToRemove;
            this.maxObservedId = maxObservedId;
        }
    }

    private @NotNull ClaimLoadResult loadClaimsFromDatabase(@NotNull Connection connection) throws SQLException
    {
        ArrayList<Claim> claimsToRemove = new ArrayList<>();
        ArrayList<LoadedSubdivision> subdivisionsToLoad = new ArrayList<>();
        Map<Long, Claim> loadedById = new HashMap<>();
        List<World> validWorlds = Bukkit.getServer().getWorlds();
        long maxObservedId = -1L;

        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT * FROM " + TABLE_CLAIM_DATA))
        {
            while (results.next())
            {
                try
                {
                    long parentId = results.getLong("parentid");
                    long claimID = results.getLong("id");
                    if (claimID > maxObservedId)
                    {
                        maxObservedId = claimID;
                    }
                    boolean inheritNothing = results.getBoolean("inheritnothing");
                    boolean inheritNothingForNewSubdivisions = results.getBoolean("inheritnothingfornewsubdivisions");
                    long expirationDate = results.getLong("expiration");

                    Location lesserBoundaryCorner;
                    Location greaterBoundaryCorner;
                    String lesserCornerString = "(location not available)";
                    try
                    {
                        lesserCornerString = results.getString("lessercorner");
                        lesserBoundaryCorner = this.locationFromString(lesserCornerString, validWorlds);
                        String greaterCornerString = results.getString("greatercorner");
                        greaterBoundaryCorner = this.locationFromString(greaterCornerString, validWorlds);
                    }
                    catch (Exception e)
                    {
                        if (e.getMessage() != null && e.getMessage().contains("World not found"))
                        {
                            logEntry("Failed to load a claim (ID:" + claimID
                                    + ") because its world isn't loaded (yet?).  Please delete the claim or contact the GriefPrevention developer with information about which plugin(s) you're using to load or create worlds.  "
                                    + lesserCornerString);
                            continue;
                        }
                        throw e;
                    }

                    String ownerName = results.getString("owner");
                    UUID ownerID = null;
                    if (ownerName != null && !ownerName.isEmpty() && !ownerName.startsWith("--"))
                    {
                        try
                        {
                            ownerID = UUID.fromString(ownerName);
                        }
                        catch (Exception ex)
                        {
                            logEntry("This owner entry is not a UUID: " + ownerName + ".");
                            logEntry("  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
                        }
                    }

                    List<String> builderNames = parseStorageList(results.getString("builders"));
                    List<String> containerNames = parseStorageList(results.getString("containers"));
                    List<String> accessorNames = parseStorageList(results.getString("accessors"));
                    List<String> managerNames = parseStorageList(results.getString("managers"));
                    List<String> deniedIdentifiers = readOptionalList(results, "denied");

                    boolean explosivesAllowed = results.getBoolean("explosivesallowed");
                    boolean witherExplosionsAllowed = results.getBoolean("witherexplosionsallowed");
                    boolean is3d = results.getBoolean("is3d");
                    boolean pvpEnabled = readOptionalBoolean(results, "pvpenabled", true);
                    boolean alertsEnabled = readOptionalBoolean(results, "alertsenabled", true);
                    boolean adminSubdivision = readOptionalBoolean(results, "adminsubdivision", false);
                    boolean pvpToggleSet = readOptionalBoolean(results, "pvptoggled", false);
                    List<String> pvpTrusted = readOptionalList(results, "pvptrusted");
                    List<String> pveTrusted = readOptionalList(results, "pvetrusted");
                    List<String> neighbors = readOptionalList(results, "neighbors");
                    boolean allowAllNeighbors = readOptionalBoolean(results, "allowallneighbors", false);
                    String shapeCornersStr = readOptionalString(results, "shapecorners");
                    long modifiedDate = readOptionalLong(results, "modifieddate", 0L);

                    Claim claim = new Claim(
                            lesserBoundaryCorner,
                            greaterBoundaryCorner,
                            ownerID,
                            builderNames,
                            containerNames,
                            accessorNames,
                            managerNames,
                            inheritNothing,
                            claimID,
                            is3d
                    );
                    claim.setExpirationDate(expirationDate);
                    claim.areExplosivesAllowed = explosivesAllowed;
                    claim.areWitherExplosionsAllowed = witherExplosionsAllowed;
                    claim.restorePvpToggle(pvpEnabled, pvpToggleSet);
                    claim.alertsEnabled = alertsEnabled;
                    claim.setInheritNothingForNewSubdivisions(inheritNothingForNewSubdivisions);
                    claim.setShapedCorners(parseCornersFromDb(shapeCornersStr));
                    claim.setAdminSubdivision(adminSubdivision);
                    claim.restoreDeniedPermissions(deniedIdentifiers);
                    for (String identifier : pvpTrusted)
                    {
                        claim.addPvpTrust(identifier);
                    }
                    for (String identifier : pveTrusted)
                    {
                        claim.addPveTrust(identifier);
                    }
                    for (String neighbor : neighbors)
                    {
                        claim.addNeighbor(neighbor);
                    }
                    claim.allowAllNeighbors = allowAllNeighbors;
                    if (modifiedDate > 0) claim.modifiedDate = new Date(modifiedDate);

                    loadedById.put(claimID, claim);

                    if (parentId == -1)
                    {
                        this.addClaim(claim, false);
                    }
                    else
                    {
                        subdivisionsToLoad.add(new LoadedSubdivision(claim, parentId));
                    }
                }
                catch (SQLException e)
                {
                    logEntry("Unable to load a claim.  Details: " + e.getMessage());
                    e.printStackTrace();
                }
                catch (Exception e)
                {
                    logEntry("Unable to load a claim.  Details: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Re-attach subdivisions by parentId first (preserving nested and 3D subdivision trees),
        // falling back to spatial lookup for legacy rows.
        for (LoadedSubdivision entry : subdivisionsToLoad)
        {
            Claim childClaim = entry.claim;
            Claim parentClaim = loadedById.get(entry.parentId);
            if (parentClaim == null || parentClaim == childClaim)
            {
                parentClaim = this.getClaimAt(childClaim.getLesserBoundaryCorner(), true, null);
            }

            if (parentClaim == null)
            {
                claimsToRemove.add(childClaim);
                logEntry("Removing orphaned claim subdivision: " + childClaim.getLesserBoundaryCorner().toString());
                continue;
            }

            childClaim.parent = parentClaim;
            if (!parentClaim.children.contains(childClaim))
            {
                parentClaim.children.add(childClaim);
            }
            childClaim.inDataStore = true;
            if (childClaim.id != null)
            {
                this.claimIDMap.put(childClaim.id, childClaim);
            }
        }

        return new ClaimLoadResult(claimsToRemove, maxObservedId);
    }

    private static final class LoadedSubdivision
    {
        final Claim claim;
        final long parentId;

        LoadedSubdivision(Claim claim, long parentId)
        {
            this.claim = claim;
            this.parentId = parentId;
        }
    }

    // -------------------------------------------------------------------------
    // Claim persistence (Folia-safe async ordered writer with transactions)
    // -------------------------------------------------------------------------

    @Override
    synchronized void writeClaimToStorage(Claim claim)
    {
        if (claim == null || claim.id == null) return;

        // Snapshot mutable Claim state on the calling thread (e.g. Folia region thread), then
        // execute JDBC I/O asynchronously so the tick thread never stalls.
        ClaimRecord record = this.snapshotClaim(claim);
        this.pendingClaimWrites.put(record.id, record);
        this.submitWrite(() -> this.flushClaimWrite(record.id));
    }

    @Override
    synchronized public void saveClaims(Collection<Claim> claims)
    {
        if (claims == null || claims.isEmpty()) return;

        List<Long> batchIds = new ArrayList<>(claims.size());
        for (Claim claim : claims)
        {
            if (claim == null) continue;
            this.assignClaimID(claim);
            ClaimRecord record = this.snapshotClaim(claim);
            this.pendingClaimWrites.put(record.id, record);
            batchIds.add(record.id);
        }

        if (!batchIds.isEmpty())
        {
            this.submitWrite(() -> this.flushClaimBatch(batchIds));
        }
    }

    private void flushClaimWrite(long claimId)
    {
        ClaimRecord record = this.pendingClaimWrites.remove(claimId);
        if (record == null) return;

        this.persistClaimRecords(Collections.singletonList(record));
    }

    private void flushClaimBatch(@NotNull List<Long> claimIds)
    {
        Map<Long, ClaimRecord> toWrite = new LinkedHashMap<>();
        for (Long claimId : claimIds)
        {
            ClaimRecord record = this.pendingClaimWrites.remove(claimId);
            if (record != null)
            {
                toWrite.put(claimId, record);
            }
        }
        if (!toWrite.isEmpty())
        {
            this.persistClaimRecords(toWrite.values());
        }
    }

    private void persistClaimRecords(@NotNull Collection<ClaimRecord> records)
    {
        try
        {
            this.connectionPool.withTransaction(connection -> {
                try (PreparedStatement deleteStmt = connection.prepareStatement(SQL_DELETE_CLAIM);
                     PreparedStatement insertStmt = connection.prepareStatement(SQL_INSERT_CLAIM))
                {
                    for (ClaimRecord record : records)
                    {
                        deleteStmt.setLong(1, record.id);
                        deleteStmt.addBatch();

                        bindClaimInsert(insertStmt, record);
                        insertStmt.addBatch();
                    }
                    deleteStmt.executeBatch();
                    insertStmt.executeBatch();
                }
            });
        }
        catch (SQLException e)
        {
            logEntry("Unable to save claim batch (" + records.size() + " claim(s)).  Details:");
            logEntry(String.valueOf(e.getMessage()));
        }
    }

    private static void bindClaimInsert(@NotNull PreparedStatement insertStmt, @NotNull ClaimRecord record)
            throws SQLException
    {
        insertStmt.setLong(1, record.id);
        insertStmt.setString(2, record.owner);
        insertStmt.setString(3, record.lesserCorner);
        insertStmt.setString(4, record.greaterCorner);
        insertStmt.setString(5, record.builders);
        insertStmt.setString(6, record.containers);
        insertStmt.setString(7, record.accessors);
        insertStmt.setString(8, record.managers);
        insertStmt.setString(9, record.denied);
        insertStmt.setBoolean(10, record.inheritNothing);
        insertStmt.setBoolean(11, record.inheritNothingForNewSubdivisions);
        insertStmt.setLong(12, record.parentId);
        insertStmt.setLong(13, record.expirationDate);
        insertStmt.setBoolean(14, record.explosivesAllowed);
        insertStmt.setBoolean(15, record.witherExplosionsAllowed);
        insertStmt.setBoolean(16, record.is3d);
        insertStmt.setString(17, record.shapeCorners);
        insertStmt.setLong(18, record.modifiedDate);
        insertStmt.setBoolean(19, record.pvpEnabled);
        insertStmt.setBoolean(20, record.alertsEnabled);
        insertStmt.setBoolean(21, record.adminSubdivision);
        insertStmt.setString(22, record.pvpTrusted);
        insertStmt.setString(23, record.pveTrusted);
        insertStmt.setBoolean(24, record.pvpToggleSet);
        insertStmt.setString(25, record.neighbors);
        insertStmt.setBoolean(26, record.allowAllNeighbors);
    }

    private @NotNull ClaimRecord snapshotClaim(@NotNull Claim claim)
    {
        String lesserCornerString = this.locationToString(claim.getLesserBoundaryCorner());
        String greaterCornerString = this.locationToString(claim.getGreaterBoundaryCorner());
        String owner = claim.ownerID != null ? claim.ownerID.toString() : "";

        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);

        String buildersString = storageStringBuilder(builders);
        String containersString = storageStringBuilder(containers);
        String accessorsString = storageStringBuilder(accessors);
        String managersString = storageStringBuilder(managers);
        // Revoked inherited trust is stored as a deny entry, not as missing trust, so it has to be
        // written alongside the grant lists or a reload hands that trust back.
        String deniedString = storageStringBuilder(new ArrayList<>(claim.getTrustSnapshot().deniedIdentifiers()));
        String pvpTrustedString = storageStringBuilder(new ArrayList<>(claim.getPvpTrustedIdentifiers()));
        String pveTrustedString = storageStringBuilder(new ArrayList<>(claim.getPveTrustedIdentifiers()));
        String neighborsString = storageStringBuilder(claim.getManualNeighbors());

        long parentId = (claim.parent == null || claim.parent.id == null) ? -1L : claim.parent.id;
        long modifiedDate = claim.modifiedDate != null ? claim.modifiedDate.getTime() : System.currentTimeMillis();

        return new ClaimRecord(
                claim.id,
                owner,
                lesserCornerString,
                greaterCornerString,
                buildersString,
                containersString,
                accessorsString,
                managersString,
                deniedString,
                claim.getSubclaimRestrictions(),
                claim.getInheritNothingForNewSubdivisions(),
                parentId,
                claim.getExpirationDate(),
                claim.areExplosivesAllowed,
                claim.areWitherExplosionsAllowed,
                claim.is3D(),
                serializeCornersForDb(claim),
                modifiedDate,
                claim.pvpEnabled,
                claim.alertsEnabled,
                claim.isAdminSubdivision(),
                pvpTrustedString,
                pveTrustedString,
                claim.pvpToggleSet,
                neighborsString,
                claim.allowAllNeighbors
        );
    }

    @Override
    synchronized void deleteClaimFromSecondaryStorage(Claim claim)
    {
        if (claim == null || claim.id == null) return;

        final long claimId = claim.id;
        final String claimType = claim.parent != null ? "subdivision" : "claim";
        final boolean debugEnabled = GriefPrevention.instance != null && GriefPrevention.instance.config_logs_debugEnabled;

        // Drop any queued write for this claim so it cannot recreate a deleted row.
        this.pendingClaimWrites.remove(claimId);

        this.submitWrite(() -> this.deleteClaimNow(claimId, claimType, debugEnabled));
    }

    private void deleteClaimNow(long claimId, @NotNull String claimType, boolean debugEnabled)
    {
        try
        {
            int rowsAffected = this.connectionPool.withConnection(connection -> {
                try (PreparedStatement deleteStmt = connection.prepareStatement(SQL_DELETE_CLAIM))
                {
                    deleteStmt.setLong(1, claimId);
                    return deleteStmt.executeUpdate();
                }
            });

            if (debugEnabled)
            {
                GriefPrevention.AddLogEntry(
                        "[DEBUG] Database: Deleted " + claimType + " " + claimId
                                + " from database (rows affected: " + rowsAffected + ")",
                        CustomLogEntryTypes.Debug,
                        true
                );
            }
        }
        catch (SQLException e)
        {
            logEntry("Unable to delete data for claim " + claimId + ".  Details:");
            logEntry(String.valueOf(e.getMessage()));
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Player data persistence (Folia-safe preload + pooled reads + ordered async writes)
    // -------------------------------------------------------------------------

    /**
     * Preloads a player's persisted claim blocks into memory from an async thread (such as
     * {@code AsyncPlayerPreLoginEvent}) so Folia region threads never stall on database reads when
     * the player joins.
     */
    void preloadPlayerData(@NotNull UUID playerID)
    {
        PlayerData playerData = this.getPlayerData(playerID);
        if (!playerData.isStorageLoaded())
        {
            playerData.getAccruedClaimBlocks();
        }
    }

    @Override
    PlayerData getPlayerDataFromStorage(UUID playerID)
    {
        PlayerData playerData = new PlayerData();
        playerData.playerID = playerID;

        if (playerID == null)
        {
            return playerData;
        }

        // If an async write for this player is still queued, serve that latest snapshot directly
        // so a rapid quit-and-rejoin never observes stale database values.
        PlayerRecord pending = this.pendingPlayerWrites.get(playerID);
        if (pending != null)
        {
            playerData.setAccruedClaimBlocks(pending.accruedBlocks);
            playerData.setBonusClaimBlocks(pending.bonusBlocks);
            playerData.glowEnabled = pending.glowEnabled;
            return playerData;
        }

        try
        {
            this.connectionPool.withConnection(connection -> {
                try (PreparedStatement selectStmt = connection.prepareStatement(SQL_SELECT_PLAYER_DATA))
                {
                    selectStmt.setString(1, playerID.toString());
                    try (ResultSet results = selectStmt.executeQuery())
                    {
                        if (results.next())
                        {
                            playerData.setAccruedClaimBlocks(results.getInt("accruedblocks"));
                            playerData.setBonusClaimBlocks(results.getInt("bonusblocks"));
                            playerData.glowEnabled = readOptionalBoolean(results, "glowenabled", true);
                        }
                    }
                }
                return null;
            });
        }
        catch (SQLException e)
        {
            // Flag this PlayerData as having failed to load. Callers must NOT treat a failed read
            // as "this player has no saved data" — that would let a transient connection failure
            // silently overwrite a player's real claim blocks with defaults on logout (#2589 / #666).
            playerData.loadFailedFromStorage = true;
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            logException(playerID + " " + errors);
            logException(
                    "Failed to load player data for " + playerID
                            + " from the database. Saves for this session will be skipped to protect existing data."
            );
        }

        return playerData;
    }

    @Override
    public void savePlayerData(UUID playerID, PlayerData playerData)
    {
        if (playerID == null || playerData == null) return;

        if (playerData.loadFailedFromStorage)
        {
            logException(
                    "Refusing to save PlayerData for " + playerID
                            + " because the most recent storage read failed. The stored record is being preserved."
            );
            return;
        }

        // If already loaded, capture an immutable snapshot on the calling thread and coalesce onto
        // the ordered writer thread instead of spawning an unbounded SavePlayerDataThread.
        if (playerData.isStorageLoaded())
        {
            PlayerRecord record = snapshotPlayer(playerID, playerData);
            this.pendingPlayerWrites.put(playerID, record);
            this.submitWrite(() -> {
                this.flushPlayerWrite(playerID);
                this.saveIgnoreList(playerID, playerData);
            });
        }
        else
        {
            // Not yet loaded: load and save asynchronously off the Folia region thread.
            this.submitWrite(() -> {
                playerData.getAccruedClaimBlocks();
                if (playerData.loadFailedFromStorage) return;
                PlayerRecord record = snapshotPlayer(playerID, playerData);
                this.pendingPlayerWrites.remove(playerID);
                this.persistPlayerRecord(record);
                this.saveIgnoreList(playerID, playerData);
            });
        }
    }

    // Synchronous player save path (called by savePlayerDataSync on shutdown and during migration).
    @Override
    public void overrideSavePlayerData(UUID playerID, PlayerData playerData)
    {
        if (playerID == null || playerData == null) return;

        if (playerData.loadFailedFromStorage)
        {
            logException(
                    "Refusing to save PlayerData for " + playerID
                            + " because the most recent storage read failed. The stored record is being preserved."
            );
            return;
        }

        PlayerRecord record = snapshotPlayer(playerID, playerData);
        this.pendingPlayerWrites.remove(playerID);
        this.persistPlayerRecord(record);
    }

    private void flushPlayerWrite(@NotNull UUID playerID)
    {
        PlayerRecord record = this.pendingPlayerWrites.remove(playerID);
        if (record == null) return;
        this.persistPlayerRecord(record);
    }

    private void persistPlayerRecord(@NotNull PlayerRecord record)
    {
        String dateString = formatSqlDateTime(record.lastLoginMillis);
        String playerIdText = record.playerId.toString();

        try
        {
            this.connectionPool.withTransaction(connection -> {
                try (PreparedStatement deleteStmt = connection.prepareStatement(SQL_DELETE_PLAYER_DATA);
                     PreparedStatement insertStmt = connection.prepareStatement(SQL_INSERT_PLAYER_DATA))
                {
                    deleteStmt.setString(1, playerIdText);
                    deleteStmt.executeUpdate();

                    insertStmt.setString(1, playerIdText);
                    insertStmt.setString(2, dateString);
                    insertStmt.setInt(3, record.accruedBlocks);
                    insertStmt.setInt(4, record.bonusBlocks);
                    insertStmt.setBoolean(5, record.glowEnabled);
                    insertStmt.executeUpdate();
                }
            });
        }
        catch (SQLException e)
        {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            logException(playerIdText + " " + errors);
        }
    }

    private static @NotNull PlayerRecord snapshotPlayer(@NotNull UUID playerID, @NotNull PlayerData playerData)
    {
        long lastLogin = playerData.lastSpawn > 0 ? playerData.lastSpawn : System.currentTimeMillis();
        return new PlayerRecord(
                playerID,
                lastLogin,
                playerData.getAccruedClaimBlocks(),
                playerData.getBonusClaimBlocks(),
                playerData.glowEnabled
        );
    }

    // -------------------------------------------------------------------------
    // Next claim ID, group bonus blocks, schema version
    // -------------------------------------------------------------------------

    @Override
    synchronized void incrementNextClaimID()
    {
        this.setNextClaimID(this.nextClaimID + 1);
    }

    synchronized void setNextClaimID(long nextID)
    {
        this.nextClaimID = nextID;
        this.pendingNextClaimId.set(nextID);
        this.submitWrite(this::flushNextClaimId);
    }

    private void flushNextClaimId()
    {
        Long nextID = this.pendingNextClaimId.getAndSet(null);
        if (nextID == null) return;

        try
        {
            this.connectionPool.withTransaction(connection -> {
                try (PreparedStatement deleteStmt = connection.prepareStatement(SQL_DELETE_NEXT_CLAIM_ID);
                     PreparedStatement insertStmt = connection.prepareStatement(SQL_SET_NEXT_CLAIM_ID))
                {
                    deleteStmt.executeUpdate();
                    insertStmt.setLong(1, nextID);
                    insertStmt.executeUpdate();
                }
            });
        }
        catch (SQLException e)
        {
            logEntry("Unable to set next claim ID to " + nextID + ".  Details:");
            logEntry(String.valueOf(e.getMessage()));
        }
    }

    @Override
    synchronized void saveGroupBonusBlocks(String groupName, int currentValue)
    {
        if (groupName == null) return;

        this.pendingGroupWrites.put(groupName, currentValue);
        this.submitWrite(() -> this.flushGroupWrite(groupName));
    }

    private void flushGroupWrite(@NotNull String groupName)
    {
        Integer currentValue = this.pendingGroupWrites.remove(groupName);
        if (currentValue == null) return;

        String dateString = formatSqlDateTime(System.currentTimeMillis());
        try
        {
            this.connectionPool.withTransaction(connection -> {
                try (PreparedStatement deleteStmt = connection.prepareStatement(SQL_DELETE_GROUP_DATA);
                     PreparedStatement insertStmt = connection.prepareStatement(SQL_INSERT_PLAYER_DATA))
                {
                    deleteStmt.setString(1, '$' + groupName);
                    deleteStmt.executeUpdate();

                    insertStmt.setString(1, '$' + groupName);
                    insertStmt.setString(2, dateString);
                    insertStmt.setInt(3, 0);
                    insertStmt.setInt(4, currentValue);
                    insertStmt.setBoolean(5, true);
                    insertStmt.executeUpdate();
                }
            });
        }
        catch (SQLException e)
        {
            logEntry("Unable to save data for group " + groupName + ".  Details:");
            logEntry(String.valueOf(e.getMessage()));
        }
    }

    @Override
    protected int getSchemaVersionFromStorage()
    {
        try
        {
            Integer stored = this.connectionPool.withConnection(connection -> {
                try (PreparedStatement selectStmt = connection.prepareStatement(SQL_SELECT_SCHEMA_VERSION);
                     ResultSet results = selectStmt.executeQuery())
                {
                    if (!results.next())
                    {
                        return null;
                    }
                    return results.getInt("version");
                }
            });
            if (stored == null)
            {
                this.setSchemaVersion(0);
                return 0;
            }
            return stored;
        }
        catch (SQLException e)
        {
            logEntry("Unable to retrieve schema version from database.  Details:");
            logEntry(String.valueOf(e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    protected void updateSchemaVersionInStorage(int versionToSet)
    {
        try
        {
            this.connectionPool.withTransaction(connection -> {
                try (PreparedStatement deleteStmt = connection.prepareStatement(SQL_DELETE_SCHEMA_VERSION);
                     PreparedStatement insertStmt = connection.prepareStatement(SQL_INSERT_SCHEMA_VERSION))
                {
                    deleteStmt.executeUpdate();
                    insertStmt.setInt(1, versionToSet);
                    insertStmt.executeUpdate();
                }
            });
        }
        catch (SQLException e)
        {
            logEntry("Unable to set schema version to " + versionToSet + ".  Details:");
            logEntry(String.valueOf(e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Async queue & shutdown lifecycle
    // -------------------------------------------------------------------------

    private void submitWrite(@NotNull Runnable task)
    {
        try
        {
            this.writeExecutor.execute(task);
        }
        catch (RejectedExecutionException e)
        {
            // Writer is shut down (server stopping) — execute synchronously so nothing is lost.
            task.run();
        }
    }

    /**
     * Waits for all queued background writes to finish and flushes any remaining coalesced records.
     * Used after flat-file migration and during shutdown.
     */
    void flushPendingWrites()
    {
        if (!this.writeExecutor.isShutdown())
        {
            try
            {
                Future<?> barrier = this.writeExecutor.submit(() -> { });
                barrier.get(30, TimeUnit.SECONDS);
            }
            catch (RejectedExecutionException ignored)
            {
                // Executor shut down concurrently — drain directly below.
            }
            catch (Exception e)
            {
                if (e instanceof InterruptedException)
                {
                    Thread.currentThread().interrupt();
                }
                logEntry("Timed out or interrupted while flushing database write queue: " + e.getMessage());
            }
        }

        this.drainCoalescedMaps();
    }

    private void drainCoalescedMaps()
    {
        List<Long> remainingClaims = new ArrayList<>(this.pendingClaimWrites.keySet());
        if (!remainingClaims.isEmpty())
        {
            this.flushClaimBatch(remainingClaims);
        }
        for (UUID playerId : new ArrayList<>(this.pendingPlayerWrites.keySet()))
        {
            this.flushPlayerWrite(playerId);
        }
        for (String group : new ArrayList<>(this.pendingGroupWrites.keySet()))
        {
            this.flushGroupWrite(group);
        }
        this.flushNextClaimId();
    }

    @Override
    synchronized void close()
    {
        this.writeExecutor.shutdown();
        try
        {
            if (!this.writeExecutor.awaitTermination(30, TimeUnit.SECONDS))
            {
                this.writeExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            this.writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        this.drainCoalescedMaps();
        this.connectionPool.close();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatSqlDateTime(long epochMillis)
    {
        SimpleDateFormat sqlFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        return sqlFormat.format(new Date(epochMillis));
    }

    private static String serializeCornersForDb(@NotNull Claim claim)
    {
        List<OrthogonalPoint2i> corners = claim.getShapedCorners();
        if (corners == null || corners.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OrthogonalPoint2i corner : corners)
        {
            if (sb.length() > 0) sb.append(';');
            sb.append(corner.x()).append(',').append(corner.z());
        }
        return sb.toString();
    }

    static List<OrthogonalPoint2i> parseCornersFromDb(@Nullable String serialized)
    {
        if (serialized == null || serialized.isEmpty())
        {
            return Collections.emptyList();
        }
        List<OrthogonalPoint2i> corners = new ArrayList<>();
        for (String entry : serialized.split(";"))
        {
            String[] parts = entry.split(",");
            if (parts.length != 2) continue;
            corners.add(new OrthogonalPoint2i(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
        }
        return corners;
    }

    private static String storageStringBuilder(@NotNull Collection<String> input)
    {
        if (input.isEmpty()) return "";
        StringBuilder output = new StringBuilder();
        for (String string : input)
        {
            if (string != null && !string.isEmpty())
            {
                output.append(string).append(';');
            }
        }
        return output.toString();
    }

    /**
     * Adds a column to {@code tableName} when the connected database does not already have it.
     *
     * <p>Uses catalog-scoped JDBC metadata instead of MariaDB-only {@code ADD COLUMN IF NOT EXISTS}
     * so the statement stays portable across MySQL 5.7/8.0/8.4, MariaDB, SQLite, and H2, and never
     * matches identically named tables in sibling databases on the same MySQL server.</p>
     */
    private void addColumnIfMissing(
            @NotNull Connection connection,
            @NotNull Statement statement,
            @NotNull String tableName,
            @NotNull String columnName,
            @NotNull String columnDefinition) throws SQLException
    {
        if (columnExists(connection, tableName, columnName)) return;

        statement.execute(
                "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition
        );
    }

    static boolean columnExists(@NotNull Connection connection, @NotNull String tableName, @NotNull String columnName)
            throws SQLException
    {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = null;
        try
        {
            catalog = connection.getCatalog();
        }
        catch (SQLException | AbstractMethodError ignored) {}

        String[] tableCandidates = {tableName, tableName.toUpperCase(Locale.ROOT)};
        String[] columnCandidates = {columnName, columnName.toUpperCase(Locale.ROOT)};
        for (String table : tableCandidates)
        {
            for (String column : columnCandidates)
            {
                try (ResultSet columns = metaData.getColumns(catalog, null, table, column))
                {
                    if (columns.next()) return true;
                }
            }
        }
        return false;
    }

    private static boolean readOptionalBoolean(@NotNull ResultSet results, @NotNull String column, boolean fallback)
    {
        try
        {
            Object value = results.getObject(column);
            if (value == null) return fallback;
            return results.getBoolean(column);
        }
        catch (SQLException e)
        {
            return fallback;
        }
    }

    private static long readOptionalLong(@NotNull ResultSet results, @NotNull String column, long fallback)
    {
        try
        {
            Object value = results.getObject(column);
            if (value == null) return fallback;
            return results.getLong(column);
        }
        catch (SQLException e)
        {
            return fallback;
        }
    }

    private static @Nullable String readOptionalString(@NotNull ResultSet results, @NotNull String column)
    {
        try
        {
            return results.getString(column);
        }
        catch (SQLException e)
        {
            return null;
        }
    }

    private static @NotNull List<String> readOptionalList(@NotNull ResultSet results, @NotNull String column)
    {
        try
        {
            return parseStorageList(results.getString(column));
        }
        catch (SQLException e)
        {
            return Collections.emptyList();
        }
    }

    /**
     * Splits a stored {@code ;}-delimited list, dropping the empty trailing entry the writer adds.
     */
    static List<String> parseStorageList(String stored)
    {
        if (stored == null || stored.isEmpty()) return Collections.emptyList();

        List<String> values = new ArrayList<>();
        for (String value : stored.split(";"))
        {
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static void logEntry(@NotNull String message)
    {
        if (GriefPrevention.instance != null)
        {
            GriefPrevention.AddLogEntry(message);
        }
    }

    private static void logException(@NotNull String message)
    {
        if (GriefPrevention.instance != null)
        {
            GriefPrevention.AddLogEntry(message, CustomLogEntryTypes.Exception, false);
        }
    }
}
