package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Optional WorldGuard integration kept reflection-only so GP3D can still load on
 * servers without WorldGuard (and across the WG 7.x binary API).
 */
final class WorldGuardWrapper {
    private final Plugin worldGuardPlugin;
    private final ClassLoader classLoader;

    WorldGuardWrapper() {
        this.worldGuardPlugin = GriefPrevention.instance.getServer().getPluginManager().getPlugin("WorldGuard");
        if (this.worldGuardPlugin == null || !this.worldGuardPlugin.isEnabled()) {
            throw new IllegalStateException("WorldGuard is not enabled");
        }
        this.classLoader = this.worldGuardPlugin.getClass().getClassLoader();
    }

    boolean canBuild(Location lesserCorner, Location greaterCorner, Player player) {
        if (lesserCorner.getWorld() == null) return true;

        try {
            Class<?> worldGuardClass = load("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = invoke(worldGuard, "getPlatform");
            Object localPlayer = invoke(worldGuardPlugin, "wrapPlayer", player);

            Class<?> bukkitAdapterClass = load("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object world = bukkitAdapterClass
                    .getMethod("adapt", org.bukkit.World.class)
                    .invoke(null, lesserCorner.getWorld());

            Object sessionManager = invoke(platform, "getSessionManager");
            Object bypass = invoke(sessionManager, "hasBypass", localPlayer, world);
            if (Boolean.TRUE.equals(bypass)) return true;

            Object regionContainer = invoke(platform, "getRegionContainer");
            Object regionManager = invoke(regionContainer, "get", world);
            if (regionManager == null) return true;

            Class<?> vectorClass = load("com.sk89q.worldedit.math.BlockVector3");
            Method vectorAt = vectorClass.getMethod("at", int.class, int.class, int.class);
            Object lesser = vectorAt.invoke(null, lesserCorner.getBlockX(),
                    GriefPrevention.getWorldMinY(lesserCorner.getWorld()), lesserCorner.getBlockZ());
            Object greater = vectorAt.invoke(null, greaterCorner.getBlockX(),
                    GriefPrevention.getWorldMaxY(greaterCorner.getWorld()), greaterCorner.getBlockZ());

            Class<?> protectedRegionClass = load("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Class<?> cuboidClass = load("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            Constructor<?> constructor = cuboidClass.getConstructor(String.class, vectorClass, vectorClass);
            Object temporaryRegion = constructor.newInstance("GP_TEMP", lesser, greater);
            Object applicableRegions = invoke(regionManager, "getApplicableRegions", temporaryRegion);

            Class<?> flagsClass = load("com.sk89q.worldguard.protection.flags.Flags");
            Object buildFlag = flagsClass.getField("BUILD").get(null);
            Class<?> stateFlagClass = load("com.sk89q.worldguard.protection.flags.StateFlag");
            Object flags = Array.newInstance(stateFlagClass, 1);
            Array.set(flags, 0, buildFlag);

            Object state = invokeQueryState(applicableRegions, localPlayer, flags);
            return state != null && "ALLOW".equals(state.toString());
        } catch (Throwable error) {
            GriefPrevention.AddLogEntry(
                    "WorldGuard integration could not check a claim area; allowing creation. " + error.getClass().getSimpleName()
                            + ": " + error.getMessage(),
                    CustomLogEntryTypes.Debug, false);
            return true;
        }
    }

    private Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, classLoader);
    }

    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != arguments.length) continue;
            try {
                return method.invoke(target, arguments);
            } catch (IllegalArgumentException ignored) {
                // Try another overload.
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    private static Object invokeQueryState(Object applicableRegions, Object localPlayer, Object flags) throws Exception {
        for (Method method : applicableRegions.getClass().getMethods()) {
            if (!method.getName().equals("queryState") || method.getParameterTypes().length != 2) continue;
            return method.invoke(applicableRegions, localPlayer, flags);
        }
        throw new NoSuchMethodException(applicableRegions.getClass().getName() + "#queryState");
    }
}
