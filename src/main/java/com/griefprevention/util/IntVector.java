package com.griefprevention.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An immutable integer-based vector.
 */
public final class IntVector
{
    private final int x;
    private final int y;
    private final int z;

    public IntVector(int x, int y, int z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Construct a new {@code IntVector} representing the specified {@link Block}.
     *
     * @param block the {@code Block}
     */
    public IntVector(@NotNull Block block)
    {
        this(block.getX(), block.getY(), block.getZ());
    }

    /**
     * Construct a new {@code IntVector} representing the specified {@link Location}.
     *
     * @param location the {@code Location}
     */
    public IntVector(@NotNull Location location)
    {
        this(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Construct a new {@code IntVector} representing the specified {@link Vector}.
     *
     * @param vector the {@code Vector}
     */
    public IntVector(@NotNull Vector vector)
    {
        this(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    public int x()
    {
        return this.x;
    }

    public int y()
    {
        return this.y;
    }

    public int z()
    {
        return this.z;
    }

    /**
     * Get a {@link Block} representing the {@code IntVector} in the specified {@link World}.
     *
     * @param world the {@code World}
     * @return the corresponding {@code Block}
     */
    public @NotNull Block toBlock(@NotNull World world)
    {
        return world.getBlockAt(x(), y(), z());
    }

    /**
     * Get a {@link Location} representing the {@code IntVector} in the specified {@link World}, if any.
     *
     * @param world the optional {@code World}
     * @return the corresponding {@code Location}
     */
    @Contract("_ -> new")
    public @NotNull Location toLocation(@Nullable World world)
    {
        return new Location(world, x(), y(), z());
    }

    /**
     * Get a {@link BlockVector} representing the {@code IntVector}.
     *
     * @return the corresponding {@code BlockVector}
     */
    @Contract(" -> new")
    public @NotNull BlockVector toVector()
    {
        return new BlockVector(x(), y(), z());
    }

    /**
     * Create a new {@code IntVector} with the specified offset.
     *
     * @param dX the X coordinate offset
     * @param dY the Y coordinate offset
     * @param dZ the Z coordinate offset
     * @return the {@code IntVector} created
     */
    @Contract("_, _, _ -> new")
    public @NotNull IntVector add(int dX, int dY, int dZ)
    {
        return new IntVector(x() + dX, y() + dY, z() + dZ);
    }

    /**
     * Create a new {@code IntVector} with the specified offset.
     *
     * @param other the offset {@code IntVector}
     * @return the {@code IntVector} created
     */
    @Contract("_ -> new")
    public @NotNull IntVector add(@NotNull IntVector other)
    {
        return new IntVector(x() + other.x(), y() + other.y(), z() + other.z());
    }

    /**
     * Calculate the squared distance to another {@code IntVector}.
     *
     * @param other the other {@code IntVector}
     * @return the squared distance
     */
    public int distanceSquared(@NotNull IntVector other)
    {
        int dX = x() - other.x();
        int dY = y() - other.y();
        int dZ = z() - other.z();
        return dX * dX + dY * dY + dZ * dZ;
    }

    /**
     * Check if the {@link org.bukkit.Chunk Chunk} containing the {@link Block} at the {@code IntVector's} coordinates
     * is loaded.
     *
     * @param world the {@link World}
     * @return true if the block is loaded
     */
    public boolean isChunkLoaded(@NotNull World world)
    {
        // Note: Location#getChunk et cetera load the chunk!
        // Only World#isChunkLoaded is safe without a Chunk object.
        return world.isChunkLoaded(x() >> 4, z() >> 4);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof IntVector)) return false;
        IntVector that = (IntVector) other;
        return this.x == that.x && this.y == that.y && this.z == that.z;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.x, this.y, this.z);
    }

    @Override
    public String toString()
    {
        return "IntVector[x=" + this.x + ", y=" + this.y + ", z=" + this.z + "]";
    }
}
