package com.griefprevention.messages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageCatalogCodecTest
{
    private final MessageCatalogCodec codec = new MessageCatalogCodec();

    @Test
    void readsPlainStringEntries() throws Exception
    {
        MessageCatalog catalog = codec.decode(
                "Messages:\n"
                        + "  NoBuildPermission: \"{0} says no building.\"\n"
        );

        assertEquals("Notch says no building.", catalog.format(MessageKey.NO_BUILD_PERMISSION, "Notch"));
    }

    @Test
    void readsLegacyTextSubKey() throws Exception
    {
        MessageCatalog catalog = codec.decode(
                "Messages:\n"
                        + "  NoAccessPermission:\n"
                        + "    Text: \"Ask {0} first.\"\n"
                        + "    Notes: \"0: owner name\"\n"
        );

        assertEquals("Ask Notch first.", catalog.format(MessageKey.NO_ACCESS_PERMISSION, "Notch"));
    }

    @Test
    void fallsBackToPaperDefaultWhenKeyIsMissing() throws Exception
    {
        MessageCatalog catalog = codec.decode("Messages:\n  BlockClaimed: \"claimed by {0}\"\n");

        assertEquals(
                "You don't have Notch's permission to build here.",
                catalog.format(MessageKey.NO_BUILD_PERMISSION, "Notch")
        );
    }

    @Test
    void keepsUnknownAddonKeys() throws Exception
    {
        MessageCatalog catalog = codec.decode("Messages:\n  SomeAddonKey: \"addon text\"\n");

        assertEquals("addon text", catalog.template("SomeAddonKey"));
    }

    @Test
    void ignoresEntriesThatAreNotText() throws Exception
    {
        MessageCatalog catalog = codec.decode(
                "Messages:\n"
                        + "  NoBuildPermission:\n"
                        + "    Notes: \"0: owner name\"\n"
        );

        assertNull(catalog.template("NoBuildPermission"));
        assertEquals(
                "You don't have Notch's permission to build here.",
                catalog.format(MessageKey.NO_BUILD_PERMISSION, "Notch")
        );
    }

    @Test
    void emptyAndRootlessDocumentsUseDefaults() throws Exception
    {
        assertEquals(MessageKey.OWNER_NAME_FOR_ADMIN_CLAIMS.defaultValue(),
                codec.decode("").format(MessageKey.OWNER_NAME_FOR_ADMIN_CLAIMS));
        assertEquals(MessageKey.OWNER_NAME_FOR_ADMIN_CLAIMS.defaultValue(),
                codec.decode("GriefPrevention:\n  Claims:\n    InitialBlocks: 100\n")
                        .format(MessageKey.OWNER_NAME_FOR_ADMIN_CLAIMS));
    }

    @Test
    void rejectsInvalidYaml()
    {
        assertThrows(MessageCatalogException.class, () -> codec.decode("Messages:\n  - not a mapping\n"));
        assertThrows(MessageCatalogException.class, () -> codec.decode("Messages: \"not a mapping\"\n"));
    }

    @Test
    void leavesPlaceholdersWithoutArgumentsAlone() throws Exception
    {
        MessageCatalog catalog = codec.decode("Messages:\n  NoBuildPermission: \"{0} and {1}\"\n");

        assertEquals("Notch and {1}", catalog.format(MessageKey.NO_BUILD_PERMISSION, "Notch"));
    }
}
