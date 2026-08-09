/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.events.AccrueClaimBlocksEvent;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.entity.Player;

import java.util.Collection;

//FEATURE: give players claim blocks for playing, as long as they're not away from their computer

//runs every 5 minutes in the main thread, grants blocks per hour / 12 to each online player who appears to be actively playing
class DeliverClaimBlocksTask implements Runnable
{
    private final Player player;
    private final GriefPrevention instance;
    private final int idleThresholdSquared;

    public DeliverClaimBlocksTask(Player player, GriefPrevention instance)
    {
        this.player = player;
        this.instance = instance;
        this.idleThresholdSquared = instance.config_claims_accruedIdleThreshold
                * instance.config_claims_accruedIdleThreshold;
    }

    @Override
    public void run()
    {
        //if no player specified, this task will create a player-specific task for each online player, scheduled one tick apart
        if (this.player == null)
        {
            @SuppressWarnings("unchecked")
            Collection<Player> players = (Collection<Player>) GriefPrevention.instance.getServer().getOnlinePlayers();

            long i = 0;
            for (Player onlinePlayer : players)
            {
                DeliverClaimBlocksTask newTask = new DeliverClaimBlocksTask(onlinePlayer, instance);
                SchedulerUtil.runLaterEntity(instance, onlinePlayer, newTask::run, i++);
            }

            return; //tasks started for each player
        }

        //deliver claim blocks to the specified player
        if (!this.player.isOnline())
        {
            return; //player is not online to receive claim blocks
        }

        DataStore dataStore = instance.dataStore;
        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());

        // Legacy idle detection deliberately treats vehicles and liquid movement as idle,
        // and otherwise requires the configured amount of player movement.
        boolean detectedIdle = false;
        try
        {
            detectedIdle = player.isInsideVehicle()
                    || player.getLocation().getBlock().isLiquid()
                    || !(playerData.lastAfkCheckLocation == null
                    || playerData.lastAfkCheckLocation.distanceSquared(player.getLocation()) > idleThresholdSquared);
        }
        catch (IllegalArgumentException ignored)
        {
            // Locations in different worlds cannot be compared; treat the player as active.
        }
        boolean isIdle = detectedIdle && !player.hasPermission("griefprevention.accruals.afkbypass");

        //remember current location for next time
        playerData.lastAfkCheckLocation = player.getLocation();

        try
        {
            //determine how fast blocks accrue for this player; can be modified by addons
            if (!player.hasPermission("griefprevention.accruals"))
            {
                GriefPrevention.AddLogEntry(player.getName() + " does not have permission to accrue claim blocks.",
                        CustomLogEntryTypes.Debug, true);
                return;
            }

            int accrualRate = instance.config_claims_blocksAccruedPerHour_default;
            if (isIdle)
            {
                if (instance.config_claims_accruedIdlePercent <= 0)
                {
                    GriefPrevention.AddLogEntry(player.getName() + " wasn't active enough to accrue claim blocks this round.", CustomLogEntryTypes.Debug, true);
                    return;
                }
                accrualRate = (int) (accrualRate * (instance.config_claims_accruedIdlePercent / 100.0D));
                // AccrueClaimBlocksEvent defaults idle events to cancelled in newer GP.
                // The legacy percentage is an explicit core opt-in, so allow the event by default.
            }

            //fire event for addons
            AccrueClaimBlocksEvent event = new AccrueClaimBlocksEvent(player, accrualRate, isIdle);
            if (isIdle && instance.config_claims_accruedIdlePercent > 0)
                event.setCancelled(false);
            instance.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled())
            {
                //event is initialized as canceled if player is idle
                if (event.isIdle())
                    GriefPrevention.AddLogEntry(player.getName() + " wasn't active enough to accrue claim blocks this round.", CustomLogEntryTypes.Debug, true);
                else
                    GriefPrevention.AddLogEntry(player.getName() + " claim block delivery was canceled by another plugin.", CustomLogEntryTypes.Debug, true);
                return; //event was cancelled
            }

            //set actual accrual
            accrualRate = event.getBlocksToAccrue();
            if (accrualRate < 0) accrualRate = 0;
            playerData.accrueBlocks(accrualRate);
            GriefPrevention.AddLogEntry("Delivering " + event.getBlocksToAccrue() + " blocks to " + player.getName(), CustomLogEntryTypes.Debug, true);

            //intentionally NOT saving data here to reduce overall secondary storage access frequency
            //many other operations will cause this player's data to save, including his eventual logout
            //dataStore.savePlayerData(player.getUniqueIdentifier(), playerData);
        }
        catch (Exception e)
        {
            GriefPrevention.AddLogEntry("Problem delivering claim blocks to player " + player.getName() + ":");
            e.printStackTrace();
        }
    }
}
