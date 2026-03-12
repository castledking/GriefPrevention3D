package com.griefprevention.api;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context for a claim tool interaction.
 */
public final class ClaimToolContext
{

    private final @NotNull GriefPrevention plugin;
    private final @NotNull DataStore dataStore;
    private final @NotNull Player player;
    private final @NotNull PlayerData playerData;
    private final @NotNull PlayerInteractEvent event;
    private final @NotNull EquipmentSlot hand;
    private final @NotNull ItemStack itemInHand;
    private final @Nullable Block clickedBlock;
    private final @NotNull Material clickedBlockType;

    public ClaimToolContext(
            @NotNull GriefPrevention plugin,
            @NotNull DataStore dataStore,
            @NotNull Player player,
            @NotNull PlayerData playerData,
            @NotNull PlayerInteractEvent event,
            @NotNull EquipmentSlot hand,
            @NotNull ItemStack itemInHand,
            @Nullable Block clickedBlock,
            @NotNull Material clickedBlockType)
    {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.player = player;
        this.playerData = playerData;
        this.event = event;
        this.hand = hand;
        this.itemInHand = itemInHand;
        this.clickedBlock = clickedBlock;
        this.clickedBlockType = clickedBlockType;
    }

    public @NotNull GriefPrevention getPlugin()
    {
        return plugin;
    }

    public @NotNull DataStore getDataStore()
    {
        return dataStore;
    }

    public @NotNull Player getPlayer()
    {
        return player;
    }

    public @NotNull PlayerData getPlayerData()
    {
        return playerData;
    }

    public @NotNull PlayerInteractEvent getEvent()
    {
        return event;
    }

    public @NotNull Action getAction()
    {
        return event.getAction();
    }

    public @NotNull EquipmentSlot getHand()
    {
        return hand;
    }

    public @NotNull ItemStack getItemInHand()
    {
        return itemInHand;
    }

    public @NotNull Material getItemType()
    {
        return itemInHand.getType();
    }

    public @Nullable Block getClickedBlock()
    {
        return clickedBlock;
    }

    public @NotNull Material getClickedBlockType()
    {
        return clickedBlockType;
    }

}
