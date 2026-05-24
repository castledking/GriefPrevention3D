package me.ryanhamshire.GriefPrevention.compat;

import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.BlockElement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public class LegacyFakeBlockElement extends BlockElement
{
    private final @NotNull Material realMaterial;
    private final @NotNull Material fakeMaterial;
    private final byte realData;
    private final byte fakeData;

    public LegacyFakeBlockElement(
            @NotNull IntVector location,
            @NotNull Material realMaterial,
            byte realData,
            @NotNull Material fakeMaterial,
            byte fakeData)
    {
        super(location);
        this.realMaterial = realMaterial;
        this.realData = realData;
        this.fakeMaterial = fakeMaterial;
        this.fakeData = fakeData;
    }

    @Override
    protected void draw(@NotNull Player player, @NotNull World world)
    {
        sendBlockChange(player, world, fakeMaterial, fakeData);
    }

    @Override
    protected void erase(@NotNull Player player, @NotNull World world)
    {
        sendBlockChange(player, world, realMaterial, realData);
    }

    private static final @NotNull String NMS_PACKAGE;

    static
    {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        NMS_PACKAGE = "net.minecraft.server." + packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private void sendBlockChange(@NotNull Player player, @NotNull World world, @NotNull Material material, byte data)
    {
        int x = 0, y = 0, z = 0;
        try {
            Location loc = getCoordinate().toLocation(world);
            x = loc.getBlockX();
            y = loc.getBlockY();
            z = loc.getBlockZ();

            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);

            int blockId = material.getId();

            Class<?> blockClass = Class.forName(NMS_PACKAGE + ".Block");
            Class<?> iblockDataClass = Class.forName(NMS_PACKAGE + ".IBlockData");
            Object block = blockClass.getMethod("getById", int.class).invoke(null, blockId);
            Object blockData = blockClass.getMethod("fromLegacyData", int.class).invoke(block, (int) data);

            Class<?> packetClass = Class.forName(NMS_PACKAGE + ".PacketPlayOutBlockChange");
            Class<?> blockPositionClass = Class.forName(NMS_PACKAGE + ".BlockPosition");
            Object packet;

            try
            {
                // 1.9+: PacketPlayOutBlockChange(BlockPosition, IBlockData)
                Object blockPosition = blockPositionClass.getConstructor(int.class, int.class, int.class)
                        .newInstance(x, y, z);
                packet = packetClass.getConstructor(blockPositionClass, iblockDataClass)
                        .newInstance(blockPosition, blockData);
            }
            catch (Exception e1)
            {
                // 1.8.x: use no-arg constructor + reflection to set all fields
                packet = packetClass.getConstructor().newInstance();
                java.lang.reflect.Field blockDataField = null;
                java.util.List<java.lang.reflect.Field> intFields = new java.util.ArrayList<>();
                boolean setBlockPosition = false;
                for (java.lang.reflect.Field f : packetClass.getDeclaredFields())
                {
                    f.setAccessible(true);
                    if (f.getType() == int.class)
                    {
                        intFields.add(f);
                    }
                    else if (iblockDataClass.isAssignableFrom(f.getType()))
                    {
                        blockDataField = f;
                    }
                    else if (f.getType() == blockPositionClass)
                    {
                        // Some PaperSpigot 1.8.8 builds store a BlockPosition field
                        f.set(packet, blockPositionClass
                                .getConstructor(int.class, int.class, int.class)
                                .newInstance(x, y, z));
                        setBlockPosition = true;
                    }
                }
                if (!setBlockPosition && intFields.size() >= 3)
                {
                    intFields.get(0).setInt(packet, x);
                    intFields.get(1).setInt(packet, y);
                    intFields.get(2).setInt(packet, z);
                }
                if (blockDataField != null)
                {
                    blockDataField.set(packet, blockData);
                }
            }

            playerConnection.getClass().getMethod("sendPacket",
                    Class.forName(NMS_PACKAGE + ".Packet"))
                    .invoke(playerConnection, packet);

        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Bukkit.getLogger().log(Level.WARNING, "LegacyFakeBlockElement: NMS packet failed for "
                    + material + " at " + x + "," + y + "," + z
                    + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!super.equals(other)) return false;
        if (getClass() != other.getClass()) return false;
        LegacyFakeBlockElement that = (LegacyFakeBlockElement) other;
        return realData == that.realData
                && fakeData == that.fakeData
                && realMaterial == that.realMaterial
                && fakeMaterial == that.fakeMaterial;
    }

    @Override
    public int hashCode()
    {
        return super.hashCode();
    }
}
