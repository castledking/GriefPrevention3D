package com.griefprevention.platform.knockback;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.EntityDamageHandler;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Abstract base class for player-caused knockback protection in claims.
 * Contains shared logic for both Paper and Spigot implementations.
 * <p>
 * Handles knockback from all player sources including melee attacks (spears),
 * projectiles (wind charges), and other mechanisms (shield blocks).
 * <p>
 * Subclasses provide the event listener method that extracts attacker/defender
 * from platform-specific events, then delegate to the protected handler methods.
 */
public abstract class KnockbackProtectionHandler implements Listener
{

    protected final DataStore dataStore;
    protected final GriefPrevention instance;

    protected KnockbackProtectionHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        this.instance = plugin;
    }

    /**
     * Logs a knockback trace line. Only written when debug logging is enabled
     * ({@code /gpdebug on}), so this is safe to leave in place on production servers.
     *
     * @param message the trace message
     */
    protected static void debug(@NotNull String message)
    {
        GriefPrevention.AddLogEntry("[GP Debug] [knockback] " + message);
    }

    /**
     * Renders an entity for debug output: player name, or entity type for anything else.
     *
     * @param entity the entity, may be null
     * @return a short human-readable description
     */
    protected static @NotNull String describe(Entity entity)
    {
        if (entity == null) return "null";
        if (entity instanceof Player) return "Player(" + entity.getName() + ")";
        return entity.getType().name();
    }

    /**
     * Handle player-caused knockback against other players. Applies GP PvP protections
     * (safe zones, fresh spawn immunity) when GP PvP rules are enabled for the world.
     *
     * @param event the knockback event
     * @param attacker the {@link Player} who caused the knockback
     * @param defender the {@link Player} being knocked back
     * @param <T> event type that extends Event and implements Cancellable
     */
    @SuppressWarnings("null")
    protected <T extends Event & Cancellable> void handleKnockbackPlayer(
            @NotNull T event,
            @NotNull Player attacker,
            @NotNull Player defender)
    {
        // Always allow self-knockback for mobility.
        if (attacker.equals(defender))
        {
            debug("allow: self-knockback (" + describe(defender) + ")");
            return;
        }

        PlayerData defenderData = this.dataStore.getPlayerData(defender.getUniqueId());
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());

        if (attackerData.ignoreClaims)
        {
            debug("allow: attacker " + attacker.getName() + " has ignoreClaims on");
            return;
        }

        // Check if defender is in a claim where attacker has trust.
        Claim defenderClaim = this.dataStore.getClaimAt(defender.getLocation(), false, defenderData.lastClaim);
        if (defenderClaim != null)
        {
            defenderData.lastClaim = defenderClaim;

            // When combat trust is enabled, only an explicit PvP grant allows fighting players
            // here - Access trust no longer covers combat. Otherwise legacy rules apply and
            // Access trust allows the knockback.
            boolean allowed;
            if (this.instance.config_claims_allowPvPTrust)
            {
                allowed = defenderClaim.hasExplicitPermission(attacker, ClaimPermission.PVP);
                debug(allowed
                        ? "allow: attacker " + attacker.getName() + " has PvP trust in defender's claim "
                                + defenderClaim.getID()
                        : "denied: attacker " + attacker.getName() + " lacks PvP trust in defender's claim "
                                + defenderClaim.getID() + " (falling through to PvP rules)");
            }
            else
            {
                // If the attacker has access trust, allow the knockback.
                allowed = defenderClaim.checkPermission(attacker, ClaimPermission.Access, null) == null;
                if (allowed)
                {
                    debug("allow: attacker " + attacker.getName() + " has Access trust in defender's claim "
                            + defenderClaim.getID());
                }
            }

            if (allowed) return;
        }

        // If GP PvP rules don't apply, GriefPrevention doesn't manage PvP in this world.
        // Allow knockback without further GP PvP checks (safe zones, fresh spawn, etc.).
        if (!instance.pvpRulesApply(defender.getWorld()))
        {
            debug("allow: GP PvP rules are disabled in world " + defender.getWorld().getName());
            return;
        }

        // Protect fresh spawns from knockback abuse.
        if (instance.config_pvp_protectFreshSpawns)
        {
            if (attackerData.pvpImmune || defenderData.pvpImmune)
            {
                debug("CANCEL: fresh-spawn PvP immunity (attackerImmune=" + attackerData.pvpImmune
                        + ", defenderImmune=" + defenderData.pvpImmune + ")");
                event.setCancelled(true);
                GriefPrevention.sendMessage(
                        attacker,
                        TextMode.Err,
                        attackerData.pvpImmune ? Messages.CantFightWhileImmune : Messages.ThatPlayerPvPImmune);
                return;
            }
        }

        // Check if defender is in a PVP safezone.
        if (defenderClaim != null && instance.claimIsPvPSafeZone(defenderClaim))
        {
            PreventPvPEvent pvpEvent = new PreventPvPEvent(defenderClaim, attacker, defender);
            Bukkit.getPluginManager().callEvent(pvpEvent);
            if (!pvpEvent.isCancelled())
            {
                debug("CANCEL: defender " + defender.getName() + " is in PvP-safe claim " + defenderClaim.getID()
                        + " (see PvP.ProtectPlayersInLandClaims in config.yml)");
                event.setCancelled(true);
                GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.PlayerInPvPSafeZone);
            }
            else
            {
                debug("allow: another plugin cancelled PreventPvPEvent for defender's claim "
                        + defenderClaim.getID());
            }
            return;
        }

        // Check if attacker is in a PVP safezone (prevent shooting from safezone).
        Claim attackerClaim = this.dataStore.getClaimAt(attacker.getLocation(), false, attackerData.lastClaim);
        if (attackerClaim != null)
        {
            attackerData.lastClaim = attackerClaim;
            if (instance.claimIsPvPSafeZone(attackerClaim))
            {
                PreventPvPEvent pvpEvent = new PreventPvPEvent(attackerClaim, attacker, defender);
                Bukkit.getPluginManager().callEvent(pvpEvent);
                if (!pvpEvent.isCancelled())
                {
                    debug("CANCEL: attacker " + attacker.getName() + " is standing in PvP-safe claim "
                            + attackerClaim.getID()
                            + " (see PvP.ProtectPlayersInLandClaims in config.yml)");
                    event.setCancelled(true);
                    GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.CantFightWhileImmune);
                }
                return;
            }
        }

        debug("allow: no GP rule matched (" + attacker.getName() + " -> " + defender.getName() + ")");
    }

    /**
     * Handle player-caused knockback against non-player entities. Prevents moving protected
     * entities out of claims.
     *
     * @param event the knockback event
     * @param attacker the {@link Player} who caused the knockback
     * @param entity the {@link Entity} being knocked back
     * @param <T> event type that extends Event and implements Cancellable
     */
    @SuppressWarnings("null")
    protected <T extends Event & Cancellable> void handleKnockbackEntity(
            @NotNull T event,
            @NotNull Player attacker,
            @NotNull Entity entity)
    {
        if (!instance.claimsEnabledForWorld(entity.getWorld())) return;

        // Determine protection type and required permission.
        ClaimPermission requiredPermission;
        if (entity instanceof ArmorStand || entity instanceof Hanging)
        {
            // These require build trust, matching handleClaimedBuildTrustDamageByEntity.
            requiredPermission = ClaimPermission.Build;
        }
        else if (entity instanceof Creature && instance.config_claims_preventTheft)
        {
            // Creatures require container trust, matching handleCreatureDamageByEntity,
            // but skip monsters - they are never protected. With PvE combat trust enabled,
            // hurting or shoving creatures requires an explicit PvE grant instead.
            if (EntityDamageHandler.isHostile(entity)) return;
            requiredPermission = instance.config_claims_allowPvETrust
                    ? ClaimPermission.PVE
                    : ClaimPermission.Container;
        }
        else
        {
            // Entity type not protected.
            return;
        }

        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());

        if (attackerData.ignoreClaims) return;

        Claim claim = this.dataStore.getClaimAt(entity.getLocation(), false, attackerData.lastClaim);

        if (claim == null) return;

        attackerData.lastClaim = claim;

        Supplier<String> noPermissionReason = claim.checkPermission(attacker, requiredPermission, event);
        if (noPermissionReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(attacker, TextMode.Err, noPermissionReason.get());
        }
    }

}