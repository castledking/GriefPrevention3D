package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class MergeClaimsTest
{
    private static final UUID OWNER = UUID.fromString("24ea4d50-a943-45bf-952e-13fd50d9d1b5");
    private static final UUID OTHER_OWNER = UUID.fromString("5afb608d-5263-48df-81fc-165c41047de7");

    @Test
    void twoRectangularClaimsAlwaysProduceTheirGlobalBoundingRectangle()
    {
        OrthogonalPolygon first = OrthogonalPolygon.fromRectangle(0, 0, 4, 4);
        OrthogonalPolygon second = OrthogonalPolygon.fromRectangle(10, 8, 14, 12);

        OrthogonalPolygon merged = DataStore.buildCorridorMerge(
                first,
                1,
                new OrthogonalPoint2i(3, 2),
                second,
                3,
                new OrthogonalPoint2i(11, 10),
                null);

        assertEquals(4, merged.corners().size());
        assertEquals(0, merged.minX());
        assertEquals(0, merged.minZ());
        assertEquals(14, merged.maxX());
        assertEquals(12, merged.maxZ());
        assertEquals(195, merged.cellCount());
    }

    @Test
    void shapedClaimsKeepTheDepthWidenedManhattanRoute()
    {
        OrthogonalPolygon first = claimWithNarrowNib(0, 0);
        OrthogonalPolygon second = claimWithNarrowNib(12, 12);

        OrthogonalPolygon merged = DataStore.buildCorridorMerge(
                first,
                0,
                new OrthogonalPoint2i(1, 5),
                second,
                0,
                new OrthogonalPoint2i(13, 14),
                null);

        assertTrue(merged.corners().size() > 4);
        assertTrue(merged.containsCell(3, 10));
        assertTrue(merged.containsCell(10, 13));
        assertFalse(merged.containsCell(10, 5));
    }

    @Test
    void mergeConflictsWithAnotherClaimOwnedByTheMergingOwner()
    {
        Claim first = claim(1L, OWNER, 0, 0, 2, 2);
        Claim second = claim(2L, OWNER, 8, 8, 10, 10);
        Claim ownersOtherClaim = claim(3L, OWNER, 4, 4, 5, 5);

        List<Claim> conflicts = DataStore.findMergeConflicts(
                OrthogonalPolygon.fromRectangle(0, 0, 10, 10),
                Arrays.asList(first, second, ownersOtherClaim),
                first,
                second);

        assertEquals(Collections.singletonList(ownersOtherClaim), conflicts);
    }

    @Test
    void mergeConflictsWithAnotherPlayersClaim()
    {
        Claim first = claim(1L, OWNER, 0, 0, 2, 2);
        Claim second = claim(2L, OWNER, 8, 8, 10, 10);
        Claim otherPlayersClaim = claim(3L, OTHER_OWNER, 4, 4, 5, 5);

        List<Claim> conflicts = DataStore.findMergeConflicts(
                OrthogonalPolygon.fromRectangle(0, 0, 10, 10),
                Arrays.asList(first, second, otherPlayersClaim),
                first,
                second);

        assertEquals(Collections.singletonList(otherPlayersClaim), conflicts);
    }

    @Test
    void populatedAreaWithoutOverlapDoesNotBlockMerge()
    {
        Claim first = claim(1L, OWNER, 0, 0, 2, 2);
        Claim second = claim(2L, OWNER, 8, 8, 10, 10);
        Claim ownersNearbyClaim = claim(3L, OWNER, 12, 0, 14, 2);
        Claim otherNearbyClaim = claim(4L, OTHER_OWNER, -4, 5, -2, 7);

        List<Claim> conflicts = DataStore.findMergeConflicts(
                OrthogonalPolygon.fromRectangle(0, 0, 10, 10),
                Arrays.asList(first, second, ownersNearbyClaim, otherNearbyClaim),
                first,
                second);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void conflictScanCoversTheEntireProposedMergeInsteadOfOnlyTheFirstClaim()
    {
        World world = mock(World.class);
        Claim first = claim(1L, OWNER, 0, 0, 2, 2);
        Claim farClaim = claim(2L, OTHER_OWNER, 80, 80, 82, 82);
        putInWorld(first, world);
        putInWorld(farClaim, world);

        DataStore dataStore = mock(DataStore.class, CALLS_REAL_METHODS);
        dataStore.chunksToClaimsMap = new ConcurrentHashMap<>();
        dataStore.chunksToClaimsMap.put(
                DataStore.getChunkHash(0, 0),
                new ArrayList<>(Collections.singletonList(first)));
        dataStore.chunksToClaimsMap.put(
                DataStore.getChunkHash(5, 5),
                new ArrayList<>(Collections.singletonList(farClaim)));

        Set<Claim> candidates = dataStore.getClaimsUnder(
                world,
                OrthogonalPolygon.fromRectangle(0, 0, 82, 82));

        assertEquals(2, candidates.size());
        assertTrue(candidates.contains(first));
        assertTrue(candidates.contains(farClaim));
    }

    @Test
    void crossingClaimBlocksShapedCorridorEvenWhenNoCornersAreContained()
    {
        Claim first = claim(1L, OWNER, 4, 0, 6, 2);
        Claim second = claim(2L, OWNER, 4, 8, 6, 10);
        Claim crossingClaim = claim(3L, OTHER_OWNER, 0, 4, 10, 6);

        List<Claim> conflicts = DataStore.findMergeConflicts(
                OrthogonalPolygon.fromRectangle(4, 0, 6, 10),
                Arrays.asList(first, second, crossingClaim),
                first,
                second);

        assertEquals(Collections.singletonList(crossingClaim), conflicts);
    }

    @Test
    void commandDoesNotMergeASecondClaimThePlayerCannotEdit()
    {
        UUID playerId = UUID.fromString("78818b81-ae63-4fbb-bb1a-24dfcaa69b19");
        Player player = mock(Player.class);
        Location location = new Location(null, 5, 0, 5);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(location);

        Claim first = mock(Claim.class);
        Claim second = mock(Claim.class);
        when(first.getID()).thenReturn(1L);
        when(second.getID()).thenReturn(2L);
        when(second.checkPermission(player, ClaimPermission.Edit, null))
                .thenReturn(() -> "You cannot edit this claim.");

        PlayerData playerData = new PlayerData();
        playerData.shovelMode = ShovelMode.Merge;
        playerData.claimMerging = first;

        DataStore dataStore = mock(DataStore.class);
        when(dataStore.getPlayerData(playerId)).thenReturn(playerData);
        when(dataStore.getClaimAt(location, false, null)).thenReturn(second);

        GriefPrevention plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        plugin.dataStore = dataStore;

        assertTrue(plugin.handleMergeClaimsCommand(player));

        verify(dataStore, never()).mergeClaims(player, playerData, first, second, null);
        verify(player).sendMessage("§cYou cannot edit this claim.");
    }

    @Test
    void commandTreatsFourCornerShapedMetadataAsARectangle()
    {
        UUID playerId = UUID.fromString("669118fd-b56f-4cad-901e-aaa827cd78e2");
        Player player = mock(Player.class);
        Location location = new Location(null, 5, 0, 5);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(location);

        Claim first = mock(Claim.class);
        Claim second = mock(Claim.class);
        when(first.getID()).thenReturn(1L);
        when(second.getID()).thenReturn(2L);
        when(second.isShaped()).thenReturn(true);
        when(second.getBoundaryPolygon()).thenReturn(OrthogonalPolygon.fromRectangle(4, 4, 8, 8));

        PlayerData playerData = new PlayerData();
        playerData.shovelMode = ShovelMode.Merge;
        playerData.claimMerging = first;

        DataStore dataStore = mock(DataStore.class);
        when(dataStore.getPlayerData(playerId)).thenReturn(playerData);
        when(dataStore.getClaimAt(location, false, null)).thenReturn(second);

        GriefPrevention plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        plugin.dataStore = dataStore;

        assertTrue(plugin.handleMergeClaimsCommand(player));

        verify(dataStore).mergeClaims(player, playerData, first, second, null);
        assertNull(playerData.mergeSecondEdgeIndex);
        assertNull(playerData.mergeSecondDepthPoint);
    }

    private static OrthogonalPolygon claimWithNarrowNib(int offsetX, int offsetZ)
    {
        return OrthogonalPolygon.fromClosedPath(Arrays.asList(
                point(offsetX, offsetZ, 0, 0),
                point(offsetX, offsetZ, 2, 0),
                point(offsetX, offsetZ, 2, 4),
                point(offsetX, offsetZ, 6, 4),
                point(offsetX, offsetZ, 6, 8),
                point(offsetX, offsetZ, 0, 8),
                point(offsetX, offsetZ, 0, 0)));
    }

    private static OrthogonalPoint2i point(int offsetX, int offsetZ, int x, int z)
    {
        return new OrthogonalPoint2i(offsetX + x, offsetZ + z);
    }

    private static Claim claim(long id, UUID owner, int minX, int minZ, int maxX, int maxZ)
    {
        return new Claim(
                new Location(null, minX, 0, minZ),
                new Location(null, maxX, 0, maxZ),
                owner,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                id);
    }

    private static void putInWorld(Claim claim, World world)
    {
        claim.lesserBoundaryCorner.setWorld(world);
        claim.greaterBoundaryCorner.setWorld(world);
        claim.inDataStore = true;
    }
}
