package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AutoExtendClaimTaskTest
{
    @BeforeAll static void beforeAll() { Bukkit.setServer(ServerMocks.newServer()); }
    @AfterAll static void afterAll() { GriefPrevention.instance = null; ServerMocks.unsetBukkitServer(); }

    @Test
    void aMassiveClaimAlreadyAtWorldBottomDoesNotCollectChunkSnapshots()
    {
        assertNoSnapshots(-64, -64);
    }

    @Test
    void configuredMinimumDepthAlsoSkipsSnapshotCollection()
    {
        assertNoSnapshots(20, 20);
    }

    private void assertNoSnapshots(int claimFloor, int configuredFloor)
    {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        GriefPrevention plugin = mock(GriefPrevention.class);
        GriefPrevention.instance = plugin;
        when(plugin.getMaxDepthForWorld(world)).thenReturn(configuredFloor);
        Claim claim = mock(Claim.class);
        when(claim.getLesserBoundaryCorner()).thenReturn(new Location(world, 0, claimFloor, 0));
        when(claim.getGreaterBoundaryCorner()).thenReturn(new Location(world, 100_000, 320, 100_000));

        AutoExtendClaimTask.scheduleAsync(claim);

        verify(plugin).getMaxDepthForWorld(world);
        verify(world, never()).isChunkLoaded(anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
}
