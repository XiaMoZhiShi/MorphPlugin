package xiamomc.morph.events;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import me.libraryaddict.disguise.DisguiseAPI;
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
import xiamomc.morph.MorphManager;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.commands.MorphCommandHelper;
import xiamomc.morph.config.ConfigOption;
import xiamomc.morph.config.MorphConfigManager;
import xiamomc.morph.messages.*;
import xiamomc.morph.messages.vanilla.VanillaMessageStore;
import xiamomc.morph.misc.DisguiseTypes;
import xiamomc.morph.misc.DisguiseUtils;
import xiamomc.morph.misc.EntityTypeUtils;
import xiamomc.morph.network.MorphClientHandler;
import xiamomc.morph.network.commands.S2C.S2CSetEquipCommand;
import xiamomc.morph.network.commands.S2C.S2CSwapCommand;
import xiamomc.morph.skills.MorphSkillHandler;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;

import static xiamomc.morph.misc.DisguiseUtils.itemOrAir;

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

    @Resolved(shouldSolveImmediately = true)
    private VanillaMessageStore vanillaMessageStore;


    private Bindable<Boolean> unMorphOnDeath;

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e)
    {
        var entity = e.getEntity();
        var killer = entity.getKiller();

        //logger.warn(entity + "died by:" + killer);

        //????????????????????????????????????
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

        //???????????????????????????
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
        if (unMorphOnDeath.get())
            morphs.unMorph(e.getPlayer(), true);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerPostRespawnEvent e)
    {
        var player = e.getPlayer();

        var state = morphs.getDisguiseStateFor(e.getPlayer());
        if (state != null)
        {
            state.getAbilities().forEach(a -> a.applyToPlayer(player, state));

            var skill = state.getSkill();
            if (skill != null)
                skill.onInitialEquip(state);
        }
    }

    private final Bindable<Integer> cooldownOnDamage = new Bindable<>(0);
    private final Bindable<Boolean> bruteIgnoreDisguises = new Bindable<>(true);

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerTookDamage(EntityDamageEvent e)
    {
        if (e.getEntity() instanceof Player player)
        {
            var state = morphs.getDisguiseStateFor(player);

            if (state != null)
            {
                //???????????????0?????????????????????
                if (e.getDamage() > 0d)
                    state.setSkillCooldown(Math.max(state.getSkillCooldown(), cooldownOnDamage.get()));
            }
        }
    }

    @Initializer
    private void load()
    {
        config.bind(cooldownOnDamage, ConfigOption.SKILL_COOLDOWN_ON_DAMAGE);
        config.bind(bruteIgnoreDisguises, ConfigOption.PIGLIN_BRUTE_IGNORE_DISGUISES);

        unMorphOnDeath = config.getBindable(Boolean.class, ConfigOption.UNMORPH_ON_DEATH);

        var actionItemId = config.getBindable(String.class, ConfigOption.ACTION_ITEM);
        actionItemId.onValueChanged((o, n) ->
        {
            var item = Material.matchMaterial(n);
            var disabled = "disabled";

            if (item == null || disabled.equals(n))
                logger.warn("???????????????" + actionItem + "?????????????????????????????????????????????");

            actionItem = item;
        }, true);
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent e)
    {
        //workaround: ??????????????????????????????????????????????????????
        if (e.getRightClicked() instanceof Player clickedPlayer)
        {
            var state = morphs.getDisguiseStateFor(clickedPlayer);

            if (state != null && state.getEntityType() == EntityType.ALLAY)
                e.setCancelled(true);
        }

        //workaround: ????????????????????????????????????????????????InteractAtEntityEvent
        if (e.getRightClicked() instanceof ArmorStand)
            e.setCancelled(tryInvokeSkillOrQuickDisguise(e.getPlayer(), Action.RIGHT_CLICK_AIR, e.getHand()) || e.isCancelled());
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e)
    {
        //workaround: ???????????????InventoryHolder?????????????????????????????????????????????????????????
        if (e.getRightClicked() instanceof InventoryHolder && e.getRightClicked().getType() != EntityType.PLAYER)
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
     * ?????????????????????????????????
     * @param player ????????????
     * @param action ??????
     * @return ??????????????????Interact??????
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
            //?????????????????????????????????
            if (!action.equals(Action.RIGHT_CLICK_BLOCK) && !action.isLeftClick() && morphs.doQuickDisguise(player, actionItem))
            {
                return true;
            }
            else if (mainHandItemType == actionItem)
            {
                //???????????????????????????
                if (state != null)
                {
                    if (action.isLeftClick())
                    {
                        if (player.getEyeLocation().getDirection().getY() <= -0.95)
                            morphs.unMorph(player);
                        else
                            morphs.setSelfDisguiseVisible(player, state.getServerSideSelfVisible(), true);

                        return true;
                    }

                    if (state.getSkillCooldown() <= 0)
                        morphs.executeDisguiseSkill(player);
                    else
                    {
                        //?????????????????????????????????????????????
                        //???????????????????????????Interact??????????????????Interact?????????????????????Tick???
                        if (plugin.getCurrentTick() - skillHandler.getLastInvoke(player) <= 1)
                            return true;

                        player.sendMessage(MessageUtils.prefixes(player,
                                SkillStrings.skillPreparing().resolve("time", state.getSkillCooldown() / 20 + "")));

                        player.playSound(Sound.sound(Key.key("minecraft", "entity.villager.no"),
                                Sound.Source.PLAYER, 1f, 1f));
                    }

                    return true;
                }
            }
        }

        return false;
    }

    //region LibsDisguises workaround

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent e)
    {
        var player = e.getPlayer();
        var state = morphs.getDisguiseStateFor(player);

        if (state != null)
        {
            //workaround: ???????????????????????????????????????????????????desync
            if (state.getServerSideSelfVisible())
            {
                this.addSchedule(() ->
                {
                    if (DisguiseAPI.isDisguised(player) && DisguiseAPI.isSelfDisguised(player))
                        player.updateInventory();
                }, 2);
            }

            //workaround: ???????????????????????????????????????????????????????????????
            if (state.showingDisguisedItems())
            {
                var disguise = state.getDisguise();
                state.swapHands();
                var equip = state.getDisguisedItems();

                var mainHand = itemOrAir(equip.getItemInMainHand());
                var offHand = itemOrAir(equip.getItemInOffHand());

                if (clientHandler.clientVersionCheck(player, 3))
                {
                    clientHandler.sendClientCommand(player, new S2CSwapCommand());
                }
                else
                {
                    clientHandler.sendClientCommand(player, new S2CSetEquipCommand(mainHand, EquipmentSlot.HAND));
                    clientHandler.sendClientCommand(player, new S2CSetEquipCommand(offHand, EquipmentSlot.OFF_HAND));
                }

                this.addSchedule(() ->
                {
                    if (!state.showingDisguisedItems() || state.getDisguise() != disguise) return;

                    var watcher = state.getDisguise().getWatcher();

                    var air = itemOrAir(null);
                    watcher.setItemInMainHand(air);
                    watcher.setItemInOffHand(air);

                    watcher.setItemInMainHand(mainHand);
                    watcher.setItemInOffHand(offHand);
                }, 2);
            }
        }
    }

    //???Premium?????????LibsDisguises???????????????????????????
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e)
    {
        var player = e.getPlayer();
        var state = morphs.getDisguiseStateFor(player);

        clientHandler.markPlayerReady(player);

        this.addSchedule(() ->
        {
            if (player.isOnline())
                vanillaMessageStore.getOrCreateSubStore(MessageUtils.getLocale(player));
        }, 3);

        if (clientHandler.clientConnected(player))
        {
            var config = morphs.getPlayerConfiguration(player);

            if (!config.shownMorphClientHint && config.getUnlockedDisguiseIdentifiers().size() > 0)
                this.addSchedule(() ->
                {
                    if (player.isOnline() && !config.shownMorphClientHint)
                    {
                        player.sendMessage(MessageUtils.prefixes(player, HintStrings.firstGrantClientHintString()));

                        config.shownMorphClientHint = true;
                    }
                }, 20 * 3);
        }

        if (state != null)
        {
            //???????????????player???info.player???????????????????????????????????????disguise
            state.setPlayer(player);
            DisguiseAPI.disguiseEntity(player, state.getDisguise());

            var disguise = DisguiseAPI.getDisguise(player);
            DisguiseUtils.addTrace(disguise);

            //??????Disguise
            var nbt = state.getCachedNbtString();
            var profile = state.getProfileNbtString();

            state.setDisguise(state.getDisguiseIdentifier(),
                    state.getSkillLookupIdentifier(), DisguiseAPI.getDisguise(player), state.shouldHandlePose(), false,
                    state.getDisguisedItems());

            state.setCachedNbtString(nbt);
            state.setCachedProfileNbtString(profile);

            //????????????
            var abilities = state.getAbilities();
            abilities.forEach(a -> a.applyToPlayer(state.getPlayer(), state));

            //????????????
            var skill = state.getSkill();

            if (skill != null)
                skill.onInitialEquip(state);

            //??????Morph??????
            Bukkit.getPluginManager().callEvent(new PlayerMorphEvent(player, state));

            return;
        }

        var offlineState = morphs.getOfflineState(player);

        if (offlineState == null && DisguiseAPI.isDisguised(player))
        {
            //?????????????????????????????????????????????????????????
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

    @Resolved
    private MorphClientHandler clientHandler;

    @EventHandler
    public void onPlayerExit(PlayerQuitEvent e)
    {
        clientHandler.unInitializePlayer(e.getPlayer());
        skillHandler.removeUnusedList(e.getPlayer());

        var state = morphs.getDisguiseStateFor(e.getPlayer());

        if (state != null)
        {
            var bossbar = state.getBossbar();

            if (bossbar != null)
                Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossbar));
        }
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent e)
    {
        var player = e.getPlayer();
        var state = morphs.getDisguiseStateFor(player);

        if (state != null)
        {
            //????????????
            var skill = state.getSkill();

            if (skill != null)
                skill.onInitialEquip(state);

            //????????????
            var abilities = state.getAbilities();

            if (abilities != null)
                abilities.forEach(a -> a.applyToPlayer(player, state));
        }
    }

    //??????LibsDisguises???MonstersIgnoreDisguises?????????PlayerDisguise?????????
    @EventHandler
    public void onEntityTarget(EntityTargetEvent e)
    {
        if (e.getTarget() == null) return;

        if (e.getEntity().getType() == EntityType.PIGLIN_BRUTE && bruteIgnoreDisguises.get())
            return;

        if (e.getTarget() instanceof Player player && !e.getEntity().getType().equals(EntityType.WARDEN))
        {
            //???????????????????????????????????????????????????
            switch (e.getReason())
            {
                case TARGET_ATTACKED_ENTITY:
                case TARGET_ATTACKED_NEARBY_ENTITY:
                case REINFORCEMENT_TARGET:
                case FOLLOW_LEADER:
                case TARGET_ATTACKED_OWNER:
                case OWNER_ATTACKED_TARGET:
                case CUSTOM:
                    return;

                default:
                    break;
            }

            //???????????????????????????????????????
            if (!DisguiseAPI.isDisguised(player)) return;

            var disguise = DisguiseAPI.getDisguise(player);

            var sourceEntityType = e.getEntity().getType();
            var disguiseEntityType = disguise.getType().getEntityType();

            //?????????????????????Target
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
