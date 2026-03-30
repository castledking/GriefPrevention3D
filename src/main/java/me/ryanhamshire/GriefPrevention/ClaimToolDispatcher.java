/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

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

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Owns claim tool interaction dispatch so the general player listener can ignore
 * cancelled interaction events. The actual tool behavior still lives in
 * {@link PlayerEventHandler} for now while the recode branch peels logic away
 * from that class incrementally.
 */
final class ClaimToolDispatcher implements Listener
{
    private final @NotNull GriefPrevention instance;
    private final @NotNull PlayerEventHandler playerEventHandler;

    ClaimToolDispatcher(@NotNull GriefPrevention instance, @NotNull PlayerEventHandler playerEventHandler)
    {
        this.instance = instance;
        this.playerEventHandler = playerEventHandler;
    }

    @EventHandler(priority = EventPriority.LOW)
    void onPlayerInteract(PlayerInteractEvent event)
    {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)
        {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND)
        {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = instance.getItemInHand(player, hand);
        Material materialInHand = itemInHand.getType();
        if (materialInHand != instance.config_claims_investigationTool
                && materialInHand != instance.config_claims_modificationTool)
        {
            return;
        }

        // Claim tools should not continue into the general interaction listener.
        event.setCancelled(true);
        this.playerEventHandler.onPlayerInteract(event);
    }
}
