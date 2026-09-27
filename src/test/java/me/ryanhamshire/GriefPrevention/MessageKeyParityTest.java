package me.ryanhamshire.GriefPrevention;

import com.griefprevention.messages.MessageKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fabric reads the same messages.yml as Paper, so each key it knows must name a Paper message and
 * fall back to the same text when the file lacks it.
 */
class MessageKeyParityTest
{
    @Test
    void everyFabricMessageKeyMatchesItsPaperDefault()
    {
        List<String> differences = new ArrayList<>();
        for (MessageKey key : MessageKey.values())
        {
            Messages paper = Messages.valueOf(key.key());
            if (!paper.defaultValue.equals(key.defaultValue()))
            {
                differences.add(key.key() + ": Paper \"" + paper.defaultValue + "\", Fabric \"" + key.defaultValue() + "\"");
            }
        }
        assertEquals(new ArrayList<String>(), differences);
    }
}
