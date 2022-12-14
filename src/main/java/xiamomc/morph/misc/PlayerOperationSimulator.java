package xiamomc.morph.misc;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R2.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.config.ConfigOption;
import xiamomc.morph.config.MorphConfigManager;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Bindables.Bindable;

import java.util.Map;

public class PlayerOperationSimulator extends MorphPluginObject
{
    private final Map<Player, BlockDestroyHandler> playerHandlerMap = new Object2ObjectOpenHashMap<>();

    @Initializer
    private void load(MorphConfigManager config)
    {
        this.addSchedule(this::update);

        config.bind(destroyTimeout, ConfigOption.REVERSE_DESTROY_TIMEOUT);
    }

    private final Bindable<Integer> destroyTimeout = new Bindable<>(40);

    private void update()
    {
        var list = new Object2ObjectOpenHashMap<>(playerHandlerMap);

        var currentTick = plugin.getCurrentTick();

        list.forEach((p, i) ->
        {
            if (!p.isOnline())
            {
                playerHandlerMap.remove(p);
                return;
            }

            //??????????????????Info
            if (currentTick - i.getLastUpdate() >= destroyTimeout.get())
            {
                i.setProgress(-1, currentTick);

                playerHandlerMap.remove(p);
            }
        });

        this.addSchedule(this::update, 5);
    }

    private final float blockReachDistance = 4.5f;
    private final float entityReachDistance = 3f;

    private RayTraceResult rayTrace(Player player)
    {
        var eyeLoc = player.getEyeLocation();

        return player.getWorld().rayTrace(eyeLoc, eyeLoc.getDirection(), blockReachDistance,
                FluidCollisionMode.NEVER, false, 0d, e ->
                {
                    if (e instanceof Player p)
                        return e != player && p.getGameMode() != GameMode.SPECTATOR;
                    else
                        return true;
                });
    }

    /**
     * ??????????????????
     *
     * @param player ????????????
     * @return ??????????????????
     */
    public SimulateResult simulateLeftClick(Player player)
    {
        //logger.warn("???????????????");
        //Thread.dumpStack();

        //???????????????????????????
        if (player.isBlocking()) return SimulateResult.fail();

        //?????????????????????????????????
        var traceResult = this.rayTrace(player);

        var targetBlock = traceResult == null ? null : traceResult.getHitBlock();
        var targetEntity = traceResult == null ? null : traceResult.getHitEntity();

        if (targetEntity != null && targetEntity.getLocation().distance(player.getLocation()) > entityReachDistance)
            targetEntity = null;

        //????????????????????????
        var destroyHandler = playerHandlerMap.getOrDefault(player, null);

        if ((targetBlock == null || targetEntity != null) && destroyHandler != null)
            destroyHandler.changeBlock(null);

        if (targetEntity != null)
        {
            if (player.getLocation().distance(targetEntity.getLocation()) > entityReachDistance)
                return SimulateResult.success(EquipmentSlot.HAND);

            player.attack(targetEntity);

            return SimulateResult.success(EquipmentSlot.HAND);
        }

        //??????????????????
        if (targetBlock == null)
            return SimulateResult.success(EquipmentSlot.HAND);

        //????????????????????????????????????????????? -> ????????????(??????)
        if (player.getGameMode() == GameMode.ADVENTURE)
        {
            var meta = player.getEquipment().getItemInMainHand().getItemMeta();
            if (meta == null || !meta.getDestroyableKeys().contains(targetBlock.getBlockData().getMaterial().getKey()))
                return SimulateResult.success(EquipmentSlot.HAND);
        }

        //?????????destoryInfo
        if (destroyHandler == null)
        {
            destroyHandler = new BlockDestroyHandler(targetBlock, 0, player);
            playerHandlerMap.put(player, destroyHandler);
        }

        //????????????
        if (!targetBlock.equals(destroyHandler.getBlock()))
            destroyHandler.changeBlock(targetBlock);

        //NMS
        var nmsWorld = destroyHandler.getNmsWorld();
        var nmsBlock = destroyHandler.getNmsBlock();
        var nmsPlayer = ((CraftPlayer) player).getHandle();

        assert nmsBlock != null;
        assert nmsWorld != null;

        //AbstractBlockState#onBlockBreakStart(World world, BlockPos pos, PlayerEntity player)
        nmsBlock.attack(nmsWorld, ((CraftBlock) targetBlock).getPosition(), nmsPlayer);

        //AbstractBlockState#calcBlockBreakingDelta(PlayerEntity player, BlockView world, BlockPos pos)
        var delta = nmsBlock.getDestroyProgress(nmsPlayer, nmsWorld, ((CraftBlock) targetBlock).getPosition());

        //??????????????????
        var val = destroyHandler.getProgress();
        val += delta;

        destroyHandler.setProgress(val, plugin.getCurrentTick());

        return SimulateResult.success(EquipmentSlot.HAND);
    }

