package xiamomc.morph.events;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import me.libraryaddict.disguise.DisguiseAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.config.ConfigOption;
import xiamomc.morph.config.MorphConfigManager;
import xiamomc.morph.misc.DisguiseTypes;
import xiamomc.morph.misc.PlayerOperationSimulator;
import xiamomc.morph.misc.permissions.CommonPermissions;
import xiamomc.morph.network.MorphClientHandler;
import xiamomc.morph.network.commands.S2C.S2CSetSneakingCommand;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReverseControlProcessor extends MorphPluginObject implements Listener
{
    private final Map<Player, String> uuidDisguiseStateMap = new ConcurrentHashMap<>();

    @Resolved
    private MorphClientHandler clientHandler;

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent e)
    {
        if (!allowSneak.get()) return;

        var state = uuidDisguiseStateMap.get(e.getPlayer());

        if (state != null)
        {
            var player = Bukkit.getPlayerExact(state);

            if (!playerInDistance(e.getPlayer(), player) || player.isSneaking() == e.isSneaking()) return;

            player.setSneaking(e.isSneaking());
            clientHandler.sendClientCommand(player, new S2CSetSneakingCommand(e.isSneaking()));
        }
    }

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent e)
    {
        if (!allowSwap.get()) return;

        var state = uuidDisguiseStateMap.get(e.getPlayer());

        if (state != null)
        {
            var player = Bukkit.getPlayerExact(state);

            if (!playerInDistance(e.getPlayer(), player)) return;

            var equipment = player.getEquipment();

            var mainHandItem = equipment.getItemInMainHand();
            var offhandItem = equipment.getItemInOffHand();

            equipment.setItemInMainHand(offhandItem);
            equipment.setItemInOffHand(mainHandItem);
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent e)
    {
        if (!allowDrop.get()) return;

        var player = e.getPlayer();
        var state = uuidDisguiseStateMap.get(e.getPlayer());

        if (state != null)
        {
            var target = Bukkit.getPlayerExact(state);

            if (!playerInDistance(player, target) || !player.isSneaking()) return;

            if (!target.getEquipment().getItemInMainHand().getType().isAir())
            {
                target.dropItem(false);
                target.swingHand(EquipmentSlot.HAND);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHotbarChange(PlayerItemHeldEvent e)
    {
        if (!allowHotBar.get()) return;

        var state = uuidDisguiseStateMap.get(e.getPlayer());

        if (state != null)
        {
            var player = Bukkit.getPlayerExact(state);

            if (!playerInDistance(e.getPlayer(), player)) return;

            player.getInventory().setHeldItemSlot(e.getNewSlot());
        }
    }

    @Resolved(shouldSolveImmediately = true)
    private MorphConfigManager config;

    @EventHandler
    public void onPlayerExit(PlayerQuitEvent e)
    {
        uuidDisguiseStateMap.remove(e.getPlayer());
    }

    @EventHandler
    public void onPlayerHurtPlayer(EntityDamageByEntityEvent e)
    {
        if (!allowSimulation.get()) return;

        if (e.getDamager() instanceof Player damager)
        {
            var state = uuidDisguiseStateMap.get(damager);

            if (state != null)
            {
                var targetPlayer = Bukkit.getPlayerExact(state);

                if (!playerInDistance(damager, targetPlayer)) return;

                //??????????????????????????????????????????????????????????????????
                if (e.getEntity() instanceof Player hurtedPlayer && hurtedPlayer.equals(targetPlayer))
                {
                    simulateOperation(Action.LEFT_CLICK_AIR, targetPlayer);

                    e.setCancelled(true);

                    return;
                }

                var damagerLookingAt = damager.getTargetEntity(3);
                var playerLookingAt = targetPlayer.getTargetEntity(3);

                //????????????????????????????????????????????????????????????????????????????????????????????????
                if (damagerLookingAt != null && damagerLookingAt.equals(playerLookingAt))
                {
                    simulateOperation(Action.LEFT_CLICK_AIR, targetPlayer);

                    e.setCancelled(true);
                }
            }
        }
    }

    @Resolved
    private PlayerTracker tracker;

    @EventHandler
    public void onPlayerSwing(PlayerArmSwingEvent e)
    {
        if (!allowSimulation.get()) return;

        var player = e.getPlayer();
        var state = uuidDisguiseStateMap.get(player);

        if (state != null)
        {
            var targetPlayer = Bukkit.getPlayerExact(state);

            if (!playerInDistance(player, targetPlayer)) return;

            var lastAction = tracker.getLastInteractAction(player);

            if (lastAction == null) return;

            //??????????????????????????????????????????Interact????????????????????????????????????????????????????????????
            if (player.getGameMode() == GameMode.SPECTATOR)
            {
                if (lastAction.isRightClick())
                    lastAction = PlayerTracker.InteractType.LEFT_CLICK_BLOCK;
            }

            //??????????????????
            if (!tracker.interactingThisTick(player))
                simulateOperation(lastAction.toBukkitAction(), targetPlayer);

            //???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            if (targetPlayer.getLocation().getWorld() == player.getLocation().getWorld()
                && Math.abs(targetPlayer.getLocation().distanceSquared(player.getLocation())) <= 6)
            {
                var theirTarget = targetPlayer.getTargetEntity(3);
                var ourTarget = player.getTargetEntity(3);

                if (theirTarget != null
                        && (ourTarget == null || ourTarget == targetPlayer || ourTarget == theirTarget))
                    e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e)
    {
        if (e.getHand() == EquipmentSlot.HAND)
        {
            var player = e.getPlayer();
            var state = uuidDisguiseStateMap.get(player);

            if (state != null)
            {
                var targetPlayer = Bukkit.getPlayerExact(state);

                if (!playerInDistance(player, targetPlayer)) return;

                simulateOperation(e.getAction(), targetPlayer);
            }
        }
    }

    @Resolved
    private PlayerOperationSimulator operationSimulator;

    /**
     * ??????????????????
     *
     * @param action ????????????
     * @param targetPlayer ????????????
     * @return ??????????????????
     */
    private boolean simulateOperation(Action action, Player targetPlayer)
    {
        if (!allowSimulation.get()) return false;

        var result = action.isRightClick()
                ? operationSimulator.simulateRightClick(targetPlayer)
                : operationSimulator.simulateLeftClick(targetPlayer);

        if (result.success())
        {
            targetPlayer.swingHand(result.hand());
            return true;
        }

        return false;
    }

    private boolean playerInDistance(@NotNull Player source, @Nullable Player target)
    {
        if (target == null || (DisguiseAPI.isDisguised(target) && ignoreDisguised.get())) return false;

        if (!source.hasPermission(CommonPermissions.REVERSE)) return false;

        var isInSameWorld = target.getWorld().equals(source.getWorld());
        var targetHelmet = target.getEquipment().getHelmet();

        //-1: ???????????????0: ??????
        if (targetHelmet != null && immuneItemMaterial != null && targetHelmet.getType().equals(immuneItemMaterial))
        {
            var immuneDistance = config.getOrDefault(Integer.class, ConfigOption.REVERSE_CONTROL_DISTANCE_IMMUNE);

            //immuneDistance???-1?????????????????????0?????????
            return immuneDistance == -1
                    || (immuneDistance != 0 && isInSameWorld && target.getLocation().distance(source.getLocation()) <= immuneDistance);
        }
        else
        {
            var normalDistance = config.getOrDefault(Integer.class, ConfigOption.REVERSE_CONTROL_DISTANCE);

            //normalDistance???-1?????????????????????0?????????
            return normalDistance == -1
                    || (normalDistance != 0 && isInSameWorld && target.getLocation().distance(source.getLocation()) <= normalDistance);
        }
    }

    private Material immuneItemMaterial;

    private final Bindable<Boolean> allowSimulation = new Bindable<>(false);
    private final Bindable<Boolean> allowSneak = new Bindable<>(false);
    private final Bindable<Boolean> allowSwap = new Bindable<>(false);
    private final Bindable<Boolean> allowDrop = new Bindable<>(false);
    private final Bindable<Boolean> allowHotBar = new Bindable<>(false);
    private final Bindable<Boolean> ignoreDisguised = new Bindable<>(false);

    @Initializer
    private void load()
    {
        this.addSchedule(this::update);

        var immuneItem = config.getBindable(String.class, ConfigOption.REVERSE_CONTROL_IMMUNE_ITEM);
        immuneItem.onValueChanged((o, immune) ->
        {
            var targetOptional = Material.matchMaterial(immune);

            if (targetOptional == null)
                logger.warn("???????????????" + immune + "???????????????????????????????????????????????????");

            immuneItemMaterial = targetOptional;
        }, true);

        config.bind(allowSimulation, ConfigOption.REVERSE_BEHAVIOR_DO_SIMULATION);
        config.bind(allowSneak, ConfigOption.REVERSE_BEHAVIOR_SNEAK);
        config.bind(allowSwap, ConfigOption.REVERSE_BEHAVIOR_SWAP_HAND);
        config.bind(allowDrop, ConfigOption.REVERSE_BEHAVIOR_DROP);
        config.bind(allowHotBar, ConfigOption.REVERSE_BEHAVIOR_HOTBAR);
        config.bind(ignoreDisguised, ConfigOption.REVERSE_IGNORE_DISGUISED);
    }

    private void update()
    {
        /*

        uuidDisguiseStateMap.forEach((p, d) ->
        {
            var targetPlayer = Bukkit.getPlayerExact(d.getName());

            if (targetPlayer != null)
            {
                targetPlayer.setHealthScale(p.getHealthScale());
                targetPlayer.setFireTicks(p.getFireTicks());
                targetPlayer.setGlowing(p.isGlowing());
                targetPlayer.setGliding(p.isGliding());
                targetPlayer.setSwimming(p.isSwimming());
                targetPlayer.setSprinting(p.isSprinting());
            }
        });

        */

        this.addSchedule(this::update);
    }

    @EventHandler
    public void onPlayerMorph(PlayerMorphEvent e)
    {
        var state = e.getState();
        var id = state.getDisguiseIdentifier();

        if (DisguiseTypes.fromId(id) == DisguiseTypes.PLAYER)
            uuidDisguiseStateMap.put(e.getPlayer(), DisguiseTypes.PLAYER.toStrippedId(id));
        else
            uuidDisguiseStateMap.remove(e.getPlayer());
    }

    @EventHandler
    public void onPlayerUnMorph(PlayerUnMorphEvent e)
    {
        uuidDisguiseStateMap.remove(e.getPlayer());
    }
}
