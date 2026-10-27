package me.ryanhamshire.GriefPrevention;

import com.griefprevention.protection.BlockPoint;
import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class BlockPlaceClimbProtectionTest
{
    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        when(server.getTag(anyString(), any(NamespacedKey.class), eq(Material.class))).thenAnswer(call -> mock(Tag.class));
        Bukkit.setServer(server);
        // Bukkit caches tags globally; another test may already have initialized this mock tag.
        if (Tag.CLIMBABLE != null)
        {
            when(Tag.CLIMBABLE.isTagged(any(Material.class))).thenAnswer(call ->
            {
                Material material = call.getArgument(0);
                return material == Material.LADDER || material == Material.VINE || material == Material.SCAFFOLDING;
            });
        }
    }
    @AfterAll static void afterAll() { GriefPrevention.instance = null; ServerMocks.unsetBukkitServer(); }

    @Test
    void rejectedBlockUnderThePlayersFeetCorrectsTheirPosition()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        assertEquals(64, f.current.getY());
        assertEquals(123, f.current.getYaw());
        verify(f.player).teleport(any(Location.class));
    }

    @Test
    void jumpingAfterThePlacementWasRejectedIsAlsoCorrected()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.listener.onBlockPlace(f.placement(true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0)); // Vanilla rolls the rejected block back.
        PlayerMoveEvent move = new PlayerMoveEvent(f.player, f.location(64), f.location(65.2));
        f.listener.onMove(move);
        assertEquals(64, move.getTo().getY());
        assertEquals(123, move.getTo().getYaw());
    }

    @Test
    void aRejectedLadderClimbsEvenOutsideItsCollisionShape()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(64.4);
        f.shapes.put(new BlockPoint(0, 64, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1, 0.125)));
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        assertEquals(64, f.current.getY());
    }

    @Test
    void aRejectedVineWithoutCollisionCanStillCauseASetback()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(64.4);
        f.shapes.put(new BlockPoint(0, 64, 0), Collections.emptyList());
        f.listener.onBlockPlace(f.placement(Material.VINE, true, true));
        assertEquals(64, f.current.getY());
    }

    @Test
    void climbingAfterTheRejectedLadderDisappearsIsCorrected()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.shapes.put(new BlockPoint(0, 64, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1, 0.125)));
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0));
        PlayerMoveEvent move = new PlayerMoveEvent(f.player, f.location(64), f.location(64.2));
        f.listener.onMove(move);
        assertEquals(64, move.getTo().getY());
    }

    @Test
    void enteringARejectedLaddersBlockAfterPlacementIsCorrected()
    {
        Fixture f = new Fixture();
        f.blocks.put(new BlockPoint(1, 63, 0), Material.STONE);
        f.current = new Location(f.world, 1.1, 64, 0.5);
        f.grounded();
        f.shapes.put(new BlockPoint(0, 64, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1, 0.125)));
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0));
        PlayerMoveEvent move = new PlayerMoveEvent(f.player, f.current, new Location(f.world, 0.9, 64.2, 0.5));
        f.listener.onMove(move);
        assertEquals(64, move.getTo().getY());
        assertEquals(1.1, move.getTo().getX());
    }

    @Test
    void jumpingBesideARejectedLadderWithoutEnteringItsBlockIsUnaffected()
    {
        Fixture f = new Fixture();
        f.blocks.put(new BlockPoint(1, 63, 0), Material.STONE);
        f.current = new Location(f.world, 1.1, 64, 0.5);
        f.grounded();
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0));
        PlayerMoveEvent move = new PlayerMoveEvent(f.player, f.current, new Location(f.world, 1.1, 64.2, 0.5));
        f.listener.onMove(move);
        assertEquals(64.2, move.getTo().getY());
    }

    @Test
    void aRealLadderBesideARejectedLadderStillAllowsClimbing()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0));
        f.blocks.put(new BlockPoint(1, 64, 0), Material.LADDER);
        f.shapes.put(new BlockPoint(1, 64, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1, 0.125)));
        PlayerMoveEvent move = new PlayerMoveEvent(f.player, f.current, new Location(f.world, 1.1, 64.2, 0.5));
        f.listener.onMove(move);
        assertEquals(64.2, move.getTo().getY());
        f.blocks.remove(new BlockPoint(1, 64, 0));
        PlayerMoveEvent offLadder = new PlayerMoveEvent(f.player, move.getTo(), f.location(64.4));
        f.listener.onMove(offLadder);
        assertEquals(64.4, offLadder.getTo().getY());
    }

    @Test
    void authorizedLadderPlacementsAreUnaffected()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(64.4);
        f.listener.onBlockPlace(f.placement(Material.LADDER, false, false));
        verify(f.player, never()).teleport(any(Location.class));
        assertEquals(64.4, f.current.getY());
    }

    @Test
    void aRejectedLadderCannotBeUsedToEscapeSidewaysAboveTheCheckpoint()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0));
        PlayerMoveEvent escape = new PlayerMoveEvent(f.player, f.current, new Location(f.world, 1.1, 64.2, 0.5));
        f.listener.onMove(escape);
        assertEquals(64, escape.getTo().getY());
        assertEquals(0.5, escape.getTo().getX());
    }

    @Test
    void startingToGlideDoesNotEraseAPendingRejectedLadderCorrection()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0));
        when(f.player.isGliding()).thenReturn(true);
        PlayerMoveEvent escape = new PlayerMoveEvent(f.player, f.current, new Location(f.world, 1.1, 64.2, 0.5));
        f.listener.onMove(escape);
        assertEquals(64, escape.getTo().getY());
    }

    @Test
    void ordinaryGlidingWithoutARejectedClimbableIsUnaffected()
    {
        Fixture f = new Fixture();
        f.grounded();
        when(f.player.isGliding()).thenReturn(true);
        PlayerMoveEvent glide = new PlayerMoveEvent(f.player, f.current, f.location(64.2));
        f.listener.onMove(glide);
        assertEquals(64.2, glide.getTo().getY());
    }

    @Test
    void aRealLadderCanBeTheSafeCheckpointWhenExtendingItIsDenied()
    {
        Fixture f = new Fixture();
        f.blocks.put(new BlockPoint(0, 63, 0), Material.LADDER);
        f.shapes.put(new BlockPoint(0, 63, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1, 0.125)));
        f.current = f.location(63.8);
        f.grounded();
        f.current = f.location(64.2);
        f.shapes.put(new BlockPoint(0, 64, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1, 0.125)));
        f.listener.onBlockPlace(f.placement(Material.LADDER, true, true));
        assertEquals(63.8, f.current.getY());
    }

    @Test
    void standingOnTopOfRejectedScaffoldingStillUsesItsCollisionProtection()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(Material.SCAFFOLDING, true, true));
        assertEquals(64, f.current.getY());
    }

    @Test
    void repeatedDeniedStepsCannotRaiseTheCheckpoint()
    {
        Fixture f = new Fixture();
        f.grounded();
        for (int attempt = 0; attempt < 20; attempt++)
        {
            f.now.addAndGet(20);
            f.current = f.location(65.1);
            f.listener.onBlockPlace(f.placement(true, true));
            assertEquals(64, f.current.getY());
            f.blocks.remove(new BlockPoint(0, 64, 0));
        }
    }

    @Test
    void defaultOffAndTrustedPlacementsDoNotTeleport()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(65.1);
        f.plugin.config_claims_preventBlockPlaceClimbing = false;
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
        f.plugin.config_claims_preventBlockPlaceClimbing = true;
        f.listener.onBlockPlace(f.placement(false, false));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void anotherPluginsCancellationDoesNotCountAsADeniedClaimPlacement()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, false));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void ordinaryJumpingAndFlyingAreUnaffected()
    {
        Fixture f = new Fixture();
        f.grounded();
        PlayerMoveEvent jump = new PlayerMoveEvent(f.player, f.location(64), f.location(65.2));
        f.listener.onMove(jump);
        assertEquals(65.2, jump.getTo().getY());
        when(f.player.isFlying()).thenReturn(true);
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void aRemovedFloorDoesNotTeleportThePlayerIntoTheAir()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.blocks.remove(new BlockPoint(0, 63, 0));
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void aBlockedCheckpointDoesNotTeleportThePlayerIntoASolidBlock()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.blocks.put(new BlockPoint(0, 65, 0), Material.STONE);
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void teleportingClearsThePreviousCheckpoint()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.listener.onTeleport(new PlayerTeleportEvent(f.player, f.current, f.location(65.1)));
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void multiBlockPlacementChecksTheBlockUnderThePlayer()
    {
        Fixture f = new Fixture();
        f.blocks.put(new BlockPoint(1, 63, 0), Material.STONE);
        f.current = new Location(f.world, 1.5, 64, 0.5);
        f.grounded();
        f.current.setY(65.1);
        f.placement(true, true); // Configure the claim's denial.
        f.blocks.put(new BlockPoint(1, 64, 0), Material.STONE);
        Block first = f.block(0, 64, 0);
        Block second = f.block(1, 64, 0);
        BlockState firstState = mock(BlockState.class);
        BlockState secondState = mock(BlockState.class);
        when(firstState.getBlock()).thenReturn(first);
        when(secondState.getBlock()).thenReturn(second);
        BlockMultiPlaceEvent event = mock(BlockMultiPlaceEvent.class);
        when(event.getPlayer()).thenReturn(f.player);
        when(event.isCancelled()).thenReturn(true);
        when(event.getReplacedBlockStates()).thenReturn(Arrays.asList(firstState, secondState));
        f.listener.onBlockPlace(event);
        assertEquals(64, f.current.getY());
        assertEquals(1.5, f.current.getX());
    }

    @Test
    void unloadedCheckpointChunksAreNeverLoadedForASetback()
    {
        Fixture f = new Fixture();
        f.grounded();
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
        verify(f.world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void ignoreClaimsAndDisabledWorldsClearAnArmedSetback()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.listener.onBlockPlace(f.placement(true, true));
        f.blocks.remove(new BlockPoint(0, 64, 0));
        f.data.ignoreClaims = true;
        PlayerMoveEvent move = new PlayerMoveEvent(f.player, f.location(64), f.location(65.2));
        f.listener.onMove(move);
        assertEquals(65.2, move.getTo().getY());
        f.data.ignoreClaims = false;
        when(f.plugin.claimsEnabledForWorld(f.world)).thenReturn(false);
        f.current = f.location(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void aSlabCanBeTheSupportedCheckpoint()
    {
        Fixture f = new Fixture();
        f.shapes.put(new BlockPoint(0, 63, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 0.5, 1)));
        f.current = f.location(63.5);
        f.grounded();
        f.current = f.location(64.5);
        f.listener.onBlockPlace(f.placement(true, true));
        assertEquals(63.5, f.current.getY());
    }

    @Test
    void decorationsWithoutCollisionDoNotCauseASetback()
    {
        Fixture f = new Fixture();
        f.grounded();
        f.current = f.location(65.1);
        f.shapes.put(new BlockPoint(0, 64, 0), Collections.emptyList());
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
    }

    @Test
    void aTallBlockBelowTheCheckpointCannotClipThePlayersFeet()
    {
        Fixture f = new Fixture();
        f.current = new Location(f.world, 0.95, 64, 0.5);
        f.grounded();
        // One part of the footprint is still supported by the floor, while a new fence-like
        // collision in the adjacent block projects half a block above that floor.
        f.blocks.put(new BlockPoint(1, 63, 0), Material.STONE);
        f.shapes.put(new BlockPoint(1, 63, 0), Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1.5, 1)));
        f.current.setY(65.1);
        f.listener.onBlockPlace(f.placement(true, true));
        verify(f.player, never()).teleport(any(Location.class));
    }

    private static final class Fixture
    {
        final GriefPrevention plugin = mock(GriefPrevention.class);
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final Claim claim = mock(Claim.class);
        final Map<BlockPoint, Material> blocks = new HashMap<>();
        final Map<BlockPoint, List<BoundingBox>> shapes = new HashMap<>();
        final AtomicLong now = new AtomicLong(100);
        final PlayerData data = new PlayerData();
        final BlockPlaceClimbProtection listener;
        Location current;

        Fixture()
        {
            GriefPrevention.instance = plugin;
            plugin.config_claims_preventBlockPlaceClimbing = true;
            plugin.dataStore = mock(DataStore.class);
            UUID id = UUID.fromString("43dafb81-0de6-4850-960c-767f7d69d8e5");
            when(player.getUniqueId()).thenReturn(id);
            when(player.getWorld()).thenReturn(world);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getLocation()).thenAnswer(call -> current.clone());
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> block(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
            when(plugin.dataStore.getPlayerData(id)).thenReturn(data);
            when(plugin.dataStore.getClaimAt(any(Location.class), eq(false), any())).thenReturn(claim);
            doAnswer(call -> { current = ((Location) call.getArgument(0)).clone(); return true; })
                    .when(player).teleport(any(Location.class));
            blocks.put(new BlockPoint(0, 63, 0), Material.STONE);
            current = location(64);
            listener = new BlockPlaceClimbProtection(plugin, now::get);
        }

        Location location(double y) { return new Location(world, 0.5, y, 0.5, 123, 25); }

        void grounded()
        {
            listener.onMoved(new PlayerMoveEvent(player, current, current));
        }

        BlockPlaceEvent placement(boolean cancelled, boolean denied)
        {
            return placement(Material.STONE, cancelled, denied);
        }

        BlockPlaceEvent placement(Material material, boolean cancelled, boolean denied)
        {
            Supplier<String> reason = () -> "No build trust";
            when(claim.checkPermission(eq(player), eq(ClaimPermission.Build), any())).thenReturn(denied ? reason : null);
            blocks.put(new BlockPoint(0, 64, 0), material);
            Block placed = block(0, 64, 0);
            BlockPlaceEvent event = mock(BlockPlaceEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getBlockPlaced()).thenReturn(placed);
            when(event.isCancelled()).thenReturn(cancelled);
            return event;
        }

        Block block(int x, int y, int z)
        {
            BlockPoint point = new BlockPoint(x, y, z);
            Material material = blocks.getOrDefault(point, Material.AIR);
            VoxelShape shape = mock(VoxelShape.class);
            when(shape.getBoundingBoxes()).thenReturn(material == Material.AIR ? Collections.emptyList()
                    : shapes.getOrDefault(point, Collections.singletonList(new BoundingBox(0, 0, 0, 1, 1,
                            material == Material.LADDER ? 0.125 : 1))));
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(material);
            when(block.getX()).thenReturn(x);
            when(block.getY()).thenReturn(y);
            when(block.getZ()).thenReturn(z);
            when(block.getWorld()).thenReturn(world);
            when(block.getLocation()).thenReturn(new Location(world, x, y, z));
            when(block.getCollisionShape()).thenReturn(shape);
            return block;
        }
    }
}
