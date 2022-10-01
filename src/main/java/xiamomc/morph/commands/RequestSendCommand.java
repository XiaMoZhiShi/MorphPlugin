package xiamomc.morph.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xiamomc.morph.MorphManager;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Command.IPluginCommand;
import xiamomc.pluginbase.PluginObject;

public class RequestSendCommand extends PluginObject implements IPluginCommand {
    @Override
    public String getCommandName() {
        return "sendrequest";
    }

    @Resolved
    private MorphManager morphs;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player sourcePlayer)
        {
            if (args.length >= 1)
            {
                var targetPlayer = Bukkit.getPlayer(args[0]);

                if (targetPlayer == null)
                {
                    //todo
                    return true;
                }

                if (targetPlayer.getUniqueId().equals(sourcePlayer.getUniqueId()))
                {
                    sourcePlayer.sendMessage(Component.text("不能给自己发请求"));
                    return true;
                }

                if (morphs.getAvaliableDisguisesFor(sourcePlayer).stream()
                        .anyMatch(c -> c.isPlayerDisguise && c.playerDisguiseTargetName.equals(args[0])))
                {
                    sourcePlayer.sendMessage(Component.text("你已经有对方的伪装形态了"));
                    return true;
                }

                morphs.createRequest(sourcePlayer, targetPlayer);
            }
            else
            {
                //todo
            }
        }

        return true;
    }
}