package xiamomc.morph.commands.subcommands.plugin.helpsections;

import xiamomc.pluginbase.messages.FormattableMessage;

public record Entry(String permission, String baseName, FormattableMessage description, String suggestingCommand)
{
    @Override
    public String toString()
    {
        return baseName + "的Entry";
    }
}
