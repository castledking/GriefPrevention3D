package me.ryanhamshire.GriefPrevention.compat;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class LegacyRightClickAirHandler implements Listener {

    private static boolean available = false;
    private static Class<?> channelClass;
    private static Class<?> channelHandlerClass;
    private static Class<?> channelInboundHandlerClass;
    private static Class<?> pipelineClass;

    static {
        try {
            channelClass = Class.forName("io.netty.channel.Channel");
            channelHandlerClass = Class.forName("io.netty.channel.ChannelHandler");
            channelInboundHandlerClass = Class.forName("io.netty.channel.ChannelInboundHandler");
            pipelineClass = Class.forName("io.netty.channel.ChannelPipeline");
            available = true;
        } catch (ClassNotFoundException e) {
            GriefPrevention.AddLogEntry("Netty not available; right-click air handler disabled.");
        }
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

            Method getMethod = pipeline.getClass().getMethod("get", String.class);
            if (getMethod.invoke(pipeline, "gp_rightclick") != null) return;

            Object handler = Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{channelHandlerClass, channelInboundHandlerClass},
                    new RightClickInvocationHandler(player)
            );

            Method addBefore = pipelineClass.getMethod("addBefore", String.class, String.class, channelHandlerClass);
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

            if ("channelRead".equals(name) && args != null && args.length == 3) {
                Object ctx = args[0];
                Object msg = args[2];

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
        if (!msg.getClass().getSimpleName().equals("PacketPlayInBlockPlace")) return false;

        try {
            Object blockPosition = getPosition(msg);
            if (blockPosition == null) return false;

            int x = getCoordinate(blockPosition, "getX");
            int y = getCoordinate(blockPosition, "getY");
            int z = getCoordinate(blockPosition, "getZ");

            if (x != -1 || y != -1 || z != -1) return false;

            Material handMaterial = player.getItemInHand().getType();
            if (handMaterial != GriefPrevention.instance.config_claims_modificationTool
                    && handMaterial != GriefPrevention.instance.config_claims_investigationTool) {
                return false;
            }

            Player fPlayer = player;
            Bukkit.getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, () -> {
                try {
                    PlayerInteractEvent fakeEvent = new PlayerInteractEvent(
                            fPlayer, Action.RIGHT_CLICK_AIR, fPlayer.getItemInHand(), null, null
                    );
                    Bukkit.getPluginManager().callEvent(fakeEvent);
                } catch (Exception e) {
                    GriefPrevention.AddLogEntry("Right-click air fake event failed for " + fPlayer.getName() + ": " + e.getClass().getSimpleName());
                }
            }, 0L);

            return true;

        } catch (Exception e) {
            GriefPrevention.AddLogEntry("Error handling right-click air packet for " + player.getName() + ": " + e.getMessage());
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
}
