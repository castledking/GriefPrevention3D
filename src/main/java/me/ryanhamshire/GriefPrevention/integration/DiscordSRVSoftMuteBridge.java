package me.ryanhamshire.GriefPrevention.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import java.lang.reflect.Method;

public final class DiscordSRVSoftMuteBridge
{
    private static final String DISCORDSRV_PLUGIN_NAME = "DiscordSRV";
    private static final String PRE_PROCESS_EVENT_CLASS = "github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent";

    private final GriefPrevention plugin;
    private final DataStore dataStore;

    public DiscordSRVSoftMuteBridge(GriefPrevention plugin, DataStore dataStore)
    {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public void registerIfAvailable()
    {
        Plugin discordSrv = Bukkit.getPluginManager().getPlugin(DISCORDSRV_PLUGIN_NAME);
        if (discordSrv == null || !discordSrv.isEnabled())
        {
            return;
        }

        try
        {
            ClassLoader discordSrvClassLoader = discordSrv.getClass().getClassLoader();
            Class<?> eventClass = Class.forName(PRE_PROCESS_EVENT_CLASS, true, discordSrvClassLoader);
            if (!Event.class.isAssignableFrom(eventClass))
            {
                plugin.getLogger().warning("DiscordSRV pre-process event is not a Bukkit event on this version; skipping soft-mute bridge");
                return;
            }
            Method getPlayer = eventClass.getMethod("getPlayer");
            Method setCancelled = eventClass.getMethod("setCancelled", boolean.class);

            Listener listener = new Listener() {};
            EventExecutor executor = (ignored, event) -> handleDiscordSrvEvent(eventClass, getPlayer, setCancelled, event);

            Bukkit.getPluginManager().registerEvent(
                eventClass.asSubclass(Event.class),
                listener,
                EventPriority.LOWEST,
                executor,
                plugin,
                false
            );

            plugin.getLogger().info("- Registered DiscordSRV soft-mute bridge");
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException e)
        {
            plugin.getLogger().warning("Failed to register DiscordSRV soft-mute bridge: " + e.getMessage());
        }
    }

    private void handleDiscordSrvEvent(Class<?> eventClass, Method getPlayer, Method setCancelled, Event event)
        throws EventException
    {
        if (!eventClass.isInstance(event))
        {
            return;
        }

        try
        {
            Object playerObject = getPlayer.invoke(event);
            if (!(playerObject instanceof Player))
            {
                return;
            }

            Player player = (Player) playerObject;
            if (dataStore.isSoftMuted(player.getUniqueId()))
            {
                setCancelled.invoke(event, true);
            }
        }
        catch (ReflectiveOperationException e)
        {
            throw new EventException(e);
        }
    }
}
