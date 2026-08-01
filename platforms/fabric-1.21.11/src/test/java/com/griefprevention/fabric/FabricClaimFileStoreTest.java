package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.persistence.ClaimDataSchema;
import com.griefprevention.persistence.ClaimDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimFileStoreTest
{
    private static final Logger LOGGER = NOPLogger.NOP_LOGGER;

    @TempDir
    private Path tempDir;

    @Test
    void missingDataFolderLoadsEmptyClaimsAndCreatesFolders()
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");

        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(dataFolder, LOGGER);

        assertTrue(loaded.documents().isEmpty());
        assertTrue(loaded.snapshots().isEmpty());
        assertTrue(loaded.trustByClaimId().isEmpty());
        assertEquals(0L, loaded.nextClaimId());
        assertTrue(Files.isDirectory(dataFolder.resolve("ClaimData")));
        assertTrue(Files.isDirectory(dataFolder.resolve("PlayerData")));
    }

    @Test
    void loadsEveryCurrentBukkitClaimField() throws Exception
    {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimDataFolder = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimDataFolder);
        Files.writeString(dataFolder.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(claimDataFolder.resolve("_nextClaimID"), "30\n", StandardCharsets.UTF_8);
        Files.writeString(claimDataFolder.resolve("28.yml"), """
                Claim ID: '28'
                Lesser Boundary Corner: world;-233;-64;-875
                Greater Boundary Corner: world;-224;320;-866
                Owner: %s
                Builders:
                - %s
                Containers: []
                Accessors:
                - public
                Managers:
                - '[gp3d.staff]'
                Parent Claim ID: -1
                inheritNothing: false
                inheritNothingForNewSubdivisions: true
                Is3D: false
                Shape Corners:
                - -233,-875
                - -224,-875
                - -224,-870
                - -228,-870
                - -228,-866
                - -233,-866
                Explosives Allowed: true
                Wither Explosions Allowed: true
                PvP Enabled: false
                Alerts Enabled: false
                Modified Date: 1779681984295
                Addon Metadata:
                  mode: keep
                Children:
                  '29':
                    Claim ID: '29'
                    Lesser Boundary Corner: world;-232;103;-873
                    Greater Boundary Corner: world;-225;111;-867
                    Owner: ''
                    Builders: []
                    Containers: []
                    Accessors: []
                    Managers: []
                    Parent Claim ID: 28
                    inheritNothing: true
                    inheritNothingForNewSubdivisions: false
                    Is3D: true
                    Explosives Allowed: false
                    Wither Explosions Allowed: false
                    PvP Enabled: true
                    Alerts Enabled: true
                    Modified Date: 1779679843818
                """.formatted(owner, builder), StandardCharsets.UTF_8);

        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(dataFolder, LOGGER);

        assertEquals(2, loaded.documents().size());
        assertEquals(30L, loaded.nextClaimId());

        ClaimDocument parentDocument = loaded.documents().getFirst();
        ClaimSnapshot parent = parentDocument.snapshot();
        assertEquals(28L, parent.id());
        assertEquals("world", parent.worldKey());
        assertEquals(owner, parent.ownerId());
        assertTrue(parent.bounds().isShaped());
        assertFalse(parent.threeDimensional());
        assertFalse(parent.subdivision());
        assertTrue(parentDocument.inheritNothingForNewSubdivisions());
        assertTrue(parentDocument.explosivesAllowed());
        assertTrue(parentDocument.witherExplosionsAllowed());
        assertFalse(parentDocument.pvpEnabled());
        assertFalse(parentDocument.alertsEnabled());
        assertEquals(1779681984295L, parentDocument.modifiedDate());
        assertEquals(Map.of("Addon Metadata", Map.of("mode", "keep")), parentDocument.extraFields());

        ClaimSnapshot child = loaded.snapshots().get(1);
        assertEquals(29L, child.id());
        assertEquals(28L, child.parentId());
        assertEquals(ClaimBounds.rectangle(-232, 103, -873, -225, 111, -867), child.bounds());
        assertTrue(child.threeDimensional());
        assertTrue(child.subdivision());
        assertTrue(loaded.documents().get(1).inheritNothing());

        ClaimTrustSnapshot trust = loaded.trustByClaimId().get(28L);
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasPublicPermission(ClaimTrustLevel.ACCESS));
        assertTrue(trust.hasExplicitIdentifierPermission("[gp3d.staff]", ClaimTrustLevel.MANAGE));
    }

    @Test
    void savesAndReloadsDocumentsWithoutTouchingPlayerBalancesOrSpecialFiles() throws Exception
    {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimDataFolder = dataFolder.resolve("ClaimData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(claimDataFolder);
        Files.createDirectories(playerDataFolder);
        String playerBalance = "\n1234\n567\n\n";
        Files.writeString(playerDataFolder.resolve(player.toString()), playerBalance, StandardCharsets.UTF_8);
        Files.writeString(claimDataFolder.resolve("_addon-index"), "leave-me-alone", StandardCharsets.UTF_8);

        List<OrthogonalPoint2i> shape = List.of(
                new OrthogonalPoint2i(-5, -5),
                new OrthogonalPoint2i(5, -5),
                new OrthogonalPoint2i(5, 0),
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(0, 5),
                new OrthogonalPoint2i(-5, 5)
        );
        ClaimSnapshot parent = new ClaimSnapshot(
                12L,
                "world",
                owner,
                null,
                ClaimBounds.shaped(
                        OrthogonalPolygon.fromClosedPath(closed(shape)),
                        -64,
                        320
                ),
                false,
                false
        );
        ClaimSnapshot child = new ClaimSnapshot(
                13L,
                "world",
                null,
                12L,
                ClaimBounds.rectangle(-2, 70, -2, 2, 80, 2),
                true,
                true
        );
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                owner,
                Map.of(builder.toString(), ClaimTrustLevel.BUILD, "public", ClaimTrustLevel.ACCESS),
                List.of("[gp3d.staff]"),
                List.of()
        );
        ClaimDocument parentDocument = new ClaimDocument(
                parent,
                trust,
                shape,
                false,
                true,
                true,
                true,
                false,
                false,
                1779681984295L,
                "12",
                Map.of("Addon Metadata", Map.of("flag", "keep"))
        );
        ClaimDocument childDocument = new ClaimDocument(
                child,
                ClaimTrustSnapshot.empty(null),
                List.of(),
                true,
                false,
                false,
                false,
                true,
                true,
                1779679843818L,
                "13",
                Map.of()
        );

        FabricClaimFileStore.save(dataFolder, List.of(parentDocument, childDocument), 14L);
        Path parentFile = claimDataFolder.resolve("12.yml");
        String yaml = Files.readString(parentFile, StandardCharsets.UTF_8);

        assertEquals("14", Files.readString(claimDataFolder.resolve("_nextClaimID"),
                StandardCharsets.UTF_8).trim());
        assertEquals(String.valueOf(ClaimDataSchema.CURRENT_VERSION),
                Files.readString(dataFolder.resolve("_schemaVersion"), StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(parentFile));
        assertFalse(Files.exists(claimDataFolder.resolve("13.yml")));
        assertTrue(yaml.contains("Claim ID: '12'"));
        assertTrue(yaml.contains("Children:"));
        assertTrue(yaml.contains("'13':"));
        assertTrue(yaml.contains("Shape Corners:"));
        assertTrue(yaml.contains("Addon Metadata:"));
        assertEquals(playerBalance,
                Files.readString(playerDataFolder.resolve(player.toString()), StandardCharsets.UTF_8));
        assertEquals("leave-me-alone",
                Files.readString(claimDataFolder.resolve("_addon-index"), StandardCharsets.UTF_8));

        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(dataFolder, LOGGER);

        assertEquals(List.of(parentDocument, childDocument), loaded.documents());
        assertEquals(14L, loaded.nextClaimId());
    }

    @Test
    void migratesUnversionedOrphanLayoutWithACompleteBackup() throws Exception
    {
        UUID player = UUID.randomUUID();
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimDataFolder = dataFolder.resolve("ClaimData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(claimDataFolder);
        Files.createDirectories(playerDataFolder);
        Files.writeString(dataFolder.resolve("config.yml"), "custom: preserved\n", StandardCharsets.UTF_8);
        Files.writeString(playerDataFolder.resolve(player.toString()), "\n88\n12\n\n", StandardCharsets.UTF_8);
        Files.writeString(claimDataFolder.resolve("_nextClaimID"), "3", StandardCharsets.UTF_8);
        Files.writeString(claimDataFolder.resolve("1.yml"), claimYaml(1L, null), StandardCharsets.UTF_8);
        Files.writeString(claimDataFolder.resolve("2.yml"), claimYaml(2L, 1L), StandardCharsets.UTF_8);

        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(dataFolder, LOGGER);

        assertEquals(2, loaded.documents().size());
        assertEquals(3L, loaded.nextClaimId());
        assertEquals("11", Files.readString(dataFolder.resolve("_schemaVersion"), StandardCharsets.UTF_8));
        assertTrue(Files.readString(claimDataFolder.resolve("1.yml"), StandardCharsets.UTF_8)
                .contains("Children:"));
        assertFalse(Files.exists(claimDataFolder.resolve("2.yml")));
        assertEquals("\n88\n12\n\n",
                Files.readString(playerDataFolder.resolve(player.toString()), StandardCharsets.UTF_8));
        assertEquals("custom: preserved\n",
                Files.readString(dataFolder.resolve("config.yml"), StandardCharsets.UTF_8));

        Path backups = dataFolder.resolve("MigrationBackups");
        Path backup;
        try (var paths = Files.list(backups))
        {
            backup = paths.findFirst().orElseThrow();
        }
        assertEquals("custom: preserved\n",
                Files.readString(backup.resolve("config.yml"), StandardCharsets.UTF_8));
        assertEquals("\n88\n12\n\n",
                Files.readString(backup.resolve("PlayerData").resolve(player.toString()), StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(backup.resolve("ClaimData").resolve("1.yml")));
        assertTrue(Files.isRegularFile(backup.resolve("ClaimData").resolve("2.yml")));
    }

    @Test
    void failsClosedForFutureSchemasAndMalformedClaims() throws Exception
    {
        Path futureData = this.tempDir.resolve("future");
        Files.createDirectories(futureData.resolve("ClaimData"));
        Files.writeString(
                futureData.resolve("_schemaVersion"),
                String.valueOf(ClaimDataSchema.CURRENT_VERSION + 1),
                StandardCharsets.UTF_8
        );

        Path malformedData = this.tempDir.resolve("malformed");
        Files.createDirectories(malformedData.resolve("ClaimData"));
        Files.writeString(malformedData.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(
                malformedData.resolve("ClaimData").resolve("1.yml"),
                "Claim ID: '1'\nOwner: ''\n",
                StandardCharsets.UTF_8
        );

        assertThrows(IllegalStateException.class,
                () -> FabricClaimFileStore.load(futureData, LOGGER));
        assertThrows(IllegalStateException.class,
                () -> FabricClaimFileStore.load(malformedData, LOGGER));
    }

    @Test
    void refusesToDeleteUnknownLegacyClaimFiles() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimDataFolder = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimDataFolder);
        Path legacy = claimDataFolder.resolve("world;0,0,10,10");
        Files.writeString(legacy, "possible legacy claim", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class,
                () -> FabricClaimFileStore.load(dataFolder, LOGGER));
        assertTrue(Files.isRegularFile(legacy));
    }

    private static List<OrthogonalPoint2i> closed(List<OrthogonalPoint2i> shape)
    {
        List<OrthogonalPoint2i> closed = new ArrayList<>(shape);
        closed.add(shape.getFirst());
        return closed;
    }

    private static String claimYaml(long id, Long parentId)
    {
        return """
                Claim ID: '%d'
                Lesser Boundary Corner: world;0;-64;0
                Greater Boundary Corner: world;10;320;10
                Owner: ''
                Builders: []
                Containers: []
                Accessors: []
                Managers: []
                Parent Claim ID: %d
                inheritNothing: false
                inheritNothingForNewSubdivisions: false
                Is3D: false
                Explosives Allowed: false
                Wither Explosions Allowed: false
                PvP Enabled: true
                Alerts Enabled: true
                Modified Date: 1234
                """.formatted(id, parentId == null ? -1L : parentId);
    }
}
