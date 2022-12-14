package xiamomc.morph;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.utilities.DisguiseValues;
import me.libraryaddict.disguise.utilities.reflection.FakeBoundingBox;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.abilities.AbilityHandler;
import xiamomc.morph.config.ConfigOption;
import xiamomc.morph.config.MorphConfigManager;
import xiamomc.morph.events.PlayerMorphEvent;
import xiamomc.morph.events.PlayerUnMorphEvent;
import xiamomc.morph.interfaces.IManagePlayerData;
import xiamomc.morph.messages.CommandStrings;
import xiamomc.morph.messages.HintStrings;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.MorphStrings;
import xiamomc.morph.misc.*;
import xiamomc.morph.misc.permissions.CommonPermissions;
import xiamomc.morph.network.MorphClientHandler;
import xiamomc.morph.network.commands.S2C.*;
import xiamomc.morph.providers.*;
import xiamomc.morph.skills.MorphSkillHandler;
import xiamomc.morph.skills.SkillCooldownInfo;
import xiamomc.morph.skills.SkillType;
import xiamomc.morph.storage.offlinestore.OfflineDisguiseState;
import xiamomc.morph.storage.offlinestore.OfflineStorageManager;
import xiamomc.morph.storage.playerdata.PlayerDataStore;
import xiamomc.morph.storage.playerdata.PlayerMorphConfiguration;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;
import xiamomc.pluginbase.Bindables.BindableList;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MorphManager extends MorphPluginObject implements IManagePlayerData
{
    /**
     * ??????????????????
     */
    private final List<DisguiseState> disguisedPlayers = new ObjectArrayList<>();

    private final PlayerDataStore data = new PlayerDataStore();

    private final OfflineStorageManager offlineStorage = new OfflineStorageManager();

    @Resolved
    private MorphSkillHandler skillHandler;

    @Resolved
    private AbilityHandler abilityHandler;

    @Resolved
    private MorphConfigManager config;

    private static final DisguiseProvider fallbackProvider = new FallbackProvider();

    public static final String disguiseFallbackName = "@default";

    @Initializer
    private void load()
    {
        this.addSchedule(this::update);

        bannedDisguises = config.getBindableList(String.class, ConfigOption.BANNED_DISGUISES);
        config.bind(allowHeadMorph, ConfigOption.ALLOW_HEAD_MORPH);

        registerProviders(ObjectList.of(
                new VanillaDisguiseProvider(),
                new PlayerDisguiseProvider(),
                new LocalDisguiseProvider(),
                fallbackProvider
        ));
    }

    private void update()
    {
        var states = this.getDisguisedPlayers();

        states.forEach(i ->
        {
            var p = i.getPlayer();

            //??????????????????
            if (!p.isOnline()) return;

            var disg = DisguiseAPI.getDisguise(p);
            var disgInState = i.getDisguise();

            //??????State????????????????????????????????????
            if (!disgInState.equals(disg))
            {
                if (DisguiseUtils.isTracing(disgInState))
                {
                    logger.warn(p.getName() + "???State??????????????????Tracing??????????????????DisguiseAPI?????????????????????");
                    logger.warn("API: " + disg + " :: State: " + disgInState);

                    p.sendMessage(MessageUtils.prefixes(p, MorphStrings.errorWhileDisguising()));
                    unMorph(p, true);
                }
                else
                {
                    logger.warn("???????????????Morph???????????????: " + p + " :: " + i.getDisguise() + " <-> " + disg);
                    unMorph(p, true);
                    DisguiseAPI.disguiseEntity(p, disg);
                    disguisedPlayers.remove(i);
                }

                return;
            }

            abilityHandler.handle(p, i);

            if (!i.getProvider().updateDisguise(p, i))
            {
                p.sendMessage(MessageUtils.prefixes(p, MorphStrings.errorWhileUpdatingDisguise()));

                unMorph(p, true);
            }
        });

        this.addSchedule(this::update);
    }

    //region ??????????????????

    /**
     * ??????????????????????????????????????????
     * @param player ????????????
     */
    public void executeDisguiseSkill(Player player)
    {
        skillHandler.executeDisguiseSkill(player);
    }

    /**
     * ??????????????????????????????
     * @return ????????????
     * @apiNote ????????????????????????????????????
     */
    public List<DisguiseState> getDisguisedPlayers()
    {
        return new ObjectArrayList<>(disguisedPlayers);
    }

    private final Map<UUID, Long> uuidMoprhTimeMap = new ConcurrentHashMap<>();

    /**
     * ????????????????????????????????????
     * @param player ??????
     * @return ??????????????????
     */
    public boolean canMorph(Player player)
    {
        return this.canMorph(player.getUniqueId());
    }

    /**
     * ????????????????????????????????????
     * @param uuid ??????UUID
     * @return ??????????????????
     */
    public boolean canMorph(UUID uuid)
    {
        var val = uuidMoprhTimeMap.get(uuid);

        return val == null || plugin.getCurrentTick() - val >= 4;
    }

    /**
     * ?????????????????????????????????????????????
     * @param player ??????????????????
     */
    public void updateLastPlayerMorphOperationTime(Player player)
    {
        uuidMoprhTimeMap.put(player.getUniqueId(), plugin.getCurrentTick());
    }

    private BindableList<String> bannedDisguises;

    /**
     * ???????????????????????????????????????????????????????????? {@link MorphManager#disguiseDisabled(String)}
     */
    @ApiStatus.Internal
    public BindableList<String> getBannedDisguises()
    {
        return bannedDisguises;
    }

    //region ???????????????

    private static final List<DisguiseProvider> providers = new ObjectArrayList<>();

    public static List<DisguiseProvider> getProviders()
    {
        return new ObjectArrayList<>(providers);
    }

    /**
     * ???ID??????DisguiseProvider
     * @param id ??????ID
     * @return ??????DisguiseProvider??????????????????id???null?????????null
     */
    public static DisguiseProvider getProvider(String id)
    {
        if (id == null) return null;

        id += ":";
        var splitedId = id.split(":", 2);

        return providers.stream().filter(p -> p.getNameSpace().equals(splitedId[0])).findFirst().orElse(fallbackProvider);
    }

    /**
     * ????????????DisguiseProvider
     * @param provider ??????Provider
     * @return ??????????????????
     */
    public boolean registerProvider(DisguiseProvider provider)
    {
        logger.info("????????????????????????" + provider.getNameSpace());

        if (provider.getNameSpace().contains(":"))
        {
            logger.error("?????????????????????????????????????????????:???");
            return false;
        }

        if (providers.stream().anyMatch(p -> p.getNameSpace().equals(provider.getNameSpace())))
        {
            logger.error("?????????????????????ID???" + provider.getNameSpace() + "???Provider???");
            return false;
        }

        providers.add(provider);
        return true;
    }

    /**
     * ????????????DisguiseProvider
     * @param providers Provider??????
     * @return ????????????????????????
     */
    public boolean registerProviders(List<DisguiseProvider> providers)
    {
        AtomicBoolean success = new AtomicBoolean(false);

        providers.forEach(p -> success.set(registerProvider(p) || success.get()));

        return success.get();
    }

    //endregion

    private final Bindable<Boolean> allowHeadMorph = new Bindable<>();

    private final Map<UUID, PlayerTextures> uuidPlayerTexturesMap = new ConcurrentHashMap<>();

    public boolean doQuickDisguise(Player player, @Nullable Material actionItem)
    {
        var state = this.getDisguiseStateFor(player);
        var mainHandItem = player.getEquipment().getItemInMainHand();
        var mainHandItemType = mainHandItem.getType();

        //?????????????????????????????????
        if (DisguiseUtils.validForHeadMorph(mainHandItemType))
        {
            if (!player.hasPermission(CommonPermissions.HEAD_MORPH))
            {
                player.sendMessage(MessageUtils.prefixes(player, CommandStrings.noPermissionMessage()));

                return true;
            }

            if (!allowHeadMorph.get())
            {
                player.sendMessage(MessageUtils.prefixes(player, MorphStrings.headDisguiseDisabledString()));

                return true;
            }

            if (!canMorph(player))
            {
                player.sendMessage(MessageUtils.prefixes(player, MorphStrings.disguiseCoolingDownString()));

                return true;
            }

            var targetEntity = player.getTargetEntity(5);

            switch (mainHandItemType)
            {
                case DRAGON_HEAD ->
                {
                    morphOrUnMorph(player, EntityType.ENDER_DRAGON.getKey().asString(), targetEntity);
                }
                case ZOMBIE_HEAD ->
                {
                    morphOrUnMorph(player, EntityType.ZOMBIE.getKey().asString(), targetEntity);
                }
                case SKELETON_SKULL ->
                {
                    morphOrUnMorph(player, EntityType.SKELETON.getKey().asString(), targetEntity);
                }
                case WITHER_SKELETON_SKULL ->
                {
                    morphOrUnMorph(player, EntityType.WITHER_SKELETON.getKey().asString(), targetEntity);
                }
                case PLAYER_HEAD ->
                {
                    var profile = ((SkullMeta) mainHandItem.getItemMeta()).getPlayerProfile();

                    //????????????profile???????????????
                    if (profile == null)
                    {
                        player.sendMessage(MessageUtils.prefixes(player, MorphStrings.invalidSkinString()));
                        return true;
                    }

                    var name = profile.getName();
                    var profileTexture = profile.getTextures();
                    var playerUniqueId = player.getUniqueId();

                    //????????????????????????????????????????????????Profile?????????????????????????????????
                    if (state != null)
                    {
                        var disguise = state.getDisguise();

                        if (disguise instanceof PlayerDisguise playerDisguise
                                && playerDisguise.getName().equals(name)
                                && profileTexture.equals(uuidPlayerTexturesMap.get(playerUniqueId)))
                        {
                            unMorph(player);
                            return true;
                        }
                    }

                    //??????????????????????????????
                    morph(player, DisguiseTypes.PLAYER.toId(profile.getName()), targetEntity);

                    uuidPlayerTexturesMap.put(playerUniqueId, profileTexture);
                }
            }

            updateLastPlayerMorphOperationTime(player);
        }
        else
        {
            if (actionItem != null && !mainHandItemType.equals(actionItem))
                return false;

            var targetedEntity = player.getTargetEntity(5);

            if (targetedEntity != null)
            {
                var disg = DisguiseAPI.getDisguise(targetedEntity);

                String targetKey;

                if (targetedEntity instanceof Player targetPlayer)
                {
                    var playerState = this.getDisguiseStateFor(targetPlayer);

                    //????????????????????????????????????ID > ?????????
                    targetKey = playerState != null
                            ? playerState.getDisguiseIdentifier()
                            : DisguiseTypes.PLAYER.toId(targetPlayer.getName());
                }
                else
                {
                    //???????????????ID > ???????????? > ????????????
                    targetKey = disg != null
                            ? (disg instanceof PlayerDisguise pd)
                            ? DisguiseTypes.PLAYER.toId(pd.getName())
                            : disg.getType().getEntityType().getKey().asString()
                            : targetedEntity.getType().getKey().asString();
                }

                morph(player, targetKey, targetedEntity);

                return true;
            }
        }

        return false;
    }

    /**
     * ???????????????key???????????????????????????????????????
     * ????????????ID????????????ID????????????????????????????????????????????????
     *
     * @param player ????????????
     * @param key ??????ID
     * @param targetEntity ???????????????????????????
     */
    public void morphOrUnMorph(Player player, String key, @Nullable Entity targetEntity)
    {
        var state = this.getDisguiseStateFor(player);

        if (state != null && state.getDisguiseIdentifier().equals(key))
            unMorph(player);
        else
            morph(player, key, targetEntity);
    }

    /**
     * ??????????????????
     *
     * @param player ????????????
     * @param key ??????ID
     * @param targetEntity ???????????????????????????
     * @return ??????????????????
     */
    public boolean morph(Player player, String key, @Nullable Entity targetEntity)
    {
        return this.morph(player, key, targetEntity, false, false);
    }

    /**
     * ??????????????????
     *
     * @param player ??????????????????
     * @param key ??????ID
     * @param targetEntity ????????????????????????
     * @param bypassPermission ????????????????????????
     * @param bypassAvailableCheck ????????????????????????
     * @return ??????????????????
     */
    public boolean morph(Player player, String key, @Nullable Entity targetEntity,
                         boolean bypassPermission, boolean bypassAvailableCheck)
    {
        if (!bypassPermission && !player.hasPermission(CommonPermissions.MORPH))
        {
            player.sendMessage(MessageUtils.prefixes(player, CommandStrings.noPermissionMessage()));

            return false;
        }

        if (!key.contains(":")) key = DisguiseTypes.VANILLA.toId(key);

        String finalKey = key;
        DisguiseInfo info = null;

        if (!bypassAvailableCheck)
        {
            info = getAvaliableDisguisesFor(player).stream()
                    .filter(i -> i.getIdentifier().equals(finalKey)).findFirst().orElse(null);
        }
        else if (!key.equals("minecraft:player"))
        {
            info = new DisguiseInfo(key, DisguiseTypes.fromId(key));
        }

        if (disguiseDisabled(key))
        {
            player.sendMessage(MessageUtils.prefixes(player, MorphStrings.disguiseBannedOrNotSupportedString()));
            return false;
        }

        var state = getDisguiseStateFor(player);

        //?????????????????????
        if (info != null)
        {
            try
            {
                //??????provider
                var strippedKey = key.split(":", 2);

                var provider = getProvider(strippedKey[0]);

                DisguiseState outComingState = null;

                if (provider == null)
                {
                    player.sendMessage(MessageUtils.prefixes(player, MorphStrings.disguiseBannedOrNotSupportedString()));
                    logger.error("???????????????????????????" + strippedKey[0] + "?????????Provider");
                    return false;
                }
                else
                {
                    var result = provider.morph(player, info, targetEntity);

                    if (!result.success())
                    {
                        player.sendMessage(MessageUtils.prefixes(player, MorphStrings.errorWhileDisguising()));
                        logger.error(provider + "??????????????????????????????");
                        return false;
                    }

                    //????????????State?????????
                    if (state != null)
                    {
                        state.getProvider().unMorph(player, state);
                        state.getAbilities().forEach(a -> a.revokeFromPlayer(player, state));

                        var skill = state.getSkill();

                        if (skill != null)
                            skill.onDeEquip(state);
                    }

                    clientHandler.updateCurrentIdentifier(player, key);

                    outComingState = postConstructDisguise(player, targetEntity,
                            info.getIdentifier(), result.disguise(), result.isCopy(), provider);
                }

                var playerLocale = MessageUtils.getLocale(player);

                var msg = MorphStrings.morphSuccessString()
                        .withLocale(playerLocale)
                        .resolve("what", info.asComponent(playerLocale));

                player.sendMessage(MessageUtils.prefixes(player, msg));

                //????????????????????????????????????????????????????????????????????????
                if (provider.validForClient(state))
                {
                    clientHandler.sendClientCommand(player, new S2CSetSelfViewCommand(provider.getSelfViewIdentifier(outComingState)));

                    provider.getInitialSyncCommands(outComingState).forEach(s -> clientHandler.sendClientCommand(player, s));

                    //?????????nbt
                    var compound = provider.getNbtCompound(outComingState, targetEntity);

                    if (compound != null)
                    {
                        outComingState.setCachedNbtString(NbtUtils.getCompoundString(compound));
                        clientHandler.sendClientCommand(player, new S2CSetNbtCommand(compound));
                    }

                    //??????Profile
                    if (outComingState.haveProfile())
                        clientHandler.sendClientCommand(player, new S2CSetProfileCommand(outComingState.getProfileNbtString()));
                }

                return true;
            }
            catch (IllegalArgumentException iae)
            {
                player.sendMessage(MessageUtils.prefixes(player, MorphStrings.parseErrorString()
                        .resolve("id", key)));

                logger.error("???????????? " + key + ": " + iae.getMessage());
                iae.printStackTrace();

                return false;
            }
        }
        else
        {
            player.sendMessage(MessageUtils.prefixes(player, MorphStrings.morphNotOwnedString()));
        }

        return false;
    }

    /**
     * ????????????????????????????????????
     * @param key ??????ID
     * @return ???????????????????????????
     */
    public boolean disguiseDisabled(String key)
    {
        if (bannedDisguises.contains(key)) return true;

        var splitKey = key.split(":", 2);

        if (splitKey.length == 0) return false;

        return bannedDisguises.contains(splitKey[0] + ":any");
    }

    public void refreshClientState(DisguiseState state)
    {
        var player = state.getPlayer();

        clientHandler.updateCurrentIdentifier(player, state.getDisguiseIdentifier());
        clientHandler.sendClientCommand(player, new S2CSetSNbtCommand(state.getCachedNbtString()));
        clientHandler.sendClientCommand(player, new S2CSetSelfViewCommand(state.getProvider().getSelfViewIdentifier(state)));

        //????????????
        var skill = state.getSkill();

        if (skill != null)
        {
            state.setSkillCooldown(state.getSkillCooldown());
            skill.onClientinit(state);
        }

        //????????????
        var abilities = state.getAbilities();

        if (abilities != null)
            abilities.forEach(a -> a.onClientInit(state));

        //????????????????????????
        state.getProvider().getInitialSyncCommands(state).forEach(c -> clientHandler.sendClientCommand(player, c));

        //Profile
        if (state.haveProfile())
            clientHandler.sendClientCommand(player, new S2CSetProfileCommand(state.getProfileNbtString()));
    }

    /**
     * ???????????????????????????
     */
    public void unMorphAll(boolean ignoreOffline)
    {
        var players = new ObjectArrayList<>(disguisedPlayers);
        players.forEach(i ->
        {
            if (ignoreOffline && !i.getPlayer().isOnline()) return;

            unMorph(i.getPlayer(), true);
        });
    }

    public void unMorph(Player player)
    {
        this.unMorph(player, false);
    }

    /**
     * ???????????????????????????
     *
     * @param player ????????????
     */
    public void unMorph(Player player, boolean bypassPermission)
    {
        if (!bypassPermission && !player.hasPermission(CommonPermissions.UNMORPH))
        {
            player.sendMessage(MessageUtils.prefixes(player, CommandStrings.noPermissionMessage()));

            return;
        }

        var state = disguisedPlayers.stream()
                .filter(i -> i.getPlayerUniqueID().equals(player.getUniqueId())).findFirst().orElse(null);

        if (state == null)
            return;

        state.getProvider().unMorph(player, state);

        //??????????????????
        state.getAbilities().forEach(a -> a.revokeFromPlayer(player, state));

        spawnParticle(player, player.getLocation(), player.getWidth(), player.getHeight(), player.getWidth());

        disguisedPlayers.remove(state);

        updateLastPlayerMorphOperationTime(player);

        //??????CD
        skillHandler.switchCooldown(player.getUniqueId(), null);

        //??????Bossbar
        state.setBossbar(null);

        player.sendMessage(MessageUtils.prefixes(player, MorphStrings.unMorphSuccessString().withLocale(MessageUtils.getLocale(player))));
        player.sendActionBar(Component.empty());

        uuidPlayerTexturesMap.remove(player.getUniqueId());

        clientHandler.updateCurrentIdentifier(player, null);
        clientHandler.sendClientCommand(player, new S2CSetSelfViewCommand(null));

        Bukkit.getPluginManager().callEvent(new PlayerUnMorphEvent(player));
    }

    private void postConstructDisguise(DisguiseState state)
    {
        postConstructDisguise(state.getPlayer(), null,
                state.getDisguiseIdentifier(), state.getDisguise(), state.shouldHandlePose(), state.getProvider());
    }

    /**
     * ?????????????????????????????????
     *
     * @param sourcePlayer     ???????????????
     * @param targetEntity     ?????????????????????
     * @param disguise         ??????
     * @param shouldHandlePose ???????????????????????????Pose??????????????????????????????
     * @param provider {@link DisguiseProvider}
     */
    private DisguiseState postConstructDisguise(Player sourcePlayer, @Nullable Entity targetEntity,
                                                String id, Disguise disguise, boolean shouldHandlePose,
                                                @NotNull DisguiseProvider provider)
    {
        //?????????????????????????????????
        DisguiseUtils.addTrace(disguise);

        var disguiseTypeLD = disguise.getType();

        var config = getPlayerConfiguration(sourcePlayer);

        //??????actionBar
        DisguiseAPI.setActionBarShown(sourcePlayer, false);

        //??????????????????DisguiseState
        var state = getDisguiseStateFor(sourcePlayer);

        EntityEquipment equipment = null;

        var theirState = getDisguiseStateFor(targetEntity);
        if (targetEntity != null && provider.canConstruct(getDisguiseInfo(id), targetEntity, theirState))
        {
            if (theirState != null)
            {
                equipment = theirState.showingDisguisedItems()
                        ? theirState.getDisguisedItems()
                        : ((LivingEntity) targetEntity).getEquipment();
            }
            else
                equipment = ((LivingEntity) targetEntity).getEquipment();
        }

        //??????
        var rawIdentifierHasSkill = skillHandler.hasSkill(id) || skillHandler.hasSpeficSkill(id, SkillType.NONE);
        var targetSkillID = rawIdentifierHasSkill ? id : provider.getNameSpace() + ":" + MorphManager.disguiseFallbackName;

        if (state == null)
        {
            state = new DisguiseState(sourcePlayer, id, targetSkillID, disguise, shouldHandlePose, provider, equipment);

            disguisedPlayers.add(state);
        }
        else
        {
            state.setDisguise(id, targetSkillID, disguise, shouldHandlePose, equipment);
        }

        if (provider != fallbackProvider)
            provider.postConstructDisguise(state, targetEntity);
        else
            logger.warn("id??? " + id + " ???????????????Provider?");

        //workaround: Disguise#getDisguiseName()??????????????????????????????????????????
        if (targetEntity != null && targetEntity.customName() != null)
            state.setDisplayName(targetEntity.customName());

        //??????????????????????????????????????????
        if (sourcePlayer.getVehicle() != null && !clientHandler.clientInitialized(sourcePlayer))
            sourcePlayer.sendMessage(MessageUtils.prefixes(sourcePlayer, MorphStrings.morphVisibleAfterStandup()));

        //????????????
        var cX = 0d;
        var cZ = 0d;
        var cY = 0d;

        //????????????????????????????????????????????????????????????
        if (disguise.isMobDisguise())
        {
            var mobDisguise = (MobDisguise) disguise;
            FakeBoundingBox box;

            var values = DisguiseValues.getDisguiseValues(disguiseTypeLD);

            if (!mobDisguise.isAdult() && values.getBabyBox() != null)
                box = values.getBabyBox();
            else
                box = values.getAdultBox();

            cX = box.getX();
            cY = box.getY();
            cZ = box.getZ();
        }
        else //????????????????????????????????????
        {
            cX = cZ = sourcePlayer.getWidth();
            cY = sourcePlayer.getHeight();
        }

        spawnParticle(sourcePlayer, sourcePlayer.getLocation(), cX, cY, cZ);

        //???????????????????????????????????????????????????
        state.setServerSideSelfVisible(config.showDisguiseToSelf && !this.clientViewAvailable(sourcePlayer));

        var isClientPlayer = clientHandler.clientConnected(sourcePlayer);

        if (state.getSkill() != null)
        {
            if (isClientPlayer)
            {
                if (!config.shownClientSkillHint)
                {
                    sourcePlayer.sendMessage(MessageUtils.prefixes(sourcePlayer, HintStrings.clientSkillString()));
                    config.shownClientSkillHint = true;
                }
            }
            else if (!config.shownMorphAbilityHint)
            {
                sourcePlayer.sendMessage(MessageUtils.prefixes(sourcePlayer, HintStrings.skillString()));
                config.shownMorphAbilityHint = true;
            }
        }

        if (!config.shownClientSuggestionMessage && !isClientPlayer)
        {
            sourcePlayer.sendMessage(MessageUtils.prefixes(sourcePlayer, HintStrings.clientSuggestionStringA()));
            sourcePlayer.sendMessage(MessageUtils.prefixes(sourcePlayer, HintStrings.clientSuggestionStringB()));

            config.shownClientSuggestionMessage = true;
        }

        if (!config.shownDisplayToSelfHint && !isClientPlayer)
        {
            sourcePlayer.sendMessage(MessageUtils.prefixes(sourcePlayer, HintStrings.morphVisibleAfterCommandString()));
            config.shownDisplayToSelfHint = true;
        }

        //????????????????????????
        updateLastPlayerMorphOperationTime(sourcePlayer);

        SkillCooldownInfo cdInfo;

        //CD??????
        cdInfo = skillHandler.getCooldownInfo(sourcePlayer.getUniqueId(), targetSkillID);

        if (cdInfo != null)
        {
            cdInfo.setCooldown(Math.max(40, state.getSkillCooldown()));
            cdInfo.setLastInvoke(plugin.getCurrentTick());
            state.setCooldownInfo(cdInfo);
        }

        //??????CD
        skillHandler.switchCooldown(sourcePlayer.getUniqueId(), cdInfo);

        //????????????
        Bukkit.getPluginManager().callEvent(new PlayerMorphEvent(sourcePlayer, state));

        return state;
    }

    public void spawnParticle(Player player, Location location, double collX, double collY, double collZ)
    {
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        location.setY(location.getY() + (collY / 2));

        //???????????????????????????????????????
        //???????????????????????????1/15????????????1
        var particleScale = Math.max(1, (collX * collY * collZ) / 15);

        //????????????
        player.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, location, //???????????????
                (int) (25 * particleScale), //??????
                collX * 0.6, collY / 4, collZ * 0.6, //????????????
                particleScale >= 10 ? 0.2 : 0.05); //??????
    }

    public void setSelfDisguiseVisible(Player player, boolean val, boolean saveToConfig)
    {
        this.setSelfDisguiseVisible(player, val, saveToConfig, clientHandler.getPlayerOption(player).isClientSideSelfView(), false);
    }

    /**
     * ??????????????????????????????
     * @param player ????????????
     * @return ????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    public boolean clientViewAvailable(Player player)
    {
        var state = this.getDisguiseStateFor(player);

        if (state == null)
            return clientHandler.getPlayerOption(player).isClientSideSelfView();

        //logger.warn(player.getName() + " SV "
        //            + " Option? " + clientHandler.getPlayerOption(player).isClientSideSelfView()
        //            + " StateValid? " + state.getProvider().validForClient(state));

        return clientHandler.getPlayerOption(player).isClientSideSelfView()
                && state.getProvider().validForClient(state);
    }

    public void setSelfDisguiseVisible(Player player, boolean value, boolean saveToConfig, boolean dontSetServerSide, boolean noClientCommand)
    {
        var state = getDisguiseStateFor(player);
        var config = data.getPlayerConfiguration(player);

        if (state != null)
        {
            //????????????????????????????????????????????????????????????
            if (!dontSetServerSide && !clientViewAvailable(player))
                state.setServerSideSelfVisible(value);
        }

        if (!noClientCommand)
            clientHandler.sendClientCommand(player, new S2CSetToggleSelfCommand(value));

        if (saveToConfig)
        {
            player.sendMessage(MessageUtils.prefixes(player, value
                    ? MorphStrings.selfVisibleOnString()
                    : MorphStrings.selfVisibleOffString()));

            config.showDisguiseToSelf = value;
        }
    }

    /**
     * ?????????????????????????????????
     *
     * @param player ????????????
     * @return ????????????????????????null???????????????????????????????????????
     */
    @Nullable
    public DisguiseState getDisguiseStateFor(Player player)
    {
        return this.disguisedPlayers.stream()
                .filter(i -> i.getPlayerUniqueID().equals(player.getUniqueId()))
                .findFirst().orElse(null);
    }

    public DisguiseState getDisguiseStateFor(Entity entity)
    {
        if (!(entity instanceof Player player)) return null;

        return getDisguiseStateFor(player);
    }

    public void onPluginDisable()
    {
        getDisguisedPlayers().forEach(s ->
        {
            var player = s.getPlayer();

            player.sendMessage(MessageUtils.prefixes(player, MorphStrings.resetString()));
            if (!player.isOnline())
                offlineStorage.pushDisguiseState(s);
        });

        unMorphAll(false);
        saveConfiguration();

        offlineStorage.saveConfiguration();
    }

    public OfflineDisguiseState getOfflineState(Player player)
    {
        return offlineStorage.popDisguiseState(player.getUniqueId());
    }

    public List<OfflineDisguiseState> getAvaliableOfflineStates()
    {
        return offlineStorage.getAvaliableDisguiseStates();
    }

    private void disguiseFromState(DisguiseState state)
    {
        if (!disguisedPlayers.contains(state))
            disguisedPlayers.add(state);

        DisguiseAPI.disguiseEntity(state.getPlayer(), state.getDisguise());
        postConstructDisguise(state);
    }

    public boolean disguiseFromOfflineState(Player player, OfflineDisguiseState offlineState)
    {
        if (player.getUniqueId() == offlineState.playerUUID)
        {
            logger.error("??????UUID???OfflineState???UUID?????????: " + player.getUniqueId() + " :: " + offlineState.playerUUID);
            return false;
        }

        var key = offlineState.disguiseID;

        if (disguiseDisabled(key) || !getPlayerConfiguration(player).getUnlockedDisguiseIdentifiers().contains(key))
            return false;

        var disguiseType = DisguiseTypes.fromId(key);

        if (disguiseType == DisguiseTypes.UNKNOWN) return false;

        //???????????????LD?????????
        if (offlineState.disguise != null && disguiseType != DisguiseTypes.LD)
        {
            DisguiseUtils.addTrace(offlineState.disguise);

            var state = DisguiseState.fromOfflineState(offlineState, data.getPlayerConfiguration(player));

            this.disguiseFromState(state);
            return true;
        }

        //????????????
        morph(player, key, null);
        return true;
    }

    //endregion ??????????????????

    @Resolved
    private MorphClientHandler clientHandler;

    //region Implementation of IManagePlayerData

    @Override
    @Nullable
    public DisguiseInfo getDisguiseInfo(String rawString)
    {
        return data.getDisguiseInfo(rawString);
    }

    @Override
    public ObjectArrayList<DisguiseInfo> getAvaliableDisguisesFor(Player player)
    {
        return data.getAvaliableDisguisesFor(player);
    }

    @Override
    public boolean grantMorphToPlayer(Player player, String disguiseIdentifier)
    {
        var success = data.grantMorphToPlayer(player, disguiseIdentifier);

        if (success)
        {
            clientHandler.sendDiff(List.of(disguiseIdentifier), null, player);

            var config = data.getPlayerConfiguration(player);

            if (clientHandler.clientConnected(player))
            {
                if (!config.shownMorphClientHint)
                {
                    player.sendMessage(MessageUtils.prefixes(player, HintStrings.firstGrantClientHintString()));
                    config.shownMorphClientHint = true;
                }
            }
            else if (!config.shownMorphHint)
            {
                player.sendMessage(MessageUtils.prefixes(player, HintStrings.firstGrantHintString()));
                config.shownMorphHint = true;
            }
        }

        return success;
    }

    @Override
    public boolean revokeMorphFromPlayer(Player player, String disguiseIdentifier)
    {
        var success = data.revokeMorphFromPlayer(player, disguiseIdentifier);

        if (success)
            clientHandler.sendDiff(null, List.of(disguiseIdentifier), player);

        return success;
    }

    @Override
    public PlayerMorphConfiguration getPlayerConfiguration(Player player)
    {
        return data.getPlayerConfiguration(player);
    }

    @Override
    public boolean reloadConfiguration()
    {
        //?????????????????????????????????????????????
        var stateToOfflineStore = new ObjectArrayList<DisguiseState>();

        disguisedPlayers.forEach(s ->
        {
            if (!s.getPlayer().isOnline())
                stateToOfflineStore.add(s);
        });

        disguisedPlayers.removeAll(stateToOfflineStore);
        var stateToRecover = getDisguisedPlayers();

        unMorphAll(false);

        var success = data.reloadConfiguration() && offlineStorage.reloadConfiguration();

        stateToOfflineStore.forEach(offlineStorage::pushDisguiseState);

        //?????????????????????????????????
        stateToRecover.forEach(s ->
        {
            var player = s.getPlayer();
            var config = this.getPlayerConfiguration(player);

            if (!disguiseDisabled(s.getDisguiseIdentifier()) && config.getUnlockedDisguiseIdentifiers().contains(s.getDisguiseIdentifier()))
            {
                var newState = s.createCopy();

                disguiseFromState(newState);
                postConstructDisguise(newState);
                refreshClientState(newState);

                player.sendMessage(MessageUtils.prefixes(player, MorphStrings.recoverString()));
            }
            else
                unMorph(player, true);
        });

        Bukkit.getOnlinePlayers().forEach(p -> clientHandler.refreshPlayerClientMorphs(this.getPlayerConfiguration(p).getUnlockedDisguiseIdentifiers(), p));

        return success;
    }

    @Override
    public boolean saveConfiguration()
    {
        return data.saveConfiguration() && offlineStorage.saveConfiguration();
    }
    //endregion Implementation of IManagePlayerData
}