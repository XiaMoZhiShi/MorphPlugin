package xiamomc.morph.skills;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.misc.DisguiseUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class MorphSkill extends MorphPluginObject implements IMorphSkill
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

    protected  <T extends Projectile> T shootFireBall(Player player, Class<T> fireball)
    {
        var fireBall = player.getWorld()
                .spawn(player.getEyeLocation(), fireball);

        fireBall.setShooter(player);

        fireBall.setVelocity(player.getEyeLocation().getDirection().multiply(2));

        return fireBall;
    }
}