    /**
     * ??????????????????
     *
     * @param player ????????????
     * @return ??????????????????
     */
    public SimulateResult simulateRightClick(Player player)
    {
        //logger.warn("?????????????????????");

        //?????????????????????????????????
        var traceResult = this.rayTrace(player);

        var targetBlock = traceResult == null ? null : traceResult.getHitBlock();
        var targetEntity = traceResult == null ? null : traceResult.getHitEntity();

        if (targetEntity != null && targetEntity.getLocation().distance(player.getLocation()) > entityReachDistance)
            targetEntity = null;

        var itemInMainHand = player.getEquipment().getItem(EquipmentSlot.HAND);
        var itemInOffHand = player.getEquipment().getItem(EquipmentSlot.OFF_HAND);

        //????????????????????????null?????????????????????
        if (targetEntity != null)
        {
            //??????????????????
            var hitPos = traceResult.getHitPosition();

            //??????????????????
            hitPos.subtract(targetEntity.getLocation().toVector());

            if (this.tryUseItemOnEntity(player, targetEntity, itemInMainHand, InteractionHand.MAIN_HAND, hitPos))
                return SimulateResult.success(EquipmentSlot.HAND);

            return SimulateResult.of(this.tryUseItemOnEntity(player, targetEntity, itemInOffHand, InteractionHand.OFF_HAND, hitPos),
                                     EquipmentSlot.OFF_HAND);
        }

        if (targetBlock != null)
        {
            var loc = ((CraftBlock) targetBlock).getPosition();

            Direction targetBlockDirection = null;
            var bukkitFace = player.getTargetBlockFace(5);
            bukkitFace = bukkitFace == null ? BlockFace.SELF : bukkitFace;

            //BlockFace(Bukkit)???BlockFace(Minecraft)
            switch (bukkitFace)
            {
                case DOWN -> targetBlockDirection = Direction.DOWN;
                case UP -> targetBlockDirection = Direction.UP;
                case NORTH -> targetBlockDirection = Direction.NORTH;
                case SOUTH -> targetBlockDirection = Direction.SOUTH;
                case WEST -> targetBlockDirection = Direction.WEST;
                case EAST -> targetBlockDirection = Direction.EAST;
                default -> logger.error("Unknown BlockFace: " + bukkitFace + ", will not attempt 'useItemOn'!");
            }

            if (targetBlockDirection != null)
            {
                var moving = new BlockHitResult(
                        new Vec3(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ()),
                        targetBlockDirection, loc, false);

                if (this.tryUseItemOnBlock(player, targetBlock, bukkitFace, itemInMainHand, InteractionHand.MAIN_HAND, moving))
                {
                    return SimulateResult.success(EquipmentSlot.HAND);
                }
                else if (this.tryUseItemOnBlock(player, targetBlock, bukkitFace, itemInOffHand, InteractionHand.OFF_HAND, moving))
                {
                    return SimulateResult.success(EquipmentSlot.OFF_HAND);
                }
            }
        }

        if (this.tryUseItemOnSelf(player, itemInMainHand, InteractionHand.MAIN_HAND))
            return SimulateResult.success(EquipmentSlot.HAND);
        else if (this.tryUseItemOnSelf(player, itemInOffHand, InteractionHand.OFF_HAND))
            return SimulateResult.success(EquipmentSlot.OFF_HAND);

        return SimulateResult.fail();
    }

    private final PluginManager pluginManager = Bukkit.getPluginManager();

    /**
     * ???????????????????????????????????????
     *
     * @param player ????????????
     * @param bukkitItem ??????
     * @param hand ???????????? {@link InteractionHand}
     * @return ??????????????????
     */
    private boolean tryUseItemOnSelf(Player player, ItemStack bukkitItem, InteractionHand hand)
    {
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, bukkitItem, null, BlockFace.SELF);
        pluginManager.callEvent(event);

        if (event.useItemInHand() != Event.Result.DENY)
        {
            var record = NmsRecord.of(player);
            var manager = record.interactManager;

            //ServerPlayerGameMode.useItem()
            return manager.useItem(record.nmsPlayer, record.nmsWorld, CraftItemStack.asNMSCopy(bukkitItem), hand).consumesAction();
        }

