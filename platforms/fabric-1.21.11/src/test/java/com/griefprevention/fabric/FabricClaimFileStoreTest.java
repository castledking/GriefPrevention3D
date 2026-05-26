package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimFileStoreTest
{
    @TempDir
    private Path tempDir;

    @Test
    void missingDataFolderLoadsEmptyClaimsAndCreatesFolders()
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");

        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(
                dataFolder,
                LoggerFactory.getLogger(FabricClaimFileStoreTest.class)
        );

        assertTrue(loaded.snapshots().isEmpty());
        assertTrue(loaded.trustByClaimId().isEmpty());
        assertEquals(1L, loaded.nextClaimId());
        assertTrue(Files.isDirectory(dataFolder.resolve("ClaimData")));
        assertTrue(Files.isDirectory(dataFolder.resolve("PlayerData")));
    }

    @Test
    void loadsBukkitStyleParentAndChildClaims() throws Exception
    {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimDataFolder = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimDataFolder);
        Files.writeString(claimDataFolder.resolve("_nextClaimID"), "30\n", StandardCharsets.UTF_8);
        Files.writeString(claimDataFolder.resolve("28.yml"), """
                Claim ID: '28'
                Lesser Boundary Corner: world;-233;0;-875
                Greater Boundary Corner: world;-224;108;-866
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
                inheritNothingForNewSubdivisions: false
                Is3D: false
                Explosives Allowed: false
                Wither Explosions Allowed: false
                Modified Date: 1779681984295
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
                    inheritNothing: false
                    inheritNothingForNewSubdivisions: false
                    Is3D: true
                    Explosives Allowed: false
                    Wither Explosions Allowed: false
                    Modified Date: 1779679843818
                """.formatted(owner, builder), StandardCharsets.UTF_8);

        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(
                dataFolder,
                LoggerFactory.getLogger(FabricClaimFileStoreTest.class)
        );

        assertEquals(2, loaded.snapshots().size());
        assertEquals(30L, loaded.nextClaimId());

        ClaimSnapshot parent = loaded.snapshots().get(0);
        assertEquals(28L, parent.id());
        assertEquals("world", parent.worldKey());
        assertEquals(owner, parent.ownerId());
        assertEquals(ClaimBounds.rectangle(-233, 0, -875, -224, 108, -866), parent.bounds());
        assertFalse(parent.threeDimensional());
        assertFalse(parent.subdivision());

        ClaimSnapshot child = loaded.snapshots().get(1);
        assertEquals(29L, child.id());
        assertEquals(28L, child.parentId());
        assertEquals(ClaimBounds.rectangle(-232, 103, -873, -225, 111, -867), child.bounds());
        assertTrue(child.threeDimensional());
        assertTrue(child.subdivision());

        ClaimTrustSnapshot trust = loaded.trustByClaimId().get(28L);
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasPublicPermission(ClaimTrustLevel.ACCESS));
        assertTrue(trust.hasExplicitIdentifierPermission("[gp3d.staff]", ClaimTrustLevel.MANAGE));
    }

    @Test
    void savesAndReloadsBukkitStyleClaims() throws Exception
    {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        ClaimSnapshot parent = new ClaimSnapshot(
                12L,
                "world",
                owner,
                null,
                ClaimBounds.rectangle(-5, -64, -5, 5, 320, 5),
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
                List.of("blocked#access")
        );

        FabricClaimFileStore.save(dataFolder, List.of(parent, child), Map.of(12L, trust), 14L);
        Path parentFile = dataFolder.resolve("ClaimData").resolve("12.yml");
        String yaml = Files.readString(parentFile, StandardCharsets.UTF_8);

        assertEquals("14", Files.readString(dataFolder.resolve("ClaimData").resolve("_nextClaimID"),
                StandardCharsets.UTF_8).trim());
        assertTrue(Files.isRegularFile(parentFile));
        assertFalse(Files.exists(dataFolder.resolve("ClaimData").resolve("13.yml")));
        assertTrue(yaml.contains("Claim ID: '12'"));
        assertTrue(yaml.contains("Children:"));
        assertTrue(yaml.contains("  '13':"));

        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(
                dataFolder,
                LoggerFactory.getLogger(FabricClaimFileStoreTest.class)
        );

        assertEquals(List.of(parent, child), loaded.snapshots());
        assertEquals(14L, loaded.nextClaimId());
        ClaimTrustSnapshot loadedTrust = loaded.trustByClaimId().get(12L);
        assertTrue(loadedTrust.hasExplicitPermission(builder, ClaimTrustLevel.BUILD));
        assertTrue(loadedTrust.hasPublicPermission(ClaimTrustLevel.ACCESS));
        assertTrue(loadedTrust.hasExplicitIdentifierPermission("[gp3d.staff]", ClaimTrustLevel.MANAGE));
    }
}
