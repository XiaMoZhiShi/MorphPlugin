package xiamomc.morph.commands;

import xiamomc.morph.commands.subcommands.MorphSubCommandHandler;
import xiamomc.morph.commands.subcommands.request.AcceptSubCommand;
import xiamomc.morph.commands.subcommands.request.DenySubCommand;
import xiamomc.morph.commands.subcommands.request.SendSubCommand;
import xiamomc.morph.messages.HelpStrings;
import xiamomc.pluginbase.Command.ISubCommand;
import xiamomc.pluginbase.messages.FormattableMessage;

import java.util.Formattable;
import java.util.List;

public class RequestCommand extends MorphSubCommandHandler
{
    private final List<ISubCommand> subCommands = List.of(
            new SendSubCommand(),
            new AcceptSubCommand(),
            new DenySubCommand()
    );

    @Override
    public List<ISubCommand> getSubCommands()
    {
        return subCommands;
    }

    private final List<FormattableMessage> notes = List.of(
            HelpStrings.requestDescriptionSpecialNote()
    );

    @Override
    public List<FormattableMessage> getNotes()
    {
        return notes;
    }

    @Override
    public String getCommandName()
    {
        return "request";
    }

    @Override
    public String getPermissionRequirement()
    {
        return null;
    }

    @Override
    public FormattableMessage getHelpMessage()
    {
        return HelpStrings.requestDescription();
    }
}
