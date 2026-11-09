package com.griefprevention.compat;

import com.griefprevention.util.ClientProtocolLookup;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

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
    public static final int BLOCK_DISPLAY_PROTOCOL = ClientProtocolLookup.BLOCK_DISPLAY;

    /** Protocol version of Minecraft 1.10.x. */
    public static final int MINECRAFT_1_10_PROTOCOL = ClientProtocolLookup.MINECRAFT_1_10;

    /** Protocol version of Minecraft 1.12 (glazed terracotta added). */
    public static final int GLAZED_TERRACOTTA_PROTOCOL = ClientProtocolLookup.MINECRAFT_1_12;

    /** Returned when the player's protocol version cannot be determined. */
    public static final int UNKNOWN_PROTOCOL = ClientProtocolLookup.UNKNOWN_PROTOCOL;

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
        return ClientProtocolLookup.getProtocolVersion(player.getUniqueId());
    }

    /**
     * Whether a player's client is on a version before glazed terracotta was introduced (protocol < 335 / < 1.12, including < 1.10).
     *
     * @param player the player
     * @return true if the client is confirmed to be older than 1.12
     */
    public static boolean isPreGlazedTerracotta(@Nullable Player player) {
        if (player == null) return false;
        return ClientProtocolLookup.isPreGlazedTerracotta(player.getUniqueId());
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
}
