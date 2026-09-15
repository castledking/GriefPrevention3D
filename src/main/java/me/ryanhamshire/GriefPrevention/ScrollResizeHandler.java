package me.ryanhamshire.GriefPrevention;

import com.griefprevention.events.BoundaryVisualizationEvent;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.Face;
import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MAX_X;
import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MAX_Y;
import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MAX_Z;
import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MIN_X;
import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MIN_Y;
import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MIN_Z;

/**
 * Runs {@code /scrollresize} sessions. While a session is active the mouse wheel pushes the side of the claim the
 * camera points at away from the player (scroll up) or pulls it closer (scroll down) and previews the new outline.
 * Right-click applies the
 * resize; left-click, drop (Q), swap hands (F), a number key, leaving the claim tool, changing worlds, or a minute
 * without scrolling cancels it.
 */
public class ScrollResizeHandler implements Listener
{
    private static final long IDLE_TIMEOUT_MILLIS = 60_000L;
    private static final long SCROLL_BURST_MILLIS = 250L;
    private static final long CHAT_STATUS_INTERVAL_MILLIS = 1_000L;
    private static final long NEARBY_REFRESH_MILLIS = 2_000L;
    private static final long LEFT_CLICK_GUARD_MILLIS = 250L;
    private static final long TICK_PERIOD = 10L;
    private static final int TARGET_RANGE = 6;

    private final @NotNull GriefPrevention plugin;
    private final @NotNull DataStore dataStore;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerInteractEvent> handledInteracts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentLeftClickCancels = new ConcurrentHashMap<>();

    public ScrollResizeHandler(@NotNull GriefPrevention plugin, @NotNull DataStore dataStore)
    {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public boolean isActive(@NotNull Player player)
    {
        return this.sessions.containsKey(player.getUniqueId());
    }

    /**
     * Whether a scroll resize session already used this interaction, in which case the claim tool must ignore it.
     */
    boolean wasHandled(@NotNull PlayerInteractEvent event)
    {
        return this.handledInteracts.remove(event.getPlayer().getUniqueId(), event);
    }

    /**
     * Pre-1.13 right-click-air packets go straight to the claim tool instead of through Bukkit events.
     *
     * @return true if a session used the click
     */
    boolean handleLegacyRightClick(@NotNull Player player)
    {
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return false;

        confirm(player, session);
        return true;
    }

    /**
     * Cancels the session from a key press that has its own event, such as swap hands.
     *
     * @return true if a session was active
     */
    boolean cancelFromKey(@NotNull Player player)
    {
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return false;

        cancel(player, session, Messages.ScrollResizeCancelled);
        return true;
    }

    public void start(@NotNull Player player)
    {
        if (!isHoldingTool(player))
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ScrollResizeNeedTool);
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerData playerData = this.dataStore.getPlayerData(playerId);
        this.sessions.remove(playerId);

        // A claim selected by right-clicking one of its corners limits the session to the sides at that corner.
        Claim claim;
        Location corner = null;
        Claim selected = playerData.claimResizing != null && playerData.claimResizing.inDataStore
                ? playerData.claimResizing
                : null;
        Claim standingIn = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);

        // The subdivision the player is looking at wins over a leftover selection of the claim around it and over
        // where they stand, so claims too small to stand in, like a 1x1x1 subdivision, are easy to target.
        Claim lookingAt = claimAtTargetBlock(player, playerData);
        if (lookingAt != null
                && !lookingAt.isShaped()
                && depth(lookingAt) > depth(selected)
                && depth(lookingAt) > depth(standingIn))
        {
            claim = lookingAt;
        }
        else if (selected != null)
        {
            claim = selected;
            corner = playerData.lastShovelLocation;
        }
        else
        {
            claim = standingIn;
        }

