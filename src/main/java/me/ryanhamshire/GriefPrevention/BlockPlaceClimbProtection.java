package me.ryanhamshire.GriefPrevention;

import com.griefprevention.compat.BlockCollisionCompat;
import com.griefprevention.protection.BlockPlaceClimbTracker;
import com.griefprevention.protection.BlockPlaceClimbTracker.Box;
import com.griefprevention.protection.BlockPlaceClimbTracker.Position;
import com.griefprevention.protection.BlockPoint;
import com.griefprevention.protection.ProtectionHelper;
import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Corrects predicted movement after GP denies a supporting or climbable block near the player's feet. */
public final class BlockPlaceClimbProtection implements Listener
{
    private final GriefPrevention plugin;
    private final LongSupplier clock;
    private final Map<UUID, State> players = new ConcurrentHashMap<>();

    public BlockPlaceClimbProtection(GriefPrevention plugin)
    {
        this(plugin, () -> System.nanoTime() / 1_000_000);
    }

    BlockPlaceClimbProtection(GriefPrevention plugin, LongSupplier clock)
    {
        this.plugin = plugin;
        this.clock = clock;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event)
    {
        Player player = event.getPlayer();
        if (!active(player)) return;
        if (!event.isCancelled())
        {
            return;
        }

        List<Block> placed = new ArrayList<>();
        if (event instanceof BlockMultiPlaceEvent)
        {
            for (BlockState replaced : ((BlockMultiPlaceEvent) event).getReplacedBlockStates()) placed.add(replaced.getBlock());
        }
        else placed.add(event.getBlockPlaced());

        Set<BlockPoint> ignored = new HashSet<>();
        List<Box> denied = new ArrayList<>();
        List<BlockPoint> climbables = new ArrayList<>();
        for (Block block : placed)
        {
            // During this event the server world still contains the blocks that will be rolled back.
            ignored.add(new BlockPoint(block.getX(), block.getY(), block.getZ()));
            if (ProtectionHelper.checkPermission(player, block.getLocation(), ClaimPermission.Build, event) != null)
            {
                if (BlockCollisionCompat.isClimbable(block)) climbables.add(new BlockPoint(block.getX(), block.getY(), block.getZ()));
                denied.addAll(BlockCollisionCompat.boxes(block));
            }
        }
        if (denied.isEmpty() && climbables.isEmpty()) return;

        State state = state(player);
        Location current = player.getLocation();
        Position position = position(current);
        long now = clock.getAsLong();
        if (BlockCollisionCompat.canStand(state.world, position, height(player), ignored)) state.tracker.recordSupported(position, now);
        boolean realClimbing = BlockCollisionCompat.hasClimbable(state.world, position, ignored);
        if (realClimbing) state.tracker.allowRealClimbing();
        Position back = realClimbing ? null : state.tracker.denyClimbablePlacement(position, climbables, now);
        if (back == null) back = state.tracker.denyPlacement(position, denied, now);
        if (back != null && BlockCollisionCompat.canStand(state.world, back, height(player), ignored))
        {
            player.setVelocity(new Vector());
            CompatUtil.teleportSafely(player, location(state.world, back, current));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event)
    {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null || !active(event.getPlayer())) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld())
        {
            players.remove(event.getPlayer().getUniqueId());
            return;
        }
        State state = state(event.getPlayer());
        long now = clock.getAsLong();
        Position from = position(event.getFrom());
        Position to = position(event.getTo());
        if (state.tracker.hasClimbableAttempt(now)
                && (BlockCollisionCompat.hasClimbable(state.world, from, Collections.emptySet())
                    || BlockCollisionCompat.hasClimbable(state.world, to, Collections.emptySet()))) state.tracker.allowRealClimbing();
        if (BlockCollisionCompat.hasSupport(state.world, from, Collections.emptySet())) state.tracker.recordSupported(from, now);
        Position back = state.tracker.setback(to, now);
        if (back != null && BlockCollisionCompat.canStand(state.world, back, height(event.getPlayer()), Collections.emptySet()))
        {
            event.getPlayer().setVelocity(new Vector());
            event.setTo(location(state.world, back, event.getTo()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoved(PlayerMoveEvent event)
    {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null || !active(event.getPlayer())) return;
        State state = state(event.getPlayer());
        Position to = position(event.getTo());
        if (BlockCollisionCompat.hasSupport(state.world, to, Collections.emptySet())) state.tracker.recordSupported(to, clock.getAsLong());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { players.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { players.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { players.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { players.remove(event.getPlayer().getUniqueId()); }

    private boolean active(Player player)
    {
        if (!plugin.config_claims_preventBlockPlaceClimbing || !plugin.claimsEnabledForWorld(player.getWorld())
                || player.isFlying() || player.isInsideVehicle() || player.isDead()
                || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || exemptTravel(player)
                || plugin.dataStore.getPlayerData(player.getUniqueId()).isIgnoringClaims())
        {
            players.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean exemptTravel(Player player)
    {
        try
        {
            if (player.isGliding())
            {
                State state = players.get(player.getUniqueId());
                if (state == null || !state.tracker.hasClimbableAttempt(clock.getAsLong())) return true;
            }
        }
        catch (NoSuchMethodError ignored) { }
        try { return player.isSwimming(); } catch (NoSuchMethodError ignored) { return false; }
    }

    private State state(Player player)
    {
        return players.compute(player.getUniqueId(), (id, old) -> old != null && old.world == player.getWorld()
                ? old : new State(player.getWorld()));
    }

    private static Position position(Location location) { return new Position(location.getX(), location.getY(), location.getZ()); }
    private static Location location(World world, Position pos, Location facing)
    {
        return new Location(world, pos.x, pos.y, pos.z, facing.getYaw(), facing.getPitch());
    }
    private static double height(Player player)
    {
        try { double height = player.getHeight(); return height > 0 ? height : 1.8; }
        catch (NoSuchMethodError ignored) { return player.isSneaking() ? 1.65 : 1.8; }
    }

    private static final class State
    {
        final World world;
        final BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        State(World world) { this.world = world; }
    }
}