        return false;
    }

    /**
     * ??????????????????????????????????????????????????????
     *
     * @param player ????????????
     * @param targetBlock ????????????
     * @param bukkitFace ??????
     * @param bukkitItem ??????
     * @param nmsHand ???????????? {@link InteractionHand}
     * @param moving ????????????NMS????????? {@link BlockHitResult}
     * @return ??????????????????
     */
    private boolean tryUseItemOnBlock(Player player, Block targetBlock, BlockFace bukkitFace,
                                      ItemStack bukkitItem, InteractionHand nmsHand,
                                      BlockHitResult moving)
    {
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, bukkitItem, targetBlock, bukkitFace);
        pluginManager.callEvent(event);

        if (event.useInteractedBlock() != Event.Result.DENY)
        {
            var record = NmsRecord.of(player);

            ServerPlayerGameMode manager = record.interactManager;

            return manager.useItemOn(record.nmsPlayer, record.nmsWorld, CraftItemStack.asNMSCopy(bukkitItem), nmsHand, moving).consumesAction();
        }

        return false;
    }

    /**
     * ?????????????????????????????????????????????????????????
     *
     * @param player ????????????
     * @param targetEntity ????????????
     * @param bukkitItem ??????
     * @param hand ???????????? {@link InteractionHand}
     * @param hitPos ???????????????{@link PlayerInteractAtEntityEvent}?????????
     * @return ??????????????????
     */
    private boolean tryUseItemOnEntity(Player player, Entity targetEntity, ItemStack bukkitItem, InteractionHand hand, Vector hitPos)
    {
        var interactEntityEvent = new PlayerInteractEntityEvent(player, targetEntity);
        var interactAtEvent = new PlayerInteractAtEntityEvent(player, targetEntity, hitPos);
        pluginManager.callEvent(interactEntityEvent);
        pluginManager.callEvent(interactAtEvent);

        //?????????InteractAt
        //????????????????????????InteractOn
        //????????????useItem
        if (!interactAtEvent.isCancelled() && !interactEntityEvent.isCancelled())
        {
            var record = NmsRecord.of(player, targetEntity);

            //EntityPlayer -> ServerPlayer in mojang mappings
            var playerHandle = record.nmsPlayer;

            //ServerLevel in mojang mappings
            var worldHandle = record.nmsWorld;
            var entityHandle = record.nmsEntity;
            var manager = record.interactManager;

            var vec = new Vec3(hitPos.getX(), hitPos.getY(), hitPos.getZ());

            assert entityHandle != null;
            return entityHandle.interactAt(playerHandle, vec, hand).consumesAction()
                    || playerHandle.interactOn(entityHandle, hand).consumesAction()
                    || manager.useItem(playerHandle, worldHandle, CraftItemStack.asNMSCopy(bukkitItem), hand).consumesAction();
        }

        return false;
    }

    /**
     * ??????????????????
     *
     * @param success ????????????
     * @param hand ??? {@link InteractionHand} ????????? {@link EquipmentSlot}
     */
    public record SimulateResult(boolean success, EquipmentSlot hand)
    {
        public static SimulateResult success(EquipmentSlot hand)
        {
            return of(true, hand);
        }

        public static SimulateResult fail()
        {
            return of(false, null);
        }

        public static SimulateResult of(boolean success, EquipmentSlot hand)
        {
            return new SimulateResult(success, hand);
        }
    }

    /**
     * NMS Record for a player
     *
     * @param nmsPlayer ServerPlayer in fabric mojang mappings
     * @param nmsWorld ServerLevel in fabric mojang mappings
     * @param nmsEntity ??? in fabric mojang mappings
     * @param interactManager GameMode in fabric mojang mappings
     */
    private record NmsRecord(ServerPlayer nmsPlayer, ServerLevel nmsWorld,
                             @Nullable net.minecraft.world.entity.Entity nmsEntity,
                             ServerPlayerGameMode interactManager)
    {
        public static NmsRecord of(Player player)
        {
            var craftPlayer = (CraftPlayer) player;

            return new NmsRecord(craftPlayer.getHandle(), ((CraftWorld) craftPlayer.getWorld()).getHandle(),
                    null, craftPlayer.getHandle().gameMode);
        }

        public static NmsRecord of(Player player, @Nullable Entity targetEntity)
        {
            if (targetEntity == null) return of(player);

            var craftPlayer = (CraftPlayer) player;
            var craftEntity = (CraftEntity) targetEntity;

            return new NmsRecord(craftPlayer.getHandle(), ((CraftWorld) craftPlayer.getWorld()).getHandle(),
                    craftEntity.getHandle(), craftPlayer.getHandle().gameMode);
        }
    }
}
