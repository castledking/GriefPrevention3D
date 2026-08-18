package com.griefprevention.fabric;

import com.griefprevention.messages.LegacyText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricLegacyComponentsTest
{
    @Test
    void uncoloredTextInheritsTheDefaultColor()
    {
        Component component = FabricLegacyComponents.toComponent("denied", ChatFormatting.RED);

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), component.getStyle().getColor());
        assertEquals(1, component.getSiblings().size());
        assertNull(component.getSiblings().get(0).getStyle().getColor());
        assertEquals("denied", component.getString());
    }

    @Test
    void appliesLegacyColorsAndStyles()
    {
        // The style code must follow the color: a color code resets the style, as in vanilla.
        Component component = FabricLegacyComponents.toComponent(
                LegacyText.translate("&6warn&7&lbold"),
                ChatFormatting.RED
        );

        List<Component> siblings = component.getSiblings();
        assertEquals(2, siblings.size());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), siblings.get(0).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), siblings.get(1).getStyle().getColor());
        assertTrue(siblings.get(1).getStyle().isBold());
        assertEquals("warnbold", component.getString());
    }

    @Test
    void appliesHexColors()
    {
        Component component = FabricLegacyComponents.toComponent(
                LegacyText.translate("&#A1B2C3fancy"),
                ChatFormatting.RED
        );

        assertEquals(
                TextColor.fromRgb(0xA1B2C3),
                component.getSiblings().get(0).getStyle().getColor()
        );
    }

    @Test
    void emptyMessageProducesNoSiblings()
    {
        assertTrue(FabricLegacyComponents.toComponent("", ChatFormatting.RED).getSiblings().isEmpty());
    }
}
