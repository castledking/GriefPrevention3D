package com.griefprevention.compat;

import com.griefprevention.protection.BlockPlaceClimbTracker.Box;
import com.griefprevention.protection.BlockPlaceClimbTracker.Position;
import com.griefprevention.protection.BlockPoint;
import org.bukkit.World;
import org.bukkit.Tag;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Collision checks for movement correction, with a conservative legacy fallback and no chunk loads. */
public final class BlockCollisionCompat
{
    private BlockCollisionCompat() {}

    public static boolean isClimbable(Block block)
    {
        try
        {
            Boolean tagged = ModernClimbables.test(block);
            if (tagged != null) return tagged;
        }
        catch (NoSuchFieldError | NoClassDefFoundError ignored)
        {
            // Tags are unavailable on older Bukkit versions.
        }
        switch (block.getType().name())
        {
            case "LADDER": case "VINE": case "SCAFFOLDING":
            case "WEEPING_VINES": case "WEEPING_VINES_PLANT":
            case "TWISTING_VINES": case "TWISTING_VINES_PLANT":
            case "CAVE_VINES": case "CAVE_VINES_PLANT": return true;
            default: return false;
        }
    }

    // Keep Tag's Keyed bound out of the outer class, which must also load on Bukkit 1.8.
    private static final class ModernClimbables
    {
        static Boolean test(Block block)
        {
            Tag<Material> climbable = Tag.CLIMBABLE;
            return climbable == null ? null : climbable.isTagged(block.getType());
        }
    }

    public static boolean hasClimbable(World world, Position feet, Set<BlockPoint> ignored)
    {
        BlockPoint block = new BlockPoint(floor(feet.x), floor(feet.y), floor(feet.z));
        return !ignored.contains(block) && world.isChunkLoaded(block.x() >> 4, block.z() >> 4)
                && isClimbable(world.getBlockAt(block.x(), block.y(), block.z()));
    }

    public static List<Box> boxes(Block block)
    {
        try
        {
            org.bukkit.util.VoxelShape shape = block.getCollisionShape();
            if (shape != null)
            {
                List<Box> result = new ArrayList<>();
                for (org.bukkit.util.BoundingBox box : shape.getBoundingBoxes())
                {
                    result.add(new Box(block.getX() + box.getMinX(), block.getY() + box.getMinY(), block.getZ() + box.getMinZ(),
                            block.getX() + box.getMaxX(), block.getY() + box.getMaxY(), block.getZ() + box.getMaxZ()));
                }
                return result;
            }
        }
        catch (NoSuchMethodError | NoClassDefFoundError ignored)
        {
            // Pre-1.13: no collision-shape API. Only ordinary full blocks are safe checkpoints.
        }
        if (!block.getType().isOccluding()) return Collections.emptyList();
        return Collections.singletonList(new Box(block.getX(), block.getY(), block.getZ(),
                block.getX() + 1, block.getY() + 1, block.getZ() + 1));
    }

    public static boolean hasSupport(World world, Position feet, Set<BlockPoint> ignored)
    {
        // A real ladder or vine is also a safe checkpoint for someone already climbing.
        if (hasClimbable(world, feet, ignored)) return true;
        for (int x = floor(feet.x - 0.299); x <= floor(feet.x + 0.299); x++)
        {
            for (int z = floor(feet.z - 0.299); z <= floor(feet.z + 0.299); z++)
            {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = floor(feet.y); y >= floor(feet.y) - 2; y--)
                {
                    if (ignored.contains(new BlockPoint(x, y, z))) continue;
                    for (Box box : boxes(world.getBlockAt(x, y, z)))
                    {
                        if (box.overlapsFeet(feet) && Math.abs(box.maxY - feet.y) <= 0.075) return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean canStand(World world, Position feet, double height, Set<BlockPoint> ignored)
    {
        if (!hasSupport(world, feet, ignored)) return false;
        Box body = new Box(feet.x - 0.299, feet.y + 0.001, feet.z - 0.299,
                feet.x + 0.299, feet.y + height - 0.001, feet.z + 0.299);
        for (int x = floor(body.minX); x <= floor(body.maxX); x++)
        {
            for (int z = floor(body.minZ); z <= floor(body.maxZ); z++)
            {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                // Fences and walls can extend above the block that owns their collision shape.
                for (int y = floor(body.minY) - 1; y <= floor(body.maxY); y++)
                {
                    if (ignored.contains(new BlockPoint(x, y, z))) continue;
                    for (Box box : boxes(world.getBlockAt(x, y, z)))
                    {
                        if (box.intersects(body)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static int floor(double value)
    {
        return (int) Math.floor(value);
    }
}
