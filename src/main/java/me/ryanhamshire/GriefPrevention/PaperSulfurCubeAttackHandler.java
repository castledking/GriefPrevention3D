package me.ryanhamshire.GriefPrevention;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Paper fires {@link PrePlayerAttackEntityEvent} before a melee hit lands. Stopping a hit on a claimed
 * sulfur cube there covers what no damage event sees: a hit that knocks a block-holding cube away, and
 * fire aspect lighting a TNT cube's fuse. Registered only where the event exists.
 */
public class PaperSulfurCubeAttackHandler implements Listener
{
    private final GriefPrevention instance;
    private final DataStore dataStore;

    public PaperSulfurCubeAttackHandler(@NotNull GriefPrevention instance, @NotNull DataStore dataStore)
    {
        this.instance = instance;
        this.dataStore = dataStore;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrePlayerAttack(@NotNull PrePlayerAttackEntityEvent event)
    {
        Entity cube = event.getAttacked();
        if (!CompatUtil.isEntityType(cube, "SULFUR_CUBE"))
            return;

        Player player = event.getPlayer();
        Supplier<String> denial = SulfurCubeHandler.denyHit(this.instance, this.dataStore, player, cube, event);
        if (denial == null)
            return;

        event.setCancelled(true);
        GriefPrevention.sendRateLimitedErrorMessage(player, denial.get());
    }
}
