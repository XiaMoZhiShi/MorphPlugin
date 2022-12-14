package xiamomc.morph;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.Scoreboard;
import xiamomc.morph.abilities.AbilityHandler;
import xiamomc.morph.commands.MorphCommandHelper;
import xiamomc.morph.config.MorphConfigManager;
import xiamomc.morph.events.*;
import xiamomc.morph.interfaces.IManagePlayerData;
import xiamomc.morph.interfaces.IManageRequests;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.MorphMessageStore;
import xiamomc.morph.messages.vanilla.VanillaMessageStore;
import xiamomc.morph.misc.PlayerOperationSimulator;
import xiamomc.morph.misc.integrations.gsit.GSitCompactProcessor;
import xiamomc.morph.misc.integrations.placeholderapi.PlaceholderIntegration;
import xiamomc.morph.network.MorphClientHandler;
import xiamomc.morph.skills.MorphSkillHandler;
import xiamomc.morph.storage.skill.SkillConfigurationStore;
import xiamomc.pluginbase.Command.CommandHelper;
import xiamomc.pluginbase.XiaMoJavaPlugin;
import xiamomc.pluginbase.Messages.MessageStore;

public final class MorphPlugin extends XiaMoJavaPlugin
{
    public static String getMorphNameSpace()
    {
        return "morphplugin";
    }

    @Override
    public String getNameSpace()
    {
        return getMorphNameSpace();
    }

    private final CommandHelper<MorphPlugin> cmdHelper = new MorphCommandHelper();

    private MorphManager morphManager;

    private PluginManager pluginManager;

    private final MorphSkillHandler skillHandler = new MorphSkillHandler();

    private final AbilityHandler abilityHandler = new AbilityHandler();

    private final VanillaMessageStore languageHelper = new VanillaMessageStore();

    private PlaceholderIntegration placeholderIntegration;

    private MorphClientHandler clientHandler;

    @Override
    public void onEnable()
    {
        super.onEnable();

        pluginManager = Bukkit.getPluginManager();

        clientHandler = new MorphClientHandler();

        var playerTracker = new PlayerTracker();
        var pluginEventListener = new PluginEventListener();
        pluginEventListener.onPluginEnable(this::onPluginEnable);

        //????????????
        dependencyManager.cache(this);
        dependencyManager.cache(morphManager = new MorphManager());
        dependencyManager.cache(skillHandler);
        dependencyManager.cache(abilityHandler);
        dependencyManager.cache(cmdHelper);
        dependencyManager.cache(clientHandler);
        dependencyManager.cache(languageHelper);

        dependencyManager.cacheAs(MessageStore.class, new MorphMessageStore());
        dependencyManager.cacheAs(MiniMessage.class, MiniMessage.miniMessage());
        dependencyManager.cacheAs(IManagePlayerData.class, morphManager);
        dependencyManager.cacheAs(IManageRequests.class, new RequestManager());
        dependencyManager.cacheAs(Scoreboard.class, Bukkit.getScoreboardManager().getMainScoreboard());
        dependencyManager.cacheAs(MorphConfigManager.class, new MorphConfigManager(this));
        dependencyManager.cache(playerTracker);

        dependencyManager.cache(new SkillConfigurationStore());

        dependencyManager.cache(new MessageUtils());

        dependencyManager.cache(new PlayerOperationSimulator());

        //??????EventProcessor
        this.schedule(() ->
        {
            registerListeners(new Listener[]
                    {
                            playerTracker,
                            pluginEventListener,
                            new ReverseControlProcessor(),
                            new CommonEventProcessor(),
                    });

            for (Plugin plugin : pluginManager.getPlugins())
                onPluginEnable(plugin.getName());

            clientHandler.sendReAuth(Bukkit.getOnlinePlayers());
        });
    }

    @Override
    public void onDisable()
    {
        //??????super.onDisable??????????????????????????????
        //?????????????????????????????????????????????
        try
        {
            if (morphManager != null)
                morphManager.onPluginDisable();

            if (placeholderIntegration != null)
                placeholderIntegration.unregister();

            if (clientHandler != null)
                clientHandler.getClientPlayers().forEach(clientHandler::unInitializePlayer);
        }
        catch (Exception e)
        {
            logger.warn("????????????????????????" + e.getMessage());
            e.printStackTrace();
        }

        super.onDisable();
    }

    private void registerListeners(Listener[] listeners)
    {
        for (Listener l : listeners)
        {
            registerListener(l);
        }
    }

    private void registerListener(Listener l)
    {
        pluginManager.registerEvents(l, this);
    }

    public void onPluginEnable(String name)
    {
        switch (name)
        {
            case "GSit" ->
            {
                registerListener(new GSitCompactProcessor());
            }

            case "PlaceholderAPI" ->
            {
                placeholderIntegration = new PlaceholderIntegration(dependencyManager);
                placeholderIntegration.register();
            }
        }
    }
}
