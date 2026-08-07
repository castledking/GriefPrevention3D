package com.griefprevention.platform.knockback;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

/**
 * Paper implementation of knockback protection handling.
 * Uses Paper's {@link EntityKnockbackByEntityEvent} and {@link EntityPushedByEntityAttackEvent}.
 * <p>
 * Handles all player-caused knockback including melee attacks (spears),
 * projectiles (wind charges), mace smash AoE, and other mechanisms (shield blocks).
 * <p>
 * Paper resolves projectiles to their shooter, so {@code getHitBy()} returns
 * the player directly for both direct attacks and projectile-caused knockback.
 * <p>
 * This event is preferred over Bukkit's version on Paper servers because it fires
 * first and is not deprecated on Paper.
 * <p>
 * <b>Known limitation:</b> Wind Burst enchantment knockback cannot be blocked due to
 * a Paper bug where cancelling {@link EntityPushedByEntityAttackEvent} does not prevent
 * the knockback. See <a href="https://github.com/PaperMC/Paper/issues/13079">Paper #13079</a>.
 */
public class PaperKnockbackProtectionHandler extends KnockbackProtectionHandler
{

    public PaperKnockbackProtectionHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        super(dataStore, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityKnockbackByEntity(@NotNull EntityKnockbackByEntityEvent event)
    {
        debug("EntityKnockbackByEntityEvent cause=" + event.getCause()
                + " entity=" + describe(event.getEntity())
                + " hitBy=" + describe(event.getHitBy()));

        if (!(event.getHitBy() instanceof Player))
        {
            debug("ignored: hitBy is not a player");
            return;
        }
        Player attacker = (Player) event.getHitBy();

        if (event.getEntity() instanceof Player)
        {
            handleKnockbackPlayer(event, attacker, (Player) event.getEntity());
        }
        else
        {
            handleKnockbackEntity(event, attacker, event.getEntity());
        }
    }

    /**
     * Handle push events from AoE attacks like mace smash.
     * This is Paper-specific and handles knockback that doesn't go through
     * the normal {@link EntityKnockbackByEntityEvent}.
     * <p>
     * Note: Wind Burst enchantment knockback bypasses this due to Paper bug #13079.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityPushedByEntityAttack(@NotNull EntityPushedByEntityAttackEvent event)
    {
        // EntityKnockbackByEntityEvent is a subclass of this event and shares its handler list,
        // so both listeners fire for it. Let the more specific handler above own those.
        if (event instanceof EntityKnockbackByEntityEvent) return;

        Entity pusher = event.getPushedBy();

        debug("EntityPushedByEntityAttackEvent cause=" + event.getCause()
                + " entity=" + describe(event.getEntity())
                + " pushedBy=" + describe(pusher));

        Player attacker;
        if (pusher instanceof Player)
        {
            attacker = (Player) pusher;
        }
        else if (pusher instanceof Projectile && ((Projectile) pusher).getShooter() instanceof Player)
        {
            attacker = (Player) ((Projectile) pusher).getShooter();
        }
        else
        {
            debug("ignored: pusher is not a player or player-shot projectile");
            return;
        }

        if (event.getEntity() instanceof Player)
        {
            handleKnockbackPlayer(event, attacker, (Player) event.getEntity());
        }
        else
        {
            handleKnockbackEntity(event, attacker, event.getEntity());
        }
    }

    /**
     * Diagnostic-only trace of every knockback event affecting a player, including the base
     * {@link EntityKnockbackEvent} that GriefPrevention does not otherwise subscribe to —
     * explosion knockback with no source entity arrives this way and is invisible to the two
     * handlers above.
     * <p>
     * Logged at {@link EventPriority#LOWEST} on arrival and again at {@link EventPriority#MONITOR}
     * after every other plugin has run, so a knockback that is cancelled or zeroed in between is
     * visible along with whoever did it. This never modifies the event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void traceKnockbackArriving(@NotNull EntityKnockbackEvent event)
    {
        traceKnockback("in ", event);
    }

    /**
     * @see #traceKnockbackArriving(EntityKnockbackEvent)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void traceKnockbackFinal(@NotNull EntityKnockbackEvent event)
    {
        traceKnockback("out", event);
    }

    private void traceKnockback(@NotNull String phase, @NotNull EntityKnockbackEvent event)
    {
        // Players only - mob knockback would drown the log.
        if (!(event.getEntity() instanceof Player)) return;

        // Skip the claim lookup entirely when debug logging is off.
        if (!instance.config_logs_debugEnabled) return;

        Player player = (Player) event.getEntity();

        // The claim at the knocked player's own location - this is what decides whether a
        // Wind Burst self-launch happens "in a claim", and it is NOT the same claim the
        // allow/cancel lines above report (those describe the defender's claim).
        Claim claim = this.dataStore.getClaimAt(player.getLocation(), false, null);

        debug("[" + phase + "] " + event.getEventName()
                + " cause=" + event.getCause()
                + " entity=" + describe(event.getEntity())
                + " standingIn=" + (claim == null ? "wilderness" : "claim " + claim.getID())
                + " cancelled=" + event.isCancelled()
                + " knockback=" + event.getKnockback());
    }

}
