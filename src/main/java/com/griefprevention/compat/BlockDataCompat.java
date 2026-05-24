package com.griefprevention.compat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BlockDataCompat {

    private static final String BLOCK_DATA_CLASS = "org.bukkit.block.data.BlockData";
    private static final String CHEST_CLASS = "org.bukkit.block.data.type.Chest";
    private static final String CHEST_TYPE_CLASS = "org.bukkit.block.data.type.Chest$Type";
    private static final String DISPENSER_CLASS = "org.bukkit.block.data.type.Dispenser";
    private static final String LIGHTABLE_CLASS = "org.bukkit.block.data.Lightable";
    private static final String WATERLOGGED_CLASS = "org.bukkit.block.data.Waterlogged";
    private static final String WALL_SIGN_CLASS = "org.bukkit.block.data.type.WallSign";

    private BlockDataCompat() {
    }

    public static boolean isModernChest(@NotNull Block block) {
        Object blockData = getBlockData(block);
        Class<?> chestClass = getClass(CHEST_CLASS);
        return blockData != null && chestClass != null && chestClass.isInstance(blockData);
    }

    public static boolean disconnectModernChest(@NotNull Block block, @NotNull Block relative, @NotNull Player player) {
        Object blockData = getBlockData(block);
        Object relativeData = getBlockData(relative);
        Class<?> chestClass = getClass(CHEST_CLASS);
        Class<?> chestTypeClass = getClass(CHEST_TYPE_CLASS);
        if (blockData == null || relativeData == null || chestClass == null || chestTypeClass == null
                || !chestClass.isInstance(blockData) || !chestClass.isInstance(relativeData)) {
            return false;
        }

        try {
            Object single = enumValue(chestTypeClass, "SINGLE");
            Method setType = chestClass.getMethod("setType", chestTypeClass);
            setType.invoke(blockData, single);
            setType.invoke(relativeData, single);
            setBlockData(block, blockData);
            setBlockData(relative, relativeData);
            sendBlockChange(player, relative.getLocation(), relativeData);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public static boolean setChestType(@NotNull Block block, @NotNull String typeName) {
        Object blockData = getBlockData(block);
        Class<?> chestClass = getClass(CHEST_CLASS);
        Class<?> chestTypeClass = getClass(CHEST_TYPE_CLASS);
        if (blockData == null || chestClass == null || chestTypeClass == null || !chestClass.isInstance(blockData)) {
            return false;
        }

        try {
            Object typeValue = enumValue(chestTypeClass, typeName);
            Method setType = chestClass.getMethod("setType", chestTypeClass);
            setType.invoke(blockData, typeValue);
            setBlockData(block, blockData);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public static @Nullable BlockFace getDispenserFacing(@NotNull Block block) {
        Object blockData = getBlockData(block);
        Class<?> dispenserClass = getClass(DISPENSER_CLASS);
        if (blockData != null && dispenserClass != null && dispenserClass.isInstance(blockData)) {
            try {
                Object facing = dispenserClass.getMethod("getFacing").invoke(blockData);
                if (facing instanceof BlockFace) {
                    return (BlockFace) facing;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Fall through to legacy material data.
            }
        }

        @SuppressWarnings("deprecation")
        MaterialData materialData = block.getState().getData();
        if (materialData instanceof Directional) {
            return ((Directional) materialData).getFacing();
        }
        return null;
    }

    public static @Nullable EntityChangeBlockEvent createLitChangeBlockEvent(
            @NotNull Entity entity,
            @NotNull Block block) {
        Object blockData = getBlockData(block);
        Class<?> lightableClass = getClass(LIGHTABLE_CLASS);
        Class<?> blockDataClass = getClass(BLOCK_DATA_CLASS);
        if (blockData == null || lightableClass == null || blockDataClass == null || !lightableClass.isInstance(blockData)) {
            return null;
        }

        try {
            lightableClass.getMethod("setLit", boolean.class).invoke(blockData, true);
            Constructor<EntityChangeBlockEvent> constructor = EntityChangeBlockEvent.class
                    .getConstructor(Entity.class, Block.class, blockDataClass);
            return constructor.newInstance(entity, block, blockData);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    public static boolean isWaterlogged(@NotNull Block block) {
        Object blockData = getBlockData(block);
        Class<?> waterloggedClass = getClass(WATERLOGGED_CLASS);
        if (blockData == null || waterloggedClass == null || !waterloggedClass.isInstance(blockData)) {
            return false;
        }
        try {
            Object result = waterloggedClass.getMethod("isWaterlogged").invoke(blockData);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public static boolean isWallSign(@NotNull Block block) {
        Object blockData = getBlockData(block);
        Class<?> wallSignClass = getClass(WALL_SIGN_CLASS);
        return blockData != null && wallSignClass != null && wallSignClass.isInstance(blockData);
    }

    public static boolean isWallSignFromBlockData(@Nullable Object blockData) {
        Class<?> wallSignClass = getClass(WALL_SIGN_CLASS);
        return blockData != null && wallSignClass != null && wallSignClass.isInstance(blockData);
    }

    public static @Nullable BlockFace getWallSignFacing(@NotNull Block block) {
        Object blockData = getBlockData(block);
        Class<?> wallSignClass = getClass(WALL_SIGN_CLASS);
        if (blockData == null || wallSignClass == null || !wallSignClass.isInstance(blockData)) {
            return null;
        }
        try {
            Object facing = wallSignClass.getMethod("getFacing").invoke(blockData);
            if (facing instanceof BlockFace) {
                return (BlockFace) facing;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
        return null;
    }

    public static @Nullable BlockFace getWallSignFacingFromBlockData(@Nullable Object blockData) {
        Class<?> wallSignClass = getClass(WALL_SIGN_CLASS);
        if (blockData == null || wallSignClass == null || !wallSignClass.isInstance(blockData)) {
            return null;
        }
        try {
            Object facing = wallSignClass.getMethod("getFacing").invoke(blockData);
            if (facing instanceof BlockFace) {
                return (BlockFace) facing;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
        return null;
    }

    public static boolean setBlockDataLit(@NotNull Block block, boolean lit) {
        Object blockData = getBlockData(block);
        Class<?> lightableClass = getClass(LIGHTABLE_CLASS);
        if (blockData == null || lightableClass == null || !lightableClass.isInstance(blockData)) {
            return false;
        }
        try {
            lightableClass.getMethod("setLit", boolean.class).invoke(blockData, lit);
            setBlockData(block, blockData);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public static @Nullable Object createBlockData(@NotNull Material material) {
        try {
            return Material.class.getMethod("createBlockData").invoke(material);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static @Nullable Object getBlockData(@NotNull Block block) {
        try {
            return Block.class.getMethod("getBlockData").invoke(block);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static void setBlockData(@NotNull Block block, @NotNull Object blockData)
            throws ReflectiveOperationException {
        Class<?> blockDataClass = Class.forName(BLOCK_DATA_CLASS, false, BlockDataCompat.class.getClassLoader());
        Block.class.getMethod("setBlockData", blockDataClass).invoke(block, blockData);
    }

    private static void sendBlockChange(@NotNull Player player, @NotNull Location location, @NotNull Object blockData)
            throws ReflectiveOperationException {
        Class<?> blockDataClass = Class.forName(BLOCK_DATA_CLASS, false, BlockDataCompat.class.getClassLoader());
        Player.class.getMethod("sendBlockChange", Location.class, blockDataClass).invoke(player, location, blockData);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull Object enumValue(@NotNull Class<?> enumClass, @NotNull String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static @Nullable Class<?> getClass(@NotNull String className) {
        try {
            return Class.forName(className, false, BlockDataCompat.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }
}