        if (claim == null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ScrollResizeNoClaim);
            return;
        }

        if (claim.checkPermission(player, ClaimPermission.Edit, null) != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
            return;
        }

        playerData.claimResizing = null;
        playerData.claimSelectionActive = false;
        playerData.lastShovelLocation = null;

        long now = System.currentTimeMillis();
        Session session = new Session(claim, corner, player.getInventory().getHeldItemSlot(), now);
        this.sessions.put(playerId, session);

        GriefPrevention.sendMessage(
                player,
                TextMode.Instr,
                session.isCornerSelection() ? Messages.ScrollResizeStartCorner : Messages.ScrollResizeStart);
        requestRender(player, session);
        scheduleTick(player, session);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemHeld(@NotNull PlayerItemHeldEvent event)
    {
        Player player = event.getPlayer();
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return;

        long now = System.currentTimeMillis();
        // Once a burst of scrolling ends, the server's slot reset has reached the client.
        if (now - session.lastScrollMillis > SCROLL_BURST_MILLIS)
        {
            session.clientSlot = event.getPreviousSlot();
        }

        int step = ScrollResizeGeometry.scrollStep(event.getPreviousSlot(), event.getNewSlot(), session.clientSlot);
        if (step == 0)
        {
            // A number key switches straight to another slot: let it through and end the session.
            cancel(player, session, Messages.ScrollResizeCancelled);
            return;
        }

        event.setCancelled(true);
        session.clientSlot = event.getNewSlot();
        session.lastScrollMillis = now;
        session.lastActivityMillis = now;
        applyStep(player, session, step);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(@NotNull PlayerInteractEvent event)
    {
        Player player = event.getPlayer();
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null || event.getAction() == Action.PHYSICAL) return;

        event.setCancelled(true);
        this.handledInteracts.put(player.getUniqueId(), event);

        EquipmentSlot hand = CompatUtil.getInteractEventHand(event);
        if (hand != null && hand != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR)
        {
            confirm(player, session);
        }
        else
        {
            this.recentLeftClickCancels.put(player.getUniqueId(), System.currentTimeMillis());
            cancel(player, session, Messages.ScrollResizeCancelled);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event)
    {
        Player player = event.getPlayer();
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        if (isMainHand(event)) confirm(player, session);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(@NotNull EntityDamageByEntityEvent event)
    {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player)) return;

        Player player = (Player) damager;
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        cancel(player, session, Messages.ScrollResizeCancelled);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(@NotNull BlockBreakEvent event)
    {
        UUID playerId = event.getPlayer().getUniqueId();
        if (this.sessions.containsKey(playerId))
        {
            event.setCancelled(true);
            return;
        }

        // A creative-mode left-click that cancelled a session must not break the block it hit.
        Long cancelledAt = this.recentLeftClickCancels.get(playerId);
        if (cancelledAt == null) return;

        if (System.currentTimeMillis() - cancelledAt <= LEFT_CLICK_GUARD_MILLIS)
        {
            event.setCancelled(true);
        }
        else
        {
            this.recentLeftClickCancels.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(@NotNull PlayerDropItemEvent event)
    {
        Player player = event.getPlayer();
        Session session = this.sessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        cancel(player, session, Messages.ScrollResizeCancelled);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(@NotNull PlayerChangedWorldEvent event)
    {
        Player player = event.getPlayer();
        Session session = this.sessions.get(player.getUniqueId());
        if (session != null) cancel(player, session, Messages.ScrollResizeCancelled);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(@NotNull PlayerDeathEvent event)
    {
        Player player = event.getEntity();
        Session session = this.sessions.get(player.getUniqueId());
        if (session != null) cancel(player, session, Messages.ScrollResizeCancelled);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event)
    {
        UUID playerId = event.getPlayer().getUniqueId();
        this.sessions.remove(playerId);
        this.handledInteracts.remove(playerId);
        this.recentLeftClickCancels.remove(playerId);
    }

    private void applyStep(@NotNull Player player, @NotNull Session session, int scroll)
    {
        Vector look = player.getLocation().getDirection();
        if (session.shaped)
        {
            Integer edgeIndex = session.corner != null
                    ? ScrollResizeGeometry.cornerEdge(session.polygon, session.corner, look.getX(), look.getZ())
                    : this.plugin.resolveShapedSegmentForPlayer(session.polygon, player.getLocation());
            if (edgeIndex == null)
            {
                sendStatus(player, session, TextMode.Err, message(player, Messages.ScrollResizeNoSegment), true);
                return;
            }

            int[] outward = ScrollResizeGeometry.outwardNormal(session.polygon, edgeIndex);
            int amount = ScrollResizeGeometry.directedStep(scroll, outward[0] * look.getX() + outward[1] * look.getZ());
            OrthogonalPolygon next = this.plugin.expandShapedSegment(session.claim, session.polygon, edgeIndex, amount);
            if (next == null)
            {
                sendStatus(player, session, TextMode.Err, message(player, Messages.ScrollResizeLimit), true);
                return;
            }

            if (session.corner != null)
            {
                OrthogonalPoint2i moved = ScrollResizeGeometry.nearestCorner(next, session.corner, Math.abs(amount));
                if (moved != null) session.corner = moved;
            }
            session.polygon = next;
        }
        else
        {
            // Target the side the player's line of sight hits; fall back to the camera's main axis when it misses or
            // hits a side a corner selection doesn't reach.
            Location eye = player.getEyeLocation();
            Face face = ScrollResizeGeometry.rayFace(
                    eye.getX(),
                    eye.getY(),
                    eye.getZ(),
                    look.getX(),
                    look.getY(),
                    look.getZ(),
                    session.bounds,
                    session.claim.is3D());
            if (face == null || !session.allowedFaces.contains(face))
            {
                face = ScrollResizeGeometry.pickFace(look.getX(), look.getY(), look.getZ(), session.allowedFaces);
            }
            int[] next = null;
            if (face != null)
            {
                double facing = ScrollResizeGeometry.facing(face, look.getX(), look.getY(), look.getZ());
                next = ScrollResizeGeometry.step(session.bounds, face, ScrollResizeGeometry.directedStep(scroll, facing));
            }
            if (next == null)
            {
                sendStatus(player, session, TextMode.Err, message(player, Messages.ScrollResizeLimit), true);
                return;
            }
            session.bounds = next;
        }

        requestRender(player, session);
    }

    private void confirm(@NotNull Player player, @NotNull Session session)
    {
        Claim claim = session.claim;
        UUID playerId = player.getUniqueId();

        if (!session.hasChanges())
        {
            if (this.sessions.remove(playerId, session))
            {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.ScrollResizeUnchanged);
                restoreOutline(player, claim);
            }
            return;
        }

        // A known conflict keeps the session open so the player can scroll back instead of losing the preview.
        Claim preview = previewClaim(session);
        session.conflict = findConflict(player, session, preview);
        if (session.conflict != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, session.conflict);
            return;
        }

        if (!this.sessions.remove(playerId, session)) return;

        if (!claim.inDataStore)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ScrollResizeClaimGone);
            this.dataStore.getPlayerData(playerId).setVisibleBoundaries(null);
            return;
        }

        if (claim.checkPermission(player, ClaimPermission.Edit, null) != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
            restoreOutline(player, claim);
            return;
        }

        PlayerData playerData = this.dataStore.getPlayerData(playerId);
        if (session.shaped)
        {
            CreateClaimResult result = this.dataStore.updateShapedClaim(player, playerData, claim, session.polygon);
            if (!result.succeeded || result.claim == null)
            {
                if (result.denialMessage != null)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, result.denialMessage.get());
                }
                else if (result.claim != null)
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);
                    BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE);
                    return;
                }
                else
                {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
                }
                restoreOutline(player, claim);
                return;
            }

            GriefPrevention.sendMessage(
                    player,
                    TextMode.Success,
                    Messages.ClaimResizeSuccess,
                    String.valueOf(playerData.getRemainingClaimBlocks()));
            BoundaryVisualization.visualizeClaim(player, result.claim, visualizationType(result.claim, false));
            return;
        }

        // Rectangles and 2D/3D subdivisions go through the same checks as the golden shovel.
        int[] bounds = session.bounds;
        playerData.claimResizing = claim;
        playerData.lastShovelLocation = null;
        this.dataStore.resizeClaimWithChecks(
                player,
                playerData,
                bounds[MIN_X],
                bounds[MAX_X],
                bounds[MIN_Y],
                bounds[MAX_Y],
                bounds[MIN_Z],
                bounds[MAX_Z]);
        playerData.claimResizing = null;
    }

    private void cancel(@NotNull Player player, @NotNull Session session, @Nullable Messages message)
    {
        if (!this.sessions.remove(player.getUniqueId(), session)) return;
        if (!player.isOnline()) return;

        restoreOutline(player, session.claim);
        if (message != null) GriefPrevention.sendMessage(player, TextMode.Info, message);
    }

    private void restoreOutline(@NotNull Player player, @NotNull Claim claim)
    {
        if (claim.inDataStore)
        {
            BoundaryVisualization.visualizeClaim(player, claim, visualizationType(claim, false));
        }
        else
        {
            this.dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
        }
    }

    private void scheduleTick(@NotNull Player player, @NotNull Session session)
    {
        SchedulerUtil.runLaterEntity(this.plugin, player, () -> tick(player, session), TICK_PERIOD);
    }

    private void tick(@NotNull Player player, @NotNull Session session)
    {
        UUID playerId = player.getUniqueId();
        if (this.sessions.get(playerId) != session) return;

        if (!player.isOnline())
        {
            this.sessions.remove(playerId, session);
            return;
        }

        if (!isHoldingTool(player))
        {
            cancel(player, session, Messages.ScrollResizeCancelled);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - session.lastActivityMillis >= IDLE_TIMEOUT_MILLIS)
        {
            cancel(player, session, Messages.ScrollResizeTimedOut);
            return;
        }

        // Keep neighbouring claims visible as the player moves, so they can see what they're about to run into.
        if (now - session.nearbyRefreshedMillis >= NEARBY_REFRESH_MILLIS)
        {
            Set<Claim> previous = session.nearbyClaims;
            session.nearbyClaims = null;
            if (!nearbyClaims(player, session).equals(previous)) requestRender(player, session);
        }

        flushChatStatus(player, session, now);
        scheduleTick(player, session);
    }

    private void requestRender(@NotNull Player player, @NotNull Session session)
    {
        // Several scroll steps can arrive in one tick; draw the preview at most once per tick.
        if (session.renderScheduled) return;
        session.renderScheduled = true;

        SchedulerUtil.runLaterEntity(this.plugin, player, () -> {
            session.renderScheduled = false;
            if (this.sessions.get(player.getUniqueId()) == session && player.isOnline())
            {
                render(player, session);
            }
        }, 1L);
    }

    private void render(@NotNull Player player, @NotNull Session session)
    {
        Claim claim = session.claim;
        Claim preview = previewClaim(session);
        session.conflict = findConflict(player, session, preview);

        Set<Boundary> boundaries = new HashSet<>();
        boundaries.add(new Boundary(preview, visualizationType(preview, session.conflict != null)));

        if (claim.parent != null)
        {
            boundaries.add(new Boundary(claim.parent, visualizationType(claim.parent, false)));
            for (Claim sibling : claim.parent.children)
            {
                if (sibling != claim && sibling.inDataStore)
                {
                    boundaries.add(new Boundary(sibling, visualizationType(sibling, false)));
                }
            }
        }

        for (Claim child : claim.children)
        {
            if (child.inDataStore) boundaries.add(new Boundary(child, visualizationType(child, false)));
        }

        Claim topLevel = topLevel(claim);
        for (Claim nearby : nearbyClaims(player, session))
        {
            if (!nearby.inDataStore || nearby.parent != null || Objects.equals(nearby.getID(), topLevel.getID())) continue;
            boundaries.add(new Boundary(nearby, visualizationType(nearby, false)));
        }

        BoundaryVisualization.callAndVisualize(
                new BoundaryVisualizationEvent(player, boundaries, player.getEyeLocation().getBlockY()));
        sendSizeStatus(player, session, preview);
    }

    private void sendSizeStatus(@NotNull Player player, @NotNull Session session, @NotNull Claim preview)
    {
        Location lesser = preview.getLesserBoundaryCorner();
        Location greater = preview.getGreaterBoundaryCorner();
        String width = String.valueOf(greater.getBlockX() - lesser.getBlockX() + 1);
        String length = String.valueOf(greater.getBlockZ() - lesser.getBlockZ() + 1);

        String size;
        if (session.shaped)
        {
            size = message(player, Messages.ScrollResizeSizeShaped, String.valueOf(preview.getArea()));
        }
        else if (preview.is3D())
        {
            String height = String.valueOf(greater.getBlockY() - lesser.getBlockY() + 1);
            size = message(player, Messages.ScrollResizeSize3D, width, length, height);
        }
        else
        {
            size = message(player, Messages.ScrollResizeSize, width, length);
        }

        if (session.conflict != null)
        {
            sendStatus(player, session, TextMode.Err, size + " - " + session.conflict, true);
            return;
        }

        Integer remaining = remainingClaimBlocks(player, session.claim, preview);
        if (remaining != null)
        {
            size = message(player, Messages.ScrollResizeBlocksLeft, size, String.valueOf(remaining));
        }
        sendStatus(player, session, TextMode.Instr, size, false);
    }

    private void sendStatus(
            @NotNull Player player,
            @NotNull Session session,
            @NotNull ChatColor color,
            @NotNull String text,
            boolean important)
    {
        String colored = color + text;
        try
        {
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colored));
            return;
        }
        catch (NoSuchMethodError | NoClassDefFoundError ignored)
        {
            // No action bar API (1.8.8): fall back to chat without a line per scroll step.
        }

        if (colored.equals(session.lastChatStatus))
        {
            session.pendingChatStatus = null;
            return;
        }

        if (important)
        {
            sendChatStatus(player, session, colored, System.currentTimeMillis());
        }
        else
        {
            // Routine size updates wait until scrolling pauses; see flushChatStatus.
            session.pendingChatStatus = colored;
        }
    }

    private void flushChatStatus(@NotNull Player player, @NotNull Session session, long now)
    {
        String pending = session.pendingChatStatus;
        if (pending == null) return;
        if (now - session.lastScrollMillis < CHAT_STATUS_INTERVAL_MILLIS) return;
        if (now - session.lastChatStatusMillis < CHAT_STATUS_INTERVAL_MILLIS) return;

        sendChatStatus(player, session, pending, now);
    }

    private static void sendChatStatus(@NotNull Player player, @NotNull Session session, @NotNull String text, long now)
    {
        player.sendMessage(text);
        session.lastChatStatus = text;
        session.lastChatStatusMillis = now;
        session.pendingChatStatus = null;
    }

    private @Nullable String findConflict(@NotNull Player player, @NotNull Session session, @NotNull Claim preview)
    {
        Claim claim = session.claim;
        for (Claim child : claim.children)
        {
            if (child.inDataStore && !containsFootprint(preview, child))
            {
                return message(player, Messages.ScrollResizeConflict);
            }
        }

        if (claim.parent == null)
        {
            for (Claim other : nearbyClaims(player, session))
            {
                if (!other.inDataStore || other.parent != null || Objects.equals(other.getID(), claim.getID())) continue;
                if (preview.overlaps(other)) return message(player, Messages.ScrollResizeConflict);
            }

            Integer remaining = remainingClaimBlocks(player, claim, preview);
            if (remaining != null && remaining < 0)
            {
                return message(player, Messages.ScrollResizeNotEnoughBlocks, String.valueOf(-remaining));
            }
            return null;
        }

        Claim parent = claim.parent;
        if (!containsFootprint(parent, preview)) return message(player, Messages.ScrollResizeConflict);

        for (Claim sibling : parent.children)
        {
            if (sibling == claim || !sibling.inDataStore) continue;
            if (preview.is3D() && sibling.is3D() && verticallySeparated(preview, sibling)) continue;
            if (preview.overlaps(sibling)) return message(player, Messages.ScrollResizeConflict);
        }
        return null;
    }

    private @Nullable Integer remainingClaimBlocks(@NotNull Player player, @NotNull Claim claim, @NotNull Claim preview)
    {
        if (claim.parent != null || claim.isAdminClaim() || !player.getUniqueId().equals(claim.getOwnerID())) return null;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        return playerData.getRemainingClaimBlocks() + claim.getArea() - preview.getArea();
    }

    private @NotNull Set<Claim> nearbyClaims(@NotNull Player player, @NotNull Session session)
    {
        if (session.nearbyClaims == null)
        {
            session.nearbyClaims = this.dataStore.getNearbyClaims(player.getLocation());
            session.nearbyRefreshedMillis = System.currentTimeMillis();
        }
        return session.nearbyClaims;
    }

    private @NotNull String message(@NotNull Player player, @NotNull Messages messageID, @NotNull String... args)
    {
        return this.dataStore.getMessage(player, messageID, args);
    }

    private boolean isHoldingTool(@NotNull Player player)
    {
        ItemStack item = CompatUtil.getItemInMainHand(player);
        return item != null && item.getType() == this.plugin.config_claims_modificationTool;
    }

    private @Nullable Claim claimAtTargetBlock(@NotNull Player player, @NotNull PlayerData playerData)
    {
        Block target;
        try
        {
            target = PlayerEventHandler.getTargetBlock(player, TARGET_RANGE);
        }
        catch (IllegalStateException e)
        {
            return null;
        }

        return target == null ? null : this.dataStore.getClaimAt(target.getLocation(), false, playerData.lastClaim);
    }

    private static int depth(@Nullable Claim claim)
    {
        int depth = -1;
        for (Claim cursor = claim; cursor != null; cursor = cursor.parent) depth++;
        return depth;
    }

    private static boolean isMainHand(@NotNull PlayerInteractEntityEvent event)
    {
        try
        {
            return event.getHand() == EquipmentSlot.HAND;
        }
        catch (NoSuchMethodError | NoClassDefFoundError e)
        {
            // 1.8.8 has a single hand.
            return true;
        }
    }

    /** A detached copy of the claim with the session's current outline, for previews and conflict checks. */
    private static @NotNull Claim previewClaim(@NotNull Session session)
    {
        Claim claim = session.claim;
        Claim preview = new Claim(claim);
        World world = claim.getLesserBoundaryCorner().getWorld();
        int[] bounds = session.bounds;

        if (session.shaped)
        {
            OrthogonalPolygon polygon = session.polygon;
            preview.setShapedCorners(polygon.corners());
            preview.lesserBoundaryCorner = new Location(world, polygon.minX(), bounds[MIN_Y], polygon.minZ());
            preview.greaterBoundaryCorner = new Location(world, polygon.maxX(), bounds[MAX_Y], polygon.maxZ());
        }
        else
        {
            preview.lesserBoundaryCorner = new Location(world, bounds[MIN_X], bounds[MIN_Y], bounds[MIN_Z]);
            preview.greaterBoundaryCorner = new Location(world, bounds[MAX_X], bounds[MAX_Y], bounds[MAX_Z]);
        }
        return preview;
    }

    static boolean containsFootprint(@NotNull Claim outer, @NotNull Claim inner)
    {
        World world = outer.getLesserBoundaryCorner().getWorld();
        int y = outer.getLesserBoundaryCorner().getBlockY();
        for (OrthogonalPoint2i corner : footprintCorners(inner))
        {
            if (!outer.contains(new Location(world, corner.x(), y, corner.z()), true, false)) return false;
        }
        return true;
    }

    // A rectangular claim one block wide isn't a valid outline polygon, so only shaped claims use their outline.
    static @NotNull List<OrthogonalPoint2i> footprintCorners(@NotNull Claim claim)
    {
        if (claim.isShaped()) return claim.getBoundaryPolygon().corners();

        Location lesser = claim.getLesserBoundaryCorner();
        Location greater = claim.getGreaterBoundaryCorner();
        int minX = Math.min(lesser.getBlockX(), greater.getBlockX());
        int maxX = Math.max(lesser.getBlockX(), greater.getBlockX());
        int minZ = Math.min(lesser.getBlockZ(), greater.getBlockZ());
        int maxZ = Math.max(lesser.getBlockZ(), greater.getBlockZ());
        return Arrays.asList(
                new OrthogonalPoint2i(minX, minZ),
                new OrthogonalPoint2i(maxX, minZ),
                new OrthogonalPoint2i(maxX, maxZ),
                new OrthogonalPoint2i(minX, maxZ));
    }

    private static boolean verticallySeparated(@NotNull Claim first, @NotNull Claim second)
    {
        return first.getMaxY() < second.getMinY() || first.getMinY() > second.getMaxY();
    }

    private static @NotNull Claim topLevel(@NotNull Claim claim)
    {
        Claim topLevel = claim;
        while (topLevel.parent != null) topLevel = topLevel.parent;
        return topLevel;
    }

    private static @NotNull VisualizationType visualizationType(@NotNull Claim claim, boolean conflict)
    {
        boolean is3D = claim.is3D();
        if (conflict) return is3D ? VisualizationType.CONFLICT_ZONE_3D : VisualizationType.CONFLICT_ZONE;
        if (claim.parent != null) return is3D ? VisualizationType.SUBDIVISION_3D : VisualizationType.SUBDIVISION;
        if (claim.isAdminClaim()) return is3D ? VisualizationType.ADMIN_CLAIM_3D : VisualizationType.ADMIN_CLAIM;
        return VisualizationType.CLAIM;
    }

    private static final class Session
    {
        private final @NotNull Claim claim;
        private final boolean shaped;
        private final int @NotNull [] originalBounds;
        private int @NotNull [] bounds;
        private final @NotNull EnumSet<Face> allowedFaces;
        private final @Nullable OrthogonalPolygon originalPolygon;
        private OrthogonalPolygon polygon;
        private @Nullable OrthogonalPoint2i corner;
        private int clientSlot;
        private long lastScrollMillis;
        private long lastActivityMillis;
        private boolean renderScheduled;
        private @Nullable Set<Claim> nearbyClaims;
        private long nearbyRefreshedMillis;
        private @Nullable String conflict;
        private @Nullable String pendingChatStatus;
        private @Nullable String lastChatStatus;
        private long lastChatStatusMillis;

        private Session(@NotNull Claim claim, @Nullable Location selectedCorner, int heldSlot, long now)
        {
            this.claim = claim;
            this.shaped = claim.isShaped();

            Location lesser = claim.getLesserBoundaryCorner();
            Location greater = claim.getGreaterBoundaryCorner();
            this.originalBounds = new int[] {
                    Math.min(lesser.getBlockX(), greater.getBlockX()),
                    Math.min(lesser.getBlockY(), greater.getBlockY()),
                    Math.min(lesser.getBlockZ(), greater.getBlockZ()),
                    Math.max(lesser.getBlockX(), greater.getBlockX()),
                    Math.max(lesser.getBlockY(), greater.getBlockY()),
                    Math.max(lesser.getBlockZ(), greater.getBlockZ())
            };
            this.bounds = this.originalBounds.clone();

            if (this.shaped)
            {
                this.originalPolygon = claim.getBoundaryPolygon();
                this.polygon = this.originalPolygon;
                OrthogonalPoint2i point = selectedCorner == null
                        ? null
                        : new OrthogonalPoint2i(selectedCorner.getBlockX(), selectedCorner.getBlockZ());
                this.corner = point != null && this.polygon.corners().contains(point) ? point : null;
                this.allowedFaces = ScrollResizeGeometry.allFaces(false);
            }
            else
            {
                this.originalPolygon = null;
                this.polygon = null;
                this.corner = null;
                this.allowedFaces = selectedCorner == null
                        ? ScrollResizeGeometry.allFaces(claim.is3D())
                        : ScrollResizeGeometry.cornerFaces(
                                this.bounds,
                                selectedCorner.getBlockX(),
                                selectedCorner.getBlockY(),
                                selectedCorner.getBlockZ(),
                                claim.is3D());
            }

            this.clientSlot = heldSlot;
            this.lastActivityMillis = now;
        }

        private boolean isCornerSelection()
        {
            if (this.shaped) return this.corner != null;
            return this.allowedFaces.size() < ScrollResizeGeometry.allFaces(this.claim.is3D()).size();
        }

        private boolean hasChanges()
        {
            if (this.shaped) return !Objects.equals(this.polygon, this.originalPolygon);
            return !Arrays.equals(this.bounds, this.originalBounds);
        }
    }
}
