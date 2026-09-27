package com.griefprevention.fabric;

import com.griefprevention.messages.LegacySegment;
import com.griefprevention.messages.LegacyText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Renders Paper's legacy-coded message text as a modern chat component. */
final class FabricLegacyComponents
{
    private FabricLegacyComponents()
    {
    }

    /**
     * @param legacy message text that may contain {@code §} color and style codes
     * @param defaultColor the color applied to any run the message does not color itself
     */
    static @NotNull Component toComponent(@NotNull String legacy, @NotNull ChatFormatting defaultColor)
    {
        MutableComponent root = Component.empty().withStyle(Style.EMPTY.withColor(defaultColor));
        List<LegacySegment> segments = LegacyText.segments(legacy);
        for (LegacySegment segment : segments)
        {
            root.append(Component.literal(segment.text()).withStyle(styleOf(segment)));
        }
        return root;
    }

    private static @NotNull Style styleOf(@NotNull LegacySegment segment)
    {
        Style style = Style.EMPTY
                .withBold(segment.bold())
                .withItalic(segment.italic())
                .withUnderlined(segment.underlined())
                .withStrikethrough(segment.strikethrough())
                .withObfuscated(segment.obfuscated());

        String color = segment.color();
        if (color == null)
        {
            // No color of its own, so the root's default color shows through.
            return style;
        }
        if (color.startsWith("#"))
        {
            return style.withColor(Integer.parseInt(color.substring(1), 16));
        }

        ChatFormatting formatting = ChatFormatting.getByCode(color.charAt(0));
        return formatting == null ? style : style.withColor(formatting);
    }
}
