package xiamomc.morph.events;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.reflection.ReflectionManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import xiamomc.morph.MorphManager;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.commands.MorphCommandHelper;
import xiamomc.morph.config.ConfigOption;
import xiamomc.morph.config.MorphConfigManager;
import xiamomc.morph.messages.CommandStrings;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.MorphStrings;
import xiamomc.morph.messages.SkillStrings;
import xiamomc.morph.misc.DisguiseTypes;
import xiamomc.morph.misc.DisguiseUtils;
import xiamomc.morph.misc.EntityTypeUtils;
import xiamomc.morph.misc.MorphGameProfile;
import xiamomc.morph.misc.permissions.CommonPermissions;
import xiamomc.morph.skills.MorphSkillHandler;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommonEventProcessor extends MorphPluginObject implements Listener
{
    @Resolved(shouldSolveImmediately = true)
    private MorphCommandHelper cmdHelper;

    @Resolved(shouldSolveImmediately = true)
    private MorphManager morphs;

    @Resolved(shouldSolveImmediately = true)
    private MorphConfigManager config;

    @Resolved(shouldSolveImmediately = true)
    private MorphSkillHandler skillHandler;

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e)
    {
        var entity = e.getEntity();
        var killer = entity.getKiller();

        //logger.warn(entity + "died by:" + killer);

        //盔甲架需要额外的一些东西
        if (entity.getType() == EntityType.ARMOR_STAND)
        {
            //logger.warn("IS armor stand");
            var lastCause = entity.getLastDamageCause();

            //logger.warn("cause: " + String.valueOf(lastCause));
            if (lastCause instanceof EntityDamageByEntityEvent damageEvent)
            {
                var cause = damageEvent.getDamager();

                //logger.warn("cause entity: " + cause);
                if (cause instanceof Player) killer = (Player) cause;
            }
        }

        //防止获得自己的伪装
        if (killer != null && killer != entity)
            this.onPlayerKillEntity(killer, e.getEntity());
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent e)
    {
        if (e.isCancelled()) return;

        var result = cmdHelper.onTabComplete(e.getBuffer(), e.getSender());
        if (result != null) e.setCompletions(result);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e)
    {
        morphs.unMorph(e.getPlayer());
    }

    private final Bindable<Integer> cooldownOnDamage = new Bindable<>(0);

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerTookDamage(EntityDamageEvent e)
    {
        if (e.getEntity() instanceof Player player)
        {
            var state = morphs.getDisguiseStateFor(player);

            if (state != null)
            {
                //如果伤害是0，那么取消事件
                if (e.getDamage() > 0d)
                    state.setSkillCooldown(Math.max(state.getSkillCooldown(), cooldownOnDamage.get()));
            }
        }
    }

    private boolean allowHeadMorph;
    private void setAllowHeadMorph(boolean val)
    {
        if (allowHeadMorph == val) return;

        allowHeadMorph = val;
        config.set(ConfigOption.ALLOW_HEAD_MORPH, val);
    }

    @Initializer
    private void load()
    {
        config.bind(cooldownOnDamage, ConfigOption.SKILL_COOLDOWN_ON_DAMAGE);

        var allowHeadMorph = config.getBindable(Boolean.class, ConfigOption.ALLOW_HEAD_MORPH);
        allowHeadMorph.onValueChanged((o, n) -> setAllowHeadMorph(n), true);

        var actionItemId = config.getBindable(String.class, ConfigOption.ACTION_ITEM);
        actionItemId.onValueChanged((o, n) ->
        {
            var item = Material.matchMaterial(n);
            var disabled = "disabled";

            if (item == null || disabled.equals(n))
                logger.warn("未能找到和" + actionItem + "对应的物品，相关功能将不会启用");

            actionItem = item;
        }, true);
    }

    @EventHandler
    public void onPlayerUnMorph(PlayerUnMorphEvent e)
    {
        uuidPlayerTexturesMap.remove(e.getPlayer().getUniqueId());
    }

    private final Map<UUID, PlayerTextures> uuidPlayerTexturesMap = new ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent e)
    {
        //workaround: 悦灵伪装右键会导致物品栏失去同步
        if (e.getRightClicked() instanceof Player clickedPlayer)
        {
            var state = morphs.getDisguiseStateFor(clickedPlayer);

            if (state != null && state.getEntityType() == EntityType.ALLAY)
                e.setCancelled(true);
        }

        //workaround: 右键盔甲架不会触发事件、盔甲架是InteractAtEntityEvent
        if (e.getRightClicked() instanceof ArmorStand)
            e.setCancelled(tryInvokeSkillOrQuickDisguise(e.getPlayer(), Action.RIGHT_CLICK_AIR, e.getHand()) || e.isCancelled());
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e)
    {
        //workaround: 右键继承了InventoryHolder的实体会打开他们的物品栏而不是使用技能
        if (e.getRightClicked() instanceof InventoryHolder)
            e.setCancelled(tryInvokeSkillOrQuickDisguise(e.getPlayer(), Action.RIGHT_CLICK_AIR, e.getHand()) || e.isCancelled());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e)
    {
        if (tryInvokeSkillOrQuickDisguise(e.getPlayer(), e.getAction(), e.getHand()))
            e.setCancelled(true);
    }

    private Material actionItem;

    /**
     * 尝试使用技能或快速伪装
     * @param player 目标玩家
     * @param action 动作
     * @return 是否应该取消Interact事件
     */
    private boolean tryInvokeSkillOrQuickDisguise(Player player, Action action, EquipmentSlot slot)
    {
        if (slot != EquipmentSlot.HAND || actionItem == null) return false;

        var state = morphs.getDisguiseStateFor(player);
        var mainHandItem = player.getEquipment().getItemInMainHand();
        var mainHandItemType = mainHandItem.getType();

        if (mainHandItemType.isAir()) return false;

        if (player.isSneaking())
        {
            //右键玩家头颅：快速伪装
            if (DisguiseUtils.validForHeadMorph(mainHandItemType))
            {
                if (!player.hasPermission(CommonPermissions.HEAD_MORPH))
                {
                    player.sendMessage(MessageUtils.prefixes(player, CommandStrings.noPermissionMessage()));

                    return true;
                }

                //忽略shift点地
                if (action.equals(Action.RIGHT_CLICK_BLOCK) || action.isLeftClick()) return false;

                if (!allowHeadMorph)
                {
                    player.sendMessage(MessageUtils.prefixes(player, MorphStrings.headDisguiseDisabledString()));

                    return true;
                }

                if (!morphs.canMorph(player))
                {
                    player.sendMessage(MessageUtils.prefixes(player, MorphStrings.disguiseCoolingDownString()));

                    return true;
                }

                var targetEntity = player.getTargetEntity(5);

                switch (mainHandItemType)
                {
                    case DRAGON_HEAD ->
                    {
                        morphs.morphOrUnMorph(player, EntityType.ENDER_DRAGON.getKey().asString(), targetEntity);
                    }
                    case ZOMBIE_HEAD ->
                    {
                        morphs.morphOrUnMorph(player, EntityType.ZOMBIE.getKey().asString(), targetEntity);
                    }
                    case SKELETON_SKULL ->
                    {
                        morphs.morphOrUnMorph(player, EntityType.SKELETON.getKey().asString(), targetEntity);
                    }
                    case WITHER_SKELETON_SKULL ->
                    {
                        morphs.morphOrUnMorph(player, EntityType.WITHER_SKELETON.getKey().asString(), targetEntity);
                    }
                    case PLAYER_HEAD ->
                    {
                        var profile = ((SkullMeta) mainHandItem.getItemMeta()).getPlayerProfile();

                        //忽略没有profile的玩家伪装
                        if (profile == null)
                        {
                            player.sendMessage(MessageUtils.prefixes(player, MorphStrings.invalidSkinString()));
                            return true;
                        }

                        var name = profile.getName();
                        var profileTexture = profile.getTextures();
                        var playerUniqueId = player.getUniqueId();

                        //如果玩家有伪装，并且伪装的材质和Profile中的一样，那么取消伪装
                        if (state != null)
                        {
                            var disguise = state.getDisguise();

                            if (disguise instanceof PlayerDisguise playerDisguise
                                    && playerDisguise.getName().equals(name)
                                    && profileTexture.equals(uuidPlayerTexturesMap.get(playerUniqueId)))
                            {
                                morphs.unMorph(player);
                                return true;
                            }
                        }

                        //否则，更新或应用伪装
                        if (morphs.morph(player, DisguiseTypes.PLAYER.toId(profile.getName()), targetEntity))
                        {
                            //成功伪装后设置皮肤为头颅的皮肤
                            var disguise = (PlayerDisguise) DisguiseAPI.getDisguise(player);
                            var wrappedProfile = WrappedGameProfile.fromHandle(new MorphGameProfile(profile));

                            var LDprofile = ReflectionManager.getGameProfileWithThisSkin(wrappedProfile.getUUID(), wrappedProfile.getName(), wrappedProfile);

                            //LD不支持直接用profile设置皮肤，只能先存到本地设置完再移除
                            DisguiseAPI.addGameProfile(LDprofile.toString(), LDprofile);
                            disguise.setSkin(LDprofile);
                            DisguiseUtilities.removeGameProfile(LDprofile.toString());

                            uuidPlayerTexturesMap.put(playerUniqueId, profileTexture);
                            return true;
                        }
                    }
                }

                morphs.updateLastPlayerMorphOperationTime(player);
            }
            else if (mainHandItemType == actionItem)
            {
                //主动技能或快速变形
                if (state != null)
                {
                    if (action.isLeftClick())
                    {
                        if (player.getEyeLocation().getDirection().getY() <= -0.95)
                            morphs.unMorph(player);
                        else
                            morphs.setSelfDisguiseVisible(player, !state.getSelfVisible(), true);

                        return true;
                    }

                    if (state.getSkillCooldown() <= 0)
                        morphs.executeDisguiseSkill(player);
                    else
                    {
                        //一段时间内内只接受一次右键触发
                        //传送前后会触发两次Interact，而且这两个Interact还不一定在同个Tick里
                        if (plugin.getCurrentTick() - skillHandler.getLastInvoke(player) <= 1)
                            return true;

                        player.sendMessage(MessageUtils.prefixes(player,
                                SkillStrings.skillPreparing().resolve("time", state.getSkillCooldown() / 20 + "")));

                        player.playSound(Sound.sound(Key.key("minecraft", "entity.villager.no"),
                                Sound.Source.PLAYER, 1f, 1f));
                    }

                    return true;
                }
                else
                {
                    var targetedEntity = player.getTargetEntity(5);

                    if (targetedEntity != null)
                    {
                        var disg = DisguiseAPI.getDisguise(targetedEntity);

                        String targetKey;

                        if (targetedEntity instanceof Player targetPlayer)
                        {
                            var playerState = morphs.getDisguiseStateFor(targetPlayer);

                            //目标实体是玩家：玩家伪装ID > 玩家名
                            targetKey = playerState != null
                                    ? playerState.getDisguiseIdentifier()
                                    : DisguiseTypes.PLAYER.toId(targetPlayer.getName());
                        }
                        else
                        {
                            //否则：伪装ID > 伪装类型 > 生物类型
                            targetKey = disg != null
                                    ? (disg instanceof PlayerDisguise pd)
                                        ? DisguiseTypes.PLAYER.toId(pd.getName())
                                        : disg.getType().getEntityType().getKey().asString()
                                    : targetedEntity.getType().getKey().asString();
                        }

                        morphs.morph(player, targetKey, targetedEntity);

                        return true;
                    }
                }
            }
        }

        return false;
    }

    //region LibsDisguises workaround

    //伪装时副手交换会desync背包
    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent e)
    {
        var player = e.getPlayer();
        if (DisguiseAPI.isDisguised(player))
        {
            //workaround: LibsDisguises在启用selfDisguiseVisible的情况下会导致副手切换异常
            this.addSchedule(c ->
            {
                if (DisguiseAPI.isDisguised(player) && DisguiseAPI.isSelfDisguised(player)) player.updateInventory();
            }, 2);
        }
    }

    //非Premium版本的LibsDisguises不会为玩家保存伪装
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e)
    {
        var player = e.getPlayer();
        var state = morphs.getDisguiseStateFor(player);

        if (state != null)
        {
            //重新进入后player和info.player不属于同一个实例，需要重新disguise
            state.setPlayer(player);
            DisguiseAPI.disguiseEntity(player, state.getDisguise());

            var disguise = DisguiseAPI.getDisguise(player);
            DisguiseUtils.addTrace(disguise);

            //刷新Disguise
            state.setDisguise(state.getDisguiseIdentifier(), state.getSkillIdentifier(), DisguiseAPI.getDisguise(player), state.shouldHandlePose(), false);

            //调用Morph事件
            Bukkit.getPluginManager().callEvent(new PlayerMorphEvent(player, state));

            return;
        }

        var offlineState = morphs.getOfflineState(player);

        if (offlineState == null && DisguiseAPI.isDisguised(player))
        {
            //移除未跟踪，未保存并且属于此插件的伪装
            var disguise = DisguiseAPI.getDisguise(player);

            if (DisguiseUtils.isTracing(disguise))
                disguise.removeDisguise(player);
        }
        else if (offlineState != null)
        {
            player.sendMessage(MessageUtils.prefixes(player, MorphStrings.stateRecoverReasonString()));

            if (morphs.disguiseFromOfflineState(player, offlineState))
            {
                if (offlineState.disguise != null)
                {
                    player.sendMessage(MessageUtils.prefixes(player, MorphStrings.recoveringStateString()));
                }
                else
                {
                    player.sendMessage(MessageUtils.prefixes(player, MorphStrings.recoveringStateLimitedString()));
                    player.sendMessage(MessageUtils.prefixes(player, MorphStrings.recoveringStateLimitedHintString()));
                }
            }
            else
                player.sendMessage(MessageUtils.prefixes(player, MorphStrings.recoveringFailedString()));
        }
    }

    @EventHandler
    public void onPlayerExit(PlayerQuitEvent e)
    {
        skillHandler.removeUnusedList(e.getPlayer());

        var state = morphs.getDisguiseStateFor(e.getPlayer());

        if (state != null)
        {
            var bossbar = state.getBossbar();

            if (bossbar != null)
                Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossbar));
        }
    }

    //解决LibsDisguises中MonstersIgnoreDisguises会忽视PlayerDisguise的问题
    @EventHandler
    public void onEntityTarget(EntityTargetEvent e)
    {
        if (e.getTarget() == null) return;

        if (e.getTarget() instanceof Player player && !e.getEntity().getType().equals(EntityType.WARDEN))
        {
            //受到外力攻击或者其他原因时不要处理
            switch (e.getReason())
            {
                case TARGET_ATTACKED_ENTITY:
                case TARGET_ATTACKED_OWNER:
                case OWNER_ATTACKED_TARGET:
                case CUSTOM:
                    return;

                default:
                    break;
            }

            //目标玩家没在伪装时不要处理
            if (!DisguiseAPI.isDisguised(player)) return;

            var disguise = DisguiseAPI.getDisguise(player);

            var sourceEntityType = e.getEntity().getType();
            var disguiseEntityType = disguise.getType().getEntityType();

            //检查是否要取消Target
            boolean shouldTarget = switch (sourceEntityType)
                    {
                        case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED -> EntityTypeUtils.isZombiesHostile(disguiseEntityType);
                        case SKELETON, STRAY -> EntityTypeUtils.isGolem(disguiseEntityType) || disguise.isPlayerDisguise();
                        case PIGLIN -> EntityTypeUtils.isPiglinHostile(disguiseEntityType);
                        case PIGLIN_BRUTE -> EntityTypeUtils.isBruteHostile(disguiseEntityType);
                        case WITHER_SKELETON -> EntityTypeUtils.isWitherSkeletonHostile(disguiseEntityType);
                        case GUARDIAN, ELDER_GUARDIAN -> EntityTypeUtils.isGuardianHostile(disguiseEntityType);
                        case WITHER -> EntityTypeUtils.isWitherHostile(disguiseEntityType);
                        case PILLAGER, VEX, ILLUSIONER, VINDICATOR, EVOKER, RAVAGER -> EntityTypeUtils.isRaiderHostile(disguiseEntityType);
                        case ENDERMAN -> disguiseEntityType == EntityType.PLAYER || disguiseEntityType == EntityType.ENDERMITE;
                        case ZOGLIN -> EntityTypeUtils.isZoglinHostile(disguiseEntityType);
                        default -> disguise.isPlayerDisguise();
                    };

            e.setCancelled(e.isCancelled() || !shouldTarget);
        }
    }

    //endregion LibsDisguises workaround

    private void onPlayerKillEntity(Player player, Entity entity)
    {
        if (!(entity instanceof LivingEntity) && !(entity.getType() == EntityType.ARMOR_STAND))
            return;

        if (entity instanceof Player targetPlayer)
            morphs.grantMorphToPlayer(player, DisguiseTypes.PLAYER.toId(targetPlayer.getName()));
        else
            morphs.grantMorphToPlayer(player, entity.getType().getKey().asString());
    }
}
