package xiamomc.morph.commands.subcommands.plugin.management;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.MorphManager;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.messages.*;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Command.ISubCommand;
import xiamomc.pluginbase.Messages.FormattableMessage;

import java.util.List;
import java.util.Objects;

public class ForceMorphSubCommand extends MorphPluginObject implements ISubCommand
{
    @Override
    public @NotNull String getCommandName()
    {
        return "morph";
    }

    @Override
    public @Nullable String getPermissionRequirement()
    {
        return "xiamomc.morph.manage.morph";
    }

    @Resolved
    private MorphManager manager;

    @Override
    public @Nullable List<String> onTabComplete(List<String> args, CommandSender source)
    {
        var list = new ObjectArrayList<String>();

        if (args.size() == 1)
        {
            var name = args.get(0);

            var onlinePlayers = Bukkit.getOnlinePlayers();

            for (var p : onlinePlayers)
                if (p.getName().toLowerCase().contains(name.toLowerCase())) list.add(p.getName());
        }
        else if (args.size() == 2)
        {
            list.add("<id>");
        }

        return list;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, String[] strings)
    {
        if (strings.length != 2) return false;

        var who = Bukkit.getPlayerExact(strings[0]);
        var targetName = strings[1];

        if (who == null || !who.isOnline())
        {
            commandSender.sendMessage(MessageUtils.prefixes(commandSender, CommonStrings.playerNotFoundString()));
            return false;
        }

        //检查是否已知
        var provider = MorphManager.getProvider(strings[1]);

        if (provider == null)
        {
            commandSender.sendMessage(MessageUtils.prefixes(commandSender, MorphStrings.invalidIdentityString()));
            return true;
        }

        FormattableMessage msg;

        if (manager.morph(who, targetName, who.getTargetEntity(3), true, true))
            msg = MorphStrings.morphSuccessString();
        else
            msg = MorphStrings.errorWhileDisguising();

        msg.resolve("what", Component.text(targetName)).resolve("who", who.getName());
        commandSender.sendMessage(MessageUtils.prefixes(commandSender, msg));

        return true;
    }

    @Override
    public FormattableMessage getHelpMessage()
    {
        return HelpStrings.manageUnmorphDescription();
    }
}
