package com.griefprevention.commands;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Deprecated
public class ClaimCommand extends CommandHandler
{
    private final ClaimCreateCommandAction createAction;

    public ClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin, "claim");
        this.createAction = new ClaimCreateCommandAction(plugin);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args)
    {
        return this.createAction.execute(sender, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args)
    {
        return this.createAction.onTabComplete(sender, command, alias, args);
    }

}
