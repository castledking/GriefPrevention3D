package com.griefprevention.util;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Looks up the protocol version a player's client is speaking via ViaVersion / ViaBackwards reflection.
 * <p>
 * Safe to call on any platform (Paper, Folia, Fabric) whether ViaVersion is present or not.
 */
public final class ClientProtocolLookup
{
    /** Protocol version of Minecraft 1.10.x. Clients with protocol < 210 are older than 1.10. */
    public static final int MINECRAFT_1_10 = 210;

    /** Protocol version of Minecraft 1.12. Glazed terracotta was added in this version (335). */
    public static final int MINECRAFT_1_12 = 335;

    /** Protocol version of Minecraft 1.19.4. Block display entities were added in this version (762). */
    public static final int BLOCK_DISPLAY = 762;

    /** Returned when the client protocol cannot be determined or ViaVersion is absent. */
    public static final int UNKNOWN_PROTOCOL = -1;

    private static boolean resolved = false;
    private static Object viaApi = null;
    private static Method getPlayerVersion = null;

    private ClientProtocolLookup()
    {
    }

    /**
     * Get the protocol version of a player's client.
     *
     * @param playerUuid the UUID of the player
     * @return the protocol version, or {@link #UNKNOWN_PROTOCOL} when ViaVersion is not present or unknown
     */
    public static int getProtocolVersion(@Nullable UUID playerUuid)
    {
        if (playerUuid == null)
        {
            return UNKNOWN_PROTOCOL;
        }

        Method method = resolveViaApi();
        if (method == null)
        {
            return UNKNOWN_PROTOCOL;
        }

        try
        {
            Object version = method.invoke(viaApi, playerUuid);
            if (version instanceof Integer)
            {
                int protocol = (Integer) version;
                return protocol > 0 ? protocol : UNKNOWN_PROTOCOL;
            }
        }
        catch (Throwable ignored)
        {
        }
        return UNKNOWN_PROTOCOL;
    }

    /**
     * Whether a client protocol is older than Minecraft 1.10 (or 1.12 where glazed terracotta was added).
     *
     * @param playerUuid the player UUID
     * @return true if the client is confirmed to be on a protocol before 1.12 (< 335)
     */
    public static boolean isPreGlazedTerracotta(@Nullable UUID playerUuid)
    {
        int protocol = getProtocolVersion(playerUuid);
        return protocol != UNKNOWN_PROTOCOL && protocol < MINECRAFT_1_12;
    }

    /**
     * Whether a client protocol supports BlockDisplay entities (1.19.4+, protocol >= 762).
     *
     * @param playerUuid the player UUID
     * @return true unless the version is known and older than 1.19.4
     */
    public static boolean supportsBlockDisplay(@Nullable UUID playerUuid)
    {
        int protocol = getProtocolVersion(playerUuid);
        return protocol == UNKNOWN_PROTOCOL || protocol >= BLOCK_DISPLAY;
    }

    private static synchronized @Nullable Method resolveViaApi()
    {
        if (resolved)
        {
            return getPlayerVersion;
        }

        for (String viaClass : new String[] {
                "com.viaversion.viaversion.api.Via",
                "us.myles.ViaVersion.api.Via"
        })
        {
            try
            {
                Class<?> via = Class.forName(viaClass);
                Object api = via.getMethod("getAPI").invoke(null);
                if (api == null)
                {
                    continue;
                }
                Method method = api.getClass().getMethod("getPlayerVersion", UUID.class);
                method.setAccessible(true);
                viaApi = api;
                getPlayerVersion = method;
                resolved = true;
                return getPlayerVersion;
            }
            catch (Throwable ignored)
            {
            }
        }
        return null;
    }
}
