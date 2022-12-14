package xiamomc.morph.misc;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.EntityPose;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class DisguiseUtils
{
    private static final String customDataTagName = "XIAMO_MORPH";

    public static void addTrace(Disguise disguise)
    {
        disguise.addCustomData(customDataTagName, true);
    }

    public static boolean isTracing(Disguise disguise)
    {
        return Boolean.TRUE.equals(disguise.getCustomData(customDataTagName));
    }

    public static String asString(DisguiseInfo info)
    {
        return info.getKey();
    }

    public static EntityPose toEntityPose(Pose pose)
    {
        return switch (pose)
        {
            case SWIMMING -> EntityPose.SWIMMING;
            case FALL_FLYING -> EntityPose.FALL_FLYING;
            case SNEAKING -> EntityPose.SNEAKING;
            case SLEEPING -> EntityPose.SLEEPING;
            case SPIN_ATTACK -> EntityPose.SPIN_ATTACK;
            case DYING -> EntityPose.DYING;
            default -> EntityPose.STANDING;
        };
    }

    public static boolean validForHeadMorph(Material material)
    {
        return material == Material.DRAGON_HEAD
                || material == Material.PLAYER_HEAD
                || material == Material.ZOMBIE_HEAD
                || material == Material.SKELETON_SKULL
                || material == Material.WITHER_SKELETON_SKULL;
    }

    /**
     * 获取和某一玩家附近多少格以内的所有玩家
     * @param player 目标玩家
     * @param distance 距离
     * @param includeSelf 是否包括自己
     * @return 玩家列表
     */
    public static List<Player> findNearbyPlayers(Player player, int distance, boolean includeSelf)
    {
        var value = new ObjectArrayList<Player>();

        var loc = player.getLocation();

        player.getWorld().getPlayers().forEach(p ->
        {
            if (p.getLocation().distance(loc) <= distance)
                value.add(p);
        });

        if (!includeSelf)
            value.remove(player);

        return value;
    }

    /**
     * 设置伪装的装备
     * @param who 谁
     * @param ourWatcher 自己伪装FlagWatcher
     * @param theirWatcher 他们（who）伪装的FlagWatcher
     */
    public static void tryCopyArmorStack(Player who, FlagWatcher ourWatcher, FlagWatcher theirWatcher)
    {
        ourWatcher.setArmor(DisguiseUtils.getArmorStack(who, theirWatcher));

        var handStack = DisguiseUtils.chooseStack(
                DisguiseUtils.getHandItems(who),
                DisguiseUtils.getHandItems(theirWatcher));

        ourWatcher.setItemInMainHand(handStack[0]);
        ourWatcher.setItemInOffHand(handStack[1]);
    }

    //获取玩家或者伪装的装备
    public static ItemStack[] getArmorStack(Player player, FlagWatcher disguiseWatcher)
    {
        var playerArmorStack = player.getEquipment().getArmorContents();
        var disguiseArmorStack = disguiseWatcher.getArmor();

        var targetStack = chooseStack(playerArmorStack, disguiseArmorStack);

        return new ItemStack[]
                {
                        itemOrAir(targetStack[0]),
                        itemOrAir(targetStack[1]),
                        itemOrAir(targetStack[2]),
                        itemOrAir(targetStack[3])
                };
    }

    public static ItemStack[] chooseStack(ItemStack[] playerStack, ItemStack[] disguiseStack)
    {
        return Arrays.stream(disguiseStack).allMatch(s -> s == null || s.getType().isAir())
                ? playerStack
                : disguiseStack;
    }

    public static ItemStack[] getHandItems(Player player)
    {
        var equipment = player.getEquipment();
        return new ItemStack[]
                {
                        itemOrAir(equipment.getItemInMainHand()),
                        itemOrAir(equipment.getItemInOffHand())
                };
    }

    public static ItemStack[] getHandItems(FlagWatcher watcher)
    {
        var equipment = watcher.getEquipment();
        return new ItemStack[]
                {
                        itemOrAir(equipment.getItemInMainHand()),
                        itemOrAir(equipment.getItemInOffHand())
                };
    }

    public static ItemStack itemOrAir(ItemStack stack)
    {
        return ItemUtils.itemOrAir(stack);
    }
}
