package me.ryanhamshire.GriefPrevention;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TutorialLinksTest
{
    @AfterEach void cleanup() { GriefPrevention.instance = null; }

    @Test
    void playerLocaleControlsTutorialUrlsInChatAndTheWelcomeBook() throws Exception
    {
        GriefPrevention plugin = mock(GriefPrevention.class);
        GriefPrevention.instance = plugin;
        plugin.config_locale = "en";
        plugin.config_perPlayerLocale = true;
        DataStore store = mock(DataStore.class, CALLS_REAL_METHODS);
        plugin.dataStore = store;
        PlayerData data = new PlayerData();
        data.locale = "fr_fr";
        doReturn(data).when(store).getPlayerData(any(UUID.class));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("92a4a211-5ccf-477e-a3c4-0c110a03e48f"));

        Map<String, String[]> locales = new HashMap<>();
        locales.put("en", messages("https://example.test/english"));
        locales.put("fr_FR", messages("&bhttps://example.test/french"));
        Field field = DataStore.class.getDeclaredField("messagesByLocale");
        field.setAccessible(true);
        field.set(store, locales);

        String url = store.getMessage(player, Messages.SurvivalVideoURL);
        assertEquals("\u00a7bhttps://example.test/french", url);
        assertEquals("Click for Land Claim Help: " + url, store.getMessage(player, Messages.SurvivalBasicsVideo2, url));
        assertEquals("Click: " + url, store.getMessage(player, Messages.BookLink, url));
        assertEquals("https://example.test/english", store.getMessage(Messages.SurvivalVideoURL));

        // Reloading replaces the locale arrays, so all existing call sites pick up the edit.
        locales.put("fr_FR", messages("https://example.test/updated"));
        assertEquals("https://example.test/updated", store.getMessage(player, Messages.SurvivalVideoURL));
        plugin.config_perPlayerLocale = false;
        assertEquals("https://example.test/english", store.getMessage(player, Messages.SurvivalVideoURL));
    }

    private static String[] messages(String url) throws Exception
    {
        MessageFile file = MessageFile.load(new ByteArrayInputStream(("Messages:\n  SurvivalVideoURL: '" + url + "'\n")
                .getBytes(StandardCharsets.UTF_8)));
        String[] result = new String[Messages.values().length];
        Method populate = MessageLocalization.class.getDeclaredMethod("populateMessagesArray", String[].class, Messages[].class, MessageFile.class);
        populate.setAccessible(true);
        populate.invoke(null, result, Messages.values(), file);
        return result;
    }
}
