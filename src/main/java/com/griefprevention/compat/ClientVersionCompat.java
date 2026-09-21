package com.griefprevention.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Looks up the protocol version a player's client is actually speaking.
 * <p>
 * A server running ViaVersion accepts clients older than itself, so the server's own version says
 * nothing about what a given player can render. Without ViaVersion installed every player speaks the
 * server's protocol, and the lookup reports "unknown" so callers use their server-version behaviour.
 */
public final class ClientVersionCompat {

    /**
     * Protocol version of Minecraft 1.19.4, the oldest client GriefPrevention3D draws glowing
     * boundaries for. Block display entities were added in that version; ViaBackwards translates them
     * into armor stands for older clients, which appear as blocks nudged out of alignment.
     */
    public static final int BLOCK_DISPLAY_PROTOCOL = 762;

    /** Returned when the player's protocol version cannot be determined. */
    public static final int UNKNOWN_PROTOCOL = -1;

    private static boolean resolved = false;
    private static Object viaApi = null;
    private static Method getPlayerVersion = null;

    private ClientVersionCompat() {
        throw new AssertionError("Instantiation of a utility class.");
    }

    /**
     * Get the protocol version of a player's client.
     *
     * @param player the player
     * @return the protocol version, or {@link #UNKNOWN_PROTOCOL} when ViaVersion is not present or
     *         does not know the player
     */
    public static int protocolVersion(@Nullable Player player) {
        if (player == null) return UNKNOWN_PROTOCOL;

        Method method = resolveViaApi();
        if (method == null) return UNKNOWN_PROTOCOL;

        try {
            Object version = method.invoke(viaApi, player.getUniqueId());
            if (!(version instanceof Integer)) return UNKNOWN_PROTOCOL;

            int protocol = (Integer) version;
            // ViaVersion reports -1 for a player it is not tracking.
            return protocol > 0 ? protocol : UNKNOWN_PROTOCOL;
        } catch (Throwable thrown) {
            return UNKNOWN_PROTOCOL;
        }
    }

    /**
     * Whether a player's client renders block display entities.
     *
     * @param player the player
     * @return true unless the client is known to be older than 1.19.4
     */
    public static boolean supportsBlockDisplay(@Nullable Player player) {
        return supportsBlockDisplay(protocolVersion(player));
    }

    /**
     * Whether a protocol version renders block display entities.
     *
     * @param protocolVersion the client's protocol version
     * @return true unless the version is known and older than 1.19.4
     */
    static boolean supportsBlockDisplay(int protocolVersion) {
        // An unknown version means no ViaVersion, so the client matches the server.
        return protocolVersion == UNKNOWN_PROTOCOL || protocolVersion >= BLOCK_DISPLAY_PROTOCOL;
    }

    /**
     * Resolve ViaVersion's API, once it is available. Resolution is retried while ViaVersion is
     * absent so that load order cannot leave the lookup permanently disabled.
     */
    private static synchronized @Nullable Method resolveViaApi() {
        if (resolved) return getPlayerVersion;

        if (!isPluginEnabled("ViaVersion")) return null;

        for (String viaClass : new String[] {
            "com.viaversion.viaversion.api.Via",   // ViaVersion 4.0+
            "us.myles.ViaVersion.api.Via"          // ViaVersion 3.x and older
        }) {
            try {
                Class<?> via = Class.forName(viaClass);
                Object api = via.getMethod("getAPI").invoke(null);
                if (api == null) continue;

                Method method = api.getClass().getMethod("getPlayerVersion", UUID.class);
                method.setAccessible(true);
                viaApi = api;
                getPlayerVersion = method;
                resolved = true;
                return getPlayerVersion;
            } catch (Throwable ignored) {
                // Try the next package, then give up until the next call.
            }
        }

        return null;
    }

    private static boolean isPluginEnabled(@NotNull String name) {
        try {
            return Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled(name);
        } catch (Throwable thrown) {
            return false;
        }
    }
}
