package xiamomc.morph.commands.subcommands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import xiamomc.morph.MorphPlugin;
import xiamomc.morph.messages.CommonStrings;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.pluginbase.Command.SubCommandHandler;

public abstract class MorphSubCommandHandler extends SubCommandHandler<MorphPlugin>
{
    @Override
    protected String getPluginNamespace()
    {
        return MorphPlugin.getMorphNameSpace();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
        var result = super.onCommand(sender, command, label, args);

        if (!result)
            sender.sendMessage(MessageUtils.prefixes(sender, CommonStrings.commandNotFoundString()));

        return true;
    }
}
