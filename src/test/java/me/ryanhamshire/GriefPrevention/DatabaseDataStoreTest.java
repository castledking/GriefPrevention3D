package me.ryanhamshire.GriefPrevention;

import com.griefprevention.geometry.OrthogonalPoint2i;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDataStoreTest
{
    private static String freshMySqlModeUrl(String name)
    {
        return "jdbc:h2:mem:" + name + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static World mockWorld(String name)
    {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn(name);
        return world;
    }

    private static Claim makeClaim(World world, long id, UUID owner, int x1, int z1, int x2, int z2)
    {
        Location lesser = new Location(world, x1, 0, z1);
        Location greater = new Location(world, x2, 255, z2);
        return new Claim(
                lesser,
                greater,
                owner,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                false,
                id,
                false
        );
    }

    @Test
    void initializesFreshSchemaEvenWithSingleConnectionPool() throws Exception
    {
        String url = freshMySqlModeUrl("single_conn");
        DatabaseConfig config = new DatabaseConfig(url, "sa", "", "", 1, 5000, 60000, 300000, 2);

        DatabaseDataStore store = new DatabaseDataStore(config, false);
        try
        {
            assertEquals(DataStore.latestSchemaVersion, store.getSchemaVersionFromStorage());
            assertEquals(1, store.connectionPool().maxPoolSize());
        }
        finally
        {
            store.close();
        }
    }

    @Test
    void upgradesLegacyTablesWithoutMariaDbOnlyAlterSyntax() throws Exception
    {
        String url = freshMySqlModeUrl("legacy_upgrade");
        // Seed a minimal legacy schema missing modern columns (denied, is3d, shapecorners,
        // pvptrusted, pvetrusted, pvptoggled, neighbors, allowallneighbors, glowenabled).
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement())
        {
            stmt.execute("CREATE TABLE griefprevention_nextclaimid (nextid INTEGER)");
            stmt.execute("INSERT INTO griefprevention_nextclaimid VALUES (5)");
            stmt.execute("CREATE TABLE griefprevention_schemaversion (version INTEGER)");
            stmt.execute("INSERT INTO griefprevention_schemaversion VALUES (2)");
            stmt.execute("CREATE TABLE griefprevention_claimdata ("
                    + "id INTEGER, owner VARCHAR(50), lessercorner VARCHAR(100), greatercorner VARCHAR(100), "
                    + "builders TEXT, containers TEXT, accessors TEXT, managers TEXT, parentid INTEGER)");
            stmt.execute("CREATE TABLE griefprevention_playerdata ("
                    + "name VARCHAR(50), lastlogin DATETIME, accruedblocks INTEGER, bonusblocks INTEGER)");
        }

        DatabaseConfig config = DatabaseConfig.of(url, "sa", "");
        DatabaseDataStore store = new DatabaseDataStore(config, false);
        try
        {
            store.connectionPool().withConnection(connection -> {
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "denied"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "is3d"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "shapecorners"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "pvptrusted"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "pvetrusted"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "pvptoggled"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "neighbors"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_claimdata", "allowallneighbors"));
                assertTrue(DatabaseDataStore.columnExists(connection, "griefprevention_playerdata", "glowenabled"));
                return null;
            });
            assertEquals(Long.valueOf(5L), store.nextClaimID);
        }
        finally
        {
            store.close();
        }
    }

    @Test
    void writesFullClaimParityFieldsAndCancelsPendingWriteOnDelete() throws Exception
    {
        String url = freshMySqlModeUrl("claim_roundtrip");
        World world = mockWorld("world");
        UUID owner = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        DatabaseDataStore store = new DatabaseDataStore(DatabaseConfig.of(url, "sa", ""), false);
        try
        {
            Claim claim = makeClaim(world, 42L, owner, 10, 20, 50, 60);
            claim.setPermission("public", ClaimPermission.Access);
            claim.restoreDeniedPermissions(Collections.singletonList("[server.builders]#build"));
            claim.addPvpTrust(neighbor.toString());
            claim.addPveTrust(neighbor.toString());
            claim.addNeighbor(neighbor.toString());
            claim.allowAllNeighbors = true;
            claim.areExplosivesAllowed = true;
            claim.areWitherExplosionsAllowed = true;
            claim.restorePvpToggle(false, true);
            claim.alertsEnabled = false;
            claim.setShapedCorners(Arrays.asList(
                    new OrthogonalPoint2i(10, 20),
                    new OrthogonalPoint2i(50, 20),
                    new OrthogonalPoint2i(50, 60),
                    new OrthogonalPoint2i(10, 60)
            ));

            Claim child = makeClaim(world, 43L, null, 15, 25, 30, 40);
            child.parent = claim;
            child.setAdminSubdivision(true);

            store.saveClaims(Arrays.asList(claim, child));
            store.flushPendingWrites();

            store.connectionPool().withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM griefprevention_claimdata WHERE id = 42");
                     ResultSet rs = ps.executeQuery())
                {
                    assertTrue(rs.next(), "Claim 42 should exist in database");
                    assertEquals(owner.toString(), rs.getString("owner"));
                    assertEquals(Collections.singletonList("[server.builders]#build"),
                            DatabaseDataStore.parseStorageList(rs.getString("denied")));
                    assertEquals(Collections.singletonList(neighbor.toString()),
                            DatabaseDataStore.parseStorageList(rs.getString("neighbors")));
                    assertTrue(rs.getBoolean("allowallneighbors"));
                    assertTrue(rs.getBoolean("explosivesallowed"));
                    assertTrue(rs.getBoolean("witherexplosionsallowed"));
                    assertFalse(rs.getBoolean("pvpenabled"));
                    assertTrue(rs.getBoolean("pvptoggled"));
                    assertFalse(rs.getBoolean("alertsenabled"));
                    assertFalse(rs.getBoolean("adminsubdivision"));
                    assertEquals(4, DatabaseDataStore.parseCornersFromDb(rs.getString("shapecorners")).size());
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM griefprevention_claimdata WHERE id = 43");
                     ResultSet rs = ps.executeQuery())
                {
                    assertTrue(rs.next(), "Subdivision 43 should exist in database");
                    assertEquals(42L, rs.getLong("parentid"));
                    assertTrue(rs.getBoolean("adminsubdivision"));
                }
                return null;
            });

            // Write then immediately delete: the deleted row must not reappear.
            store.writeClaimToStorage(claim);
            store.deleteClaimFromSecondaryStorage(claim);
            store.flushPendingWrites();

            store.connectionPool().withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) FROM griefprevention_claimdata WHERE id = 42");
                     ResultSet rs = ps.executeQuery())
                {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                }
                return null;
            });
        }
        finally
        {
            store.close();
        }
    }

    @Test
    void handlesConcurrentMultiRegionFoliaWorkloadSafely() throws Exception
    {
        String url = freshMySqlModeUrl("folia_concurrent");
        World world = mockWorld("world");
        DatabaseConfig config = new DatabaseConfig(url, "sa", "", "", 8, 10_000, 300_000, 1_800_000, 3);
        final DatabaseDataStore store = new DatabaseDataStore(config, false);

        int regionThreads = 8;
        int claimsPerRegion = 20;
        ExecutorService regions = Executors.newFixedThreadPool(regionThreads);
        try
        {
            List<Callable<Void>> tasks = new ArrayList<>();
            final List<UUID> players = new ArrayList<>();
            for (int r = 0; r < regionThreads; r++)
            {
                final int regionIndex = r;
                final UUID playerId = UUID.randomUUID();
                players.add(playerId);

                tasks.add(() -> {
                    List<Claim> batch = new ArrayList<>();
                    for (int i = 0; i < claimsPerRegion; i++)
                    {
                        long claimId = (long) regionIndex * 1000L + i;
                        Claim claim = makeClaim(world, claimId, playerId, i * 10, regionIndex * 10, i * 10 + 5, regionIndex * 10 + 5);
                        if (i % 4 == 0)
                        {
                            batch.add(claim);
                        }
                        else
                        {
                            store.writeClaimToStorage(claim);
                        }
                        store.incrementNextClaimID();
                    }
                    store.saveClaims(batch);

                    PlayerData pd = new PlayerData();
                    pd.playerID = playerId;
                    pd.setAccruedClaimBlocks(500 + regionIndex * 10);
                    pd.setBonusClaimBlocks(100 + regionIndex);
                    pd.glowEnabled = (regionIndex % 2 == 0);
                    pd.lastSpawn = System.currentTimeMillis();
                    store.savePlayerData(playerId, pd);

                    // Concurrent reads from another region thread must not corrupt statements.
                    PlayerData readBack = store.getPlayerDataFromStorage(playerId);
                    assertFalse(readBack.loadFailedFromStorage);

                    store.saveGroupBonusBlocks("group" + regionIndex, 50 * regionIndex);
                    return null;
                });
            }

            List<Future<Void>> futures = regions.invokeAll(tasks);
            for (Future<Void> future : futures)
            {
                future.get(30, TimeUnit.SECONDS);
            }

            // Close drains all queued writes before shutting down the pool.
            store.close();

            // Re-open and verify every claim, player, group, and nextClaimID persisted cleanly.
            DatabaseDataStore verifyStore = new DatabaseDataStore(config, false);
            try
            {
                int claimCount = verifyStore.connectionPool().withConnection(connection -> {
                    try (Statement st = connection.createStatement();
                         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM griefprevention_claimdata"))
                    {
                        rs.next();
                        return rs.getInt(1);
                    }
                });
                assertEquals(regionThreads * claimsPerRegion, claimCount);
                assertEquals(Long.valueOf((long) regionThreads * claimsPerRegion), verifyStore.nextClaimID);

                for (int r = 0; r < regionThreads; r++)
                {
                    PlayerData loaded = verifyStore.getPlayerDataFromStorage(players.get(r));
                    assertFalse(loaded.loadFailedFromStorage);
                    assertEquals(500 + r * 10, loaded.getAccruedClaimBlocks());
                    assertEquals(100 + r, loaded.getBonusClaimBlocks());
                    assertEquals(r % 2 == 0, loaded.glowEnabled);
                }
            }
            finally
            {
                verifyStore.close();
            }
        }
        finally
        {
            regions.shutdownNow();
            if (!store.connectionPool().isClosed())
            {
                store.close();
            }
        }
    }

    @Test
    void refusesToOverwritePlayerDataWhenLoadFailedFlagIsSet() throws Exception
    {
        String url = freshMySqlModeUrl("load_failed_guard");
        UUID playerId = UUID.randomUUID();
        DatabaseDataStore store = new DatabaseDataStore(DatabaseConfig.of(url, "sa", ""), false);
        try
        {
            PlayerData real = new PlayerData();
            real.playerID = playerId;
            real.setAccruedClaimBlocks(9000);
            real.setBonusClaimBlocks(2500);
            real.glowEnabled = false;
            store.overrideSavePlayerData(playerId, real);

            PlayerData failed = new PlayerData();
            failed.playerID = playerId;
            failed.loadFailedFromStorage = true;
            failed.setAccruedClaimBlocks(100);
            failed.setBonusClaimBlocks(0);
            store.savePlayerData(playerId, failed);
            store.overrideSavePlayerData(playerId, failed);
            store.flushPendingWrites();

            PlayerData reloaded = store.getPlayerDataFromStorage(playerId);
            assertEquals(9000, reloaded.getAccruedClaimBlocks());
            assertEquals(2500, reloaded.getBonusClaimBlocks());
            assertFalse(reloaded.glowEnabled);
        }
        finally
        {
            store.close();
        }
    }

    @Test
    void poolRecoversTransparentlyFromDroppedIdleConnection() throws Exception
    {
        String url = freshMySqlModeUrl("pool_recovery");
        DatabaseConnectionPool pool = new DatabaseConnectionPool(
                new DatabaseConfig(url, "sa", "", "", 2, 5000, 60000, 300000, 2),
                null
        );
        try
        {
            // Open a connection and forcibly close it before returning it to simulate wait_timeout.
            pool.withConnection(connection -> {
                connection.close();
                return null;
            });

            // Next query must transparently replace the broken connection and succeed.
            int result = pool.withConnection(connection -> {
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery("SELECT 1"))
                {
                    rs.next();
                    return rs.getInt(1);
                }
            });
            assertEquals(1, result);
        }
        finally
        {
            pool.close();
        }
    }
}
