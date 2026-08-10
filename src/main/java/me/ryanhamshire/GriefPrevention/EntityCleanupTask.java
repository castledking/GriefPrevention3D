/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire
    GPL-3.0-or-later
 */
package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.compat.CompatUtil;

import java.util.ArrayList;
import java.util.List;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;

/** Periodically removes the entity litter forbidden by creative-world claim rules. */
class EntityCleanupTask implements Runnable {
    private final double percentageStart;

    EntityCleanupTask(double percentageStart) {
        this.percentageStart = percentageStart;
    }

    @Override
    public void run() {
        for (final World world : GriefPrevention.instance.getServer().getWorlds()) {
            if (GriefPrevention.instance.config_claims_worldModes.get(world) != ClaimsMode.Creative) continue;
            Chunk[] chunks = world.getLoadedChunks();
            int start = (int) (chunks.length * percentageStart);
            int stop = Math.min(chunks.length, (int) Math.ceil(chunks.length * (percentageStart + .10)));
            for (int index = start; index < stop; index++) {
                final int chunkX = chunks[index].getX();
                final int chunkZ = chunks[index].getZ();
                Location location = new Location(world, (chunkX << 4) + 8, world.getSeaLevel(), (chunkZ << 4) + 8);
                SchedulerUtil.runAtLocation(GriefPrevention.instance, location, new Runnable() {
                    @Override
                    public void run() {
                        if (world.isChunkLoaded(chunkX, chunkZ)) cleanChunk(world.getChunkAt(chunkX, chunkZ));
                    }
                });
            }
        }

        double next = percentageStart + .10;
        if (next > .99) next = 0;
        SchedulerUtil.runLaterGlobal(GriefPrevention.instance, new EntityCleanupTask(next), 20L * 60L);
    }

    private static void cleanChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) continue;
            boolean remove = false;
            if (entity instanceof Boat) {
                remove = ((Boat) entity).isEmpty();
            } else if (entity instanceof Vehicle) {
                Vehicle vehicle = (Vehicle) entity;
                if (vehicle.getVelocity().lengthSquared() != 0) {
                    remove = vehicle.isEmpty() || !(vehicle.getPassenger() instanceof Player);
                } else {
                    Material material = entity.getWorld().getBlockAt(entity.getLocation()).getType();
                    // RAIL was named RAILS before 1.13
                    remove = !CompatUtil.isMaterial(material, "RAIL")
                            && !CompatUtil.isMaterial(material, "RAILS")
                            && material != Material.POWERED_RAIL
                            && material != Material.DETECTOR_RAIL;
                }
            } else {
                Claim claim = GriefPrevention.instance.dataStore.getClaimAt(entity.getLocation(), false, null);
                remove = claim == null;
            }
            if (remove) {
                GriefPrevention.AddLogEntry("Removing entity " + entity.getType().name() + " @ "
                        + entity.getLocation(), CustomLogEntryTypes.Debug, true);
                entity.remove();
            }
        }

        // Enforce claim-scaled limits as well as preventing new over-limit placements.
        List<Claim> checked = new ArrayList<Claim>();
        for (Entity entity : chunk.getEntities()) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(entity.getLocation(), false, null);
            if (claim != null && !checked.contains(claim)) {
                checked.add(claim);
                claim.allowMoreEntities(true);
            }
        }
    }
}
