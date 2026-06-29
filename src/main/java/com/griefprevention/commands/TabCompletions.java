package com.griefprevention.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Collections;

/**
 * A container for tab completion helper methods.
 */
public final class TabCompletions
{

    public static @NotNull List<String> integer(
            @NotNull String[] args,
            @Range(from = 1, to = Integer.MAX_VALUE - 1) int maxDigits,
            boolean allowBelowOne)
    {
        String prefix = asPrefix(args);

        // If completing nothing, return all eligible values.
        if (prefix.isEmpty())
        {
            List<String> completions = new ArrayList<>(allowBelowOne ? 19 : 10);
            for (int i = allowBelowOne ? -9 : 1; i <= 9; ++i)
            {
                completions.add(Integer.toString(i));
            }
            return completions;
        }

        char[] prefixChars = prefix.toCharArray();
        // If we allow negatives, ignore the appropriate prefix character.
        int startIndex = allowBelowOne && prefixChars[0] == '-' ? 1 : 0;

        // Ensure that all characters are digits.
        for (int index = startIndex; index < prefixChars.length; ++index)
        {
            char prefixChar = prefixChars[index];
            if (prefixChar < '0' || prefixChar > '9')
            {
                return Collections.emptyList();
            }
        }

        int digitsLength = prefixChars.length - startIndex;
        List<String> completions = new ArrayList<>(11);

        // If the existing value has digits, add it.
        if (digitsLength > 0)
        {
            completions.add(prefix);
        }

        // If the input already has the max number of digits, don't suggest more.
        if (digitsLength >= maxDigits)
        {
            return completions;
        }

        // Prefix is acceptable, offer all digits prefixed by existing content.
        for (int i = 0; i <= 9; ++i)
        {
            completions.add(prefix + i);
        }
        return completions;
    }

    /**
     * Offer completions for visible players' names.
     *
     * @param sender the sender
     * @param args the existing command arguments
     * @return the matching players' names
     */
    public static @NotNull List<String> visiblePlayers(@Nullable CommandSender sender, @NotNull String[] args)
    {
        // When Allium + PartyManager is present, use its tab completion list
        // (which excludes genuinely vanished players but includes distance-hidden ones).
        if (sender instanceof Player) {
            List<String> alliumCompletions = queryAlliumCompletions((Player) sender, args);
            if (alliumCompletions != null) {
                return alliumCompletions;
            }
        }

        // Bukkit returns a view of the player list. So that Craftbukkit doesn't have to hack around type limitations,
        // this is actually a view of the player implementation, represented via Bukkit as a generic extending Player.
        // Unfortunately, this leads to our own type limitations. We can work around those by converting to an array,
        // which has the side benefit of allowing us to support every other data type with the same generic method
        // instead of having to have otherwise-identical array and iterable methods.
        Player[] onlinePlayers = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        // Require sender to be able to see a player to complete their name.
        Predicate<Player> canSee = sender instanceof Player ? ((Player) sender)::canSee : null;
        return complete(onlinePlayers, Player::getName, canSee, args);
    }

    /**
     * Fires an AlliumTabCompletionsEvent via reflection. If Allium's PartyManager is active
     * and populates the result, returns the completions list. Otherwise returns null.
     */
    private static @Nullable List<String> queryAlliumCompletions(@NotNull Player sender, @NotNull String[] args)
    {
        try
        {
            org.bukkit.plugin.Plugin allium = Bukkit.getPluginManager().getPlugin("Allium");
            if (allium == null || !allium.isEnabled()) return null;

            Class<?> eventClass = Class.forName("codes.castled.allium.events.AlliumTabCompletionsEvent");
            Object event = eventClass.getConstructor(Player.class).newInstance(sender);

            Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);

            @SuppressWarnings("unchecked")
            List<String> completions = (List<String>) eventClass.getMethod("getCompletions").invoke(event);
            if (completions == null) return null;

            // Filter completions by the typed prefix
            String prefix = asPrefix(args);
            List<String> filtered = new ArrayList<>();
            for (String name : completions) {
                if (prefix.isEmpty() || StringUtil.startsWithIgnoreCase(name, prefix)) {
                    filtered.add(name);
                }
            }
            filtered.sort(String.CASE_INSENSITIVE_ORDER);
            return filtered;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Offer completions matching an array of options. Options can be filtered.
     *
     * @param completable the array of completable options
     * @param asString the method for converting an option to a String
     * @param filter the filter to apply, or null for no filtering
     * @param args the existing command arguments
     * @return a {@link List} of all matching completable options in {@code String} form
     * @param <T> the type of the option
     */
    private static <T> @NotNull List<String> complete(
            T @NotNull [] completable,
            @NotNull Function<T, String> asString,
            @Nullable Predicate<T> filter,
            @NotNull String[] args)
    {
        String prefix = asPrefix(args);

        List<String> completions = new ArrayList<>();

        for (T element : completable)
        {
            // Require element to be present and match filter, if provided.
            if (element == null || filter != null && !filter.test(element)) continue;

            String string = asString.apply(element);
            // Require element string to be non-empty and start with user's existing text.
            if (string != null
                    && !string.isEmpty()
                    && (prefix.isEmpty() || StringUtil.startsWithIgnoreCase(string, prefix)))
            {
                completions.add(string);
            }
        }

        // Sort completions alphabetically.
        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions;
    }

    private static @NotNull String asPrefix(@NotNull String[] args)
    {
        // Length should never be 0 because that case should be handled by Bukkit completing the raw command name.
        // Never hurts to be safe though.
        return args.length == 0 ? "" : args[args.length - 1];
    }

    private TabCompletions() {}

}
