package xiamomc.morph.skills;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Wall;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.SkillStrings;
import xiamomc.morph.misc.DisguiseUtils;
import xiamomc.morph.storage.skill.ISkillOption;

import java.util.List;

public abstract class MorphSkill<T extends ISkillOption> extends MorphPluginObject implements IMorphSkill<T>
{
    protected void playSoundToNearbyPlayers(Player player, int distance, Key key, Sound.Source source)
    {
        var loc = player.getLocation();

        //volume需要根据距离判断
        var sound = Sound.sound(key, source, distance / 8f, 1f);

        player.getWorld().playSound(sound, loc.getX(), loc.getY(), loc.getZ());
    }

    protected List<Player> findNearbyPlayers(Player player, int distance)
    {
        return DisguiseUtils.findNearbyPlayers(player, distance, false);
    }

    protected void sendDenyMessageToPlayer(Player player, Component text)
    {
        player.sendMessage(MessageUtils.prefixes(player, text.color(NamedTextColor.RED)));

        player.playSound(Sound.sound(Key.key("minecraft", "entity.villager.no"),
                Sound.Source.PLAYER, 1f, 1f));
    }

    protected void printErrorMessage(Player player, String message)
    {
        logger.error(message);

        sendDenyMessageToPlayer(player, SkillStrings.exceptionOccurredString()
                .withLocale(MessageUtils.getLocale(player)).toComponent(null));
    }

    /**
     * 向玩家的目标方向发射实体
     * @param player 玩家
     * @param fireball 实体
     * @return 发射的实体，如果为null则发射失败
     * @param <E> 发射出去的实体
     */
    @Nullable
    protected <E extends Entity> E launchProjectile(Player player, EntityType fireball, float multiplier)
    {
        Entity fireBall;
        try
        {
            fireBall = player.getWorld()
                    .spawnEntity(player.getEyeLocation(), fireball, CreatureSpawnEvent.SpawnReason.DEFAULT);
        }
        catch (Throwable t)
        {
            printErrorMessage(player, "未能生成" + fireball + ": " + t.getMessage());
            t.printStackTrace();
            return null;
        }

        if (fireBall instanceof Projectile projectile)
            projectile.setShooter(player);

        fireBall.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2d * multiplier));

        return (E) fireBall;
    }

    protected float getTopY(Block block)
    {
        var data = block.getBlockData();

        var val = block.getBoundingBox().getMinY() + block.getBoundingBox().getHeight();

        if (data instanceof Fence || data instanceof Wall || data instanceof Gate)
            val += 0.5d;

        return (float) Math.max(block.getLocation().getY(), val);
    }
}
