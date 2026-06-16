package me.ryanhamshire.GriefPrevention.compat;

import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LegacyRightClickAirHandler implements Listener {

    private static boolean available = false;
    private static final Map<UUID, float[]> playerLookDirections = new ConcurrentHashMap<>();
    private static Class<?> channelClass;
    private static Class<?> channelHandlerClass;
    private static Class<?> channelInboundHandlerClass;
    private static Class<?> pipelineClass;

    static {
        // DISABLED - Netty injection interferes with player operations on 1.8.8
        // Using Bukkit event approach instead
        available = false;
        GriefPrevention.AddLogEntry("[GP Debug] LegacyRightClickAirHandler disabled - using Bukkit event approach instead");
    }

    public static boolean isAvailable() {
        return available;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!available) return;
        injectPlayer(event.getPlayer());
    }

    private void injectPlayer(Player player) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);
            Object networkManager = playerConnection.getClass().getField("networkManager").get(playerConnection);

            Field channelField = networkManager.getClass().getField("channel");
            Object channel = channelField.get(networkManager);

            Method pipelineMethod = channelClass.getMethod("pipeline");
            Object pipeline = pipelineMethod.invoke(channel);

            Method getMethod = pipelineClass.getMethod("get", String.class);
            getMethod.setAccessible(true);
            if (getMethod.invoke(pipeline, "gp_rightclick") != null) return;

            Object handler = Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{channelHandlerClass, channelInboundHandlerClass},
                    new RightClickInvocationHandler(player)
            );

            Method addBefore = pipelineClass.getMethod("addBefore", String.class, String.class, channelHandlerClass);
            addBefore.setAccessible(true);
            addBefore.invoke(pipeline, "packet_handler", "gp_rightclick", handler);

        } catch (Exception e) {
            GriefPrevention.AddLogEntry("Failed to inject right-click air handler for " + player.getName() + ": " + e.getMessage());
        }
    }

    private static class RightClickInvocationHandler implements InvocationHandler {
        private final Player player;

        RightClickInvocationHandler(Player player) {
            this.player = player;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            if ("channelRead".equals(name) && args != null && args.length == 2) {
                Object ctx = args[0];
                Object msg = args[1];

                boolean consumed = handlePacket(player, msg);

                if (!consumed) {
                    try {
                        Method fireChannelRead = ctx.getClass().getMethod("fireChannelRead", Object.class);
                        fireChannelRead.invoke(ctx, msg);
                    } catch (Exception ignored) {}
                }
                return null;
            }

            if ("exceptionCaught".equals(name) && args != null && args.length == 2) {
                try {
                    Object exCtx = args[0];
                    Method fireExceptionCaught = exCtx.getClass().getMethod("fireExceptionCaught", Throwable.class);
                    fireExceptionCaught.invoke(exCtx, args[1]);
                } catch (Exception ignored) {}
                return null;
            }

            return null;
        }
    }

    private static boolean handlePacket(Player player, Object msg) {
        String packetName = msg.getClass().getSimpleName();

        if (packetName.equals("PacketPlayInLook")) {
            try {
                float yaw = getPacketYaw(msg);
                float pitch = getPacketPitch(msg);
                playerLookDirections.put(player.getUniqueId(), new float[]{yaw, pitch});
                GriefPrevention.AddLogEntry("[LegacyRightClick] Cached look direction: yaw=" + yaw + ", pitch=" + pitch, CustomLogEntryTypes.Debug, false);
            } catch (Exception e) {
                GriefPrevention.AddLogEntry("[LegacyRightClick] Failed to extract yaw/pitch: " + e.getMessage(), CustomLogEntryTypes.Debug, false);
            }
            return false;
        }

        if (!packetName.equals("PacketPlayInBlockPlace")) {
            return false;
        }

        try {
            Object blockPosition = getPosition(msg);
            if (blockPosition == null) return false;

            int x = getCoordinate(blockPosition, "getX");
            int y = getCoordinate(blockPosition, "getY");
            int z = getCoordinate(blockPosition, "getZ");

            GriefPrevention.AddLogEntry("[LegacyRightClick] PacketPlayInBlockPlace received for " + player.getName() + " at " + x + "," + y + "," + z, CustomLogEntryTypes.Debug, false);

            if (x != -1 || y != -1 || z != -1) {
                GriefPrevention.AddLogEntry("[LegacyRightClick] Not air click (coordinates not -1), ignoring", CustomLogEntryTypes.Debug, false);
                return false;
            }

            ItemStack packetItem = getItemFromBlockPlacePacket(msg);
            Material handMaterial = packetItem == null ? Material.AIR : packetItem.getType();
            Material modificationTool = GriefPrevention.instance.config_claims_modificationTool;
            Material investigationTool = GriefPrevention.instance.config_claims_investigationTool;

            GriefPrevention.AddLogEntry("[LegacyRightClick] Item from packet: " + handMaterial + ", ModTool: " + modificationTool + ", InvTool: " + investigationTool, CustomLogEntryTypes.Debug, false);

            if (handMaterial != modificationTool && handMaterial != investigationTool) {
                GriefPrevention.AddLogEntry("[LegacyRightClick] Not a claim tool at packet time, ignoring", CustomLogEntryTypes.Debug, false);
                return false;
            }

            // Call direct entrypoint to avoid duplicate event paths
            Player fPlayer = player;
            float[] cachedLook = playerLookDirections.get(player.getUniqueId());
            float pitch = cachedLook != null ? cachedLook[1] : 0;
            float yaw = cachedLook != null ? cachedLook[0] : 0;

            Bukkit.getScheduler().runTask(GriefPrevention.instance, () -> {
                try {
                    Player currentPlayer = Bukkit.getPlayer(fPlayer.getUniqueId());
                    if (currentPlayer == null) {
                        return;
                    }

                    // Call direct entrypoint with cached yaw/pitch (no NMS mutation)
                    GriefPrevention.instance.playerEventHandler.handleLegacyRightClickAir(currentPlayer, packetItem, yaw, pitch);
                } catch (Exception e) {
                    GriefPrevention.AddLogEntry("Right-click air direct call failed for " + fPlayer.getName() + ": " + e.getClass().getSimpleName());
                    e.printStackTrace();
                }
            });

            return true; // Consume packet to prevent normal Bukkit event

        } catch (Exception e) {
            GriefPrevention.AddLogEntry("Error handling right-click air packet for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static Object getPosition(Object packet) throws Exception {
        try {
            return packet.getClass().getMethod("getPosition").invoke(packet);
        } catch (NoSuchMethodException e) {
            return packet.getClass().getMethod("a").invoke(packet);
        }
    }

    private static int getCoordinate(Object blockPosition, String methodName) throws Exception {
        return (int) blockPosition.getClass().getMethod(methodName).invoke(blockPosition);
    }

    private static float getPacketYaw(Object packet) throws Exception {
        try {
            Method m = packet.getClass().getMethod("d");
            if (m.getReturnType() == float.class) {
                return (float) m.invoke(packet);
            }
        } catch (NoSuchMethodException ignored) {}

        Field f = findFieldInHierarchy(packet.getClass(), "yaw");
        return f.getFloat(packet);
    }

    private static float getPacketPitch(Object packet) throws Exception {
        try {
            Method m = packet.getClass().getMethod("e");
            if (m.getReturnType() == float.class) {
                return (float) m.invoke(packet);
            }
        } catch (NoSuchMethodException ignored) {}

        Field f = findFieldInHierarchy(packet.getClass(), "pitch");
        return f.getFloat(packet);
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found in " + clazz.getName() + " or its superclasses");
    }

    private static ItemStack getItemFromBlockPlacePacket(Object packet) {
        try {
            Method m = packet.getClass().getMethod("getItemStack");
            Object nmsItem = m.invoke(packet);

            if (nmsItem == null) {
                return null;
            }

            // Convert NMS ItemStack to Bukkit ItemStack
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsItemStack = Class.forName("net.minecraft.server." + version + ".ItemStack");

            Method asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsItemStack);
            return (ItemStack) asBukkitCopy.invoke(null, nmsItem);
        } catch (Throwable t) {
            GriefPrevention.AddLogEntry("[LegacyRightClick] Failed to get item from packet: " + t.getMessage(), CustomLogEntryTypes.Debug, false);
            return null;
        }
    }
}
