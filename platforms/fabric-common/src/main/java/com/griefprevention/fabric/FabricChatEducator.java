package com.griefprevention.fabric;

import com.griefprevention.messages.MessageKey;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Paper-style chat educator: points players at /trapped and claim-help videos when they ask in chat.
 */
final class FabricChatEducator
{
    private final FabricMessages messages;
    private final FabricSettings settings;
    private final FabricClaimRepository claims;
    private final Logger logger;

    private volatile @NotNull String cachedHowToClaimSource = "";
    private volatile @NotNull Pattern cachedHowToClaim = Pattern.compile("$^");

    FabricChatEducator(
            @NotNull FabricMessages messages,
            @NotNull FabricSettings settings,
            @NotNull FabricClaimRepository claims,
            @NotNull Logger logger)
    {
        this.messages = messages;
        this.settings = settings;
        this.claims = claims;
        this.logger = logger;
    }

    void register()
    {
        ModPlatform.current().onChatMessage(this::onChat);
    }

    void onChat(@NotNull ServerPlayer player, @NotNull String rawMessage)
    {
        String message = rawMessage == null ? "" : rawMessage;
        if (message.isEmpty())
        {
            return;
        }

        educateHowToClaim(player, message);
        educateTrapped(player, message);
    }

    private void educateHowToClaim(@NotNull ServerPlayer player, @NotNull String message)
    {
        Pattern pattern = howToClaimPattern();
        if (!pattern.matcher(message).matches())
        {
            return;
        }
        ServerLevel level = FabricVersionCompat.level(player);
        String worldKey = this.claims.worldKey(level);
        boolean creative = this.settings.world().creativeRulesApply(worldKey);
        if (creative)
        {
            String url = this.messages.format(MessageKey.CREATIVE_VIDEO_URL);
            String text = this.messages.format(MessageKey.CREATIVE_BASICS_VIDEO_2, url);
            sendInfo(player, text);
        }
        else
        {
            String url = this.messages.format(MessageKey.SURVIVAL_VIDEO_URL);
            String text = this.messages.format(MessageKey.SURVIVAL_BASICS_VIDEO_2, url);
            sendInfo(player, text);
        }
    }

    private void educateTrapped(@NotNull ServerPlayer player, @NotNull String message)
    {
        if (message.toLowerCase(Locale.ROOT).contains("/trapped"))
        {
            return;
        }
        String keywords = this.messages.format(MessageKey.TRAPPED_CHAT_KEYWORD);
        if (keywords == null || keywords.isEmpty())
        {
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String keyword : keywords.split(";"))
        {
            String trimmed = keyword.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty())
            {
                continue;
            }
            if (lower.contains(trimmed))
            {
                sendInfo(player, this.messages.format(MessageKey.TRAPPED_INSTRUCTIONS));
                return;
            }
        }
    }

    private void sendInfo(@NotNull ServerPlayer player, @NotNull String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        player.sendSystemMessage(FabricLegacyComponents.toComponent(text, ChatFormatting.AQUA));
    }

    private @NotNull Pattern howToClaimPattern()
    {
        String source = this.messages.format(MessageKey.HOW_TO_CLAIM_REGEX);
        if (source.equals(this.cachedHowToClaimSource))
        {
            return this.cachedHowToClaim;
        }
        Pattern compiled;
        try
        {
            compiled = Pattern.compile(source, Pattern.CASE_INSENSITIVE);
        }
        catch (PatternSyntaxException exception)
        {
            this.logger.warn("Invalid HowToClaimRegex '{}'; chat educator regex disabled until fixed.", source, exception);
            compiled = Pattern.compile("$^");
        }
        this.cachedHowToClaimSource = source;
        this.cachedHowToClaim = compiled;
        return compiled;
    }
}
