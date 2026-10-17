package me.ryanhamshire.GriefPrevention;

import com.griefprevention.compat.ContentCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Cushions (26.3+) are entities, but not living ones, so a hit on one raises no
 * {@link org.bukkit.event.entity.EntityDamageEvent} at all: {@code Cushion.hurtServer} reaches
 * {@code BlockAttachedEntity.hurtServer}, which fires Paper's break events and then kills the
 * entity outright. The {@code CUSHION} entry in
 * {@code EntityDamageHandler#handleClaimedBuildTrustDamageByEntity} therefore never runs, which is
 * why breaking a cushion in a claim went unprotected.
 * <p>
 * Every player-driven removal funnels through {@code EntityBreakByEntityEvent}, so that one event
 * covers melee, arrows, snowballs and eggs alike. {@code EntityBreakEvent} covers the rest: fire
 * and any other damage with no causing entity.
 * <p>
 * Reflected, because these events are newer than the paper-api this plugin compiles against. Like
 * {@link DiscordSRVSoftMuteBridge}, the listener is registered directly rather than through
 * {@code @EventHandler}, since a handler method cannot name a type that is absent at compile time.
 */
public final class CushionBreakHandler {

    private static final String BREAK_EVENT_CLASS = "io.papermc.paper.event.entity.EntityBreakEvent";
    private static final String BREAK_BY_ENTITY_EVENT_CLASS = "io.papermc.paper.event.entity.EntityBreakByEntityEvent";

    private final GriefPrevention instance;
    private final DataStore dataStore;

    private CushionBreakHandler(@NotNull GriefPrevention instance, @NotNull DataStore dataStore) {
        this.instance = instance;
        this.dataStore = dataStore;
    }

    /**
     * Registers the handler where the break events exist. A no-op on older servers, where no entity
     * can be a cushion anyway.
     */
    public static void registerIfAvailable(@NotNull GriefPrevention instance, @NotNull DataStore dataStore) {
        try {
            Class<?> breakClass = Class.forName(BREAK_EVENT_CLASS, false, CushionBreakHandler.class.getClassLoader());
            Class<?> breakByEntityClass = Class.forName(BREAK_BY_ENTITY_EVENT_CLASS, false, CushionBreakHandler.class.getClassLoader());

            if (!Event.class.isAssignableFrom(breakClass)) {
                GriefPrevention.AddLogEntry("Paper entity break event is not a Bukkit event on this version; " +
                        "cushions will not be protected against breakage");
                return;
            }

            Method getEntity = breakClass.getMethod("getEntity");
            Method setCancelled = breakClass.getMethod("setCancelled", boolean.class);
            Method getDamageSource = breakByEntityClass.getMethod("getDamageSource");

            CushionBreakHandler handler = new CushionBreakHandler(instance, dataStore);
            Listener listener = new Listener() {
            };
            EventExecutor executor = (ignored, event) -> handler.handle(
                    breakClass, breakByEntityClass, getEntity, setCancelled, getDamageSource, event);

            // Registered on the parent class, which covers the by-entity subclass too.
            Bukkit.getPluginManager().registerEvent(
                    breakClass.asSubclass(Event.class),
                    listener,
                    EventPriority.LOWEST,
                    executor,
                    instance,
                    true);
        } catch (ClassNotFoundException | LinkageError | NoSuchMethodException e) {
            // Pre-26.3 servers, where nothing can be a cushion.
        }
    }

    private void handle(
            Class<?> breakClass,
            Class<?> breakByEntityClass,
            Method getEntity,
            Method setCancelled,
            Method getDamageSource,
            Event event) throws EventException {
        if (!breakClass.isInstance(event)) {
            return;
        }

        try {
            Object entityObject = getEntity.invoke(event);
            if (!(entityObject instanceof Entity) || !ContentCompat.isCushionEntity((Entity) entityObject)) {
                return;
            }

            Entity cushion = (Entity) entityObject;

            if (!this.instance.claimsEnabledForWorld(cushion.getWorld())) {
                return;
            }

            if (breakByEntityClass.isInstance(event)) {
                this.denyPlayerBreak(cushion, getDamageSource.invoke(event), setCancelled, event);
            } else {
                // Nothing but the environment did this, so no player can hold build trust here.
                // Fire reaching a cushion in a claim is refused, as it is for frames and stands.
                if (this.dataStore.getClaimAt(cushion.getLocation(), false, null) != null) {
                    setCancelled.invoke(event, true);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new EventException(e);
        }
    }

    private void denyPlayerBreak(
            Entity cushion,
            Object damageSource,
            Method setCancelled,
            Event event) throws ReflectiveOperationException {
        Player player = causingPlayer(damageSource);

        // A dispenser or other non-player source has no player trust behind it, so it cannot be
        // given build permission in the claim.
        if (player == null) {
            setCancelled.invoke(event, true);
            return;
        }

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.isIgnoringClaims()) {
            return;
        }

        Claim claim = this.dataStore.getClaimAt(cushion.getLocation(), false, playerData.lastClaim);
        if (claim == null) {
            return;
        }

        playerData.lastClaim = claim;

        // A cushion is placed and broken like a decoration, so breaking one takes build trust.
        Supplier<String> denial = claim.checkPermission(player, ClaimPermission.Build, event);
        if (denial == null) {
            return;
        }

        setCancelled.invoke(event, true);
        GriefPrevention.sendRateLimitedErrorMessage(player, denial.get());
    }

    /**
     * The player behind a damage source, if any.
     * <p>
     * The event's own {@code getRemover()} is the damage source's <em>direct</em> entity, which for
     * a snowball is the snowball rather than the player who threw it, so the causing entity is the
     * one to ask about trust.
     */
    private @Nullable Player causingPlayer(Object damageSource) {
        if (damageSource == null) {
            return null;
        }

        try {
            Method getCausingEntity = damageSource.getClass().getMethod("getCausingEntity");
            Object causing = getCausingEntity.invoke(damageSource);
            return causing instanceof Player ? (Player) causing : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Fabric does not use EntityBreakEvent for snowballs and eggs, so we need to register
     * for Fabric's UseEntityCallback to catch those projectiles.
     * The real Fabric solution is in FabricWorldProtection.mayDamage().
     * This method is commented out to avoid compilation issues with net.minecraft types.
     */
    // private void onFabricProjectileHit(@NotNull net.minecraft.world.entity.Entity shooter,
    //                                   @NotNull net.minecraft.world.entity.Entity target,
    //                                   @Nullable net.minecraft.world.entity.Entity direct) {
    //     // This is the Fabric equivalent of checking ProjectileHitEvent for projectiles
    //     // The actual implementation is in FabricWorldProtection.mayDamage
    //     // and uses the mayDamage() method which calls isBuildProtected() for cushions
    // }

}
    // End of CushionBreakHandler class