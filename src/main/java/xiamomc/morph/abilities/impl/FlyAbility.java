package xiamomc.morph.abilities.impl;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.jetbrains.annotations.NotNull;
import xiamomc.morph.MorphManager;
import xiamomc.morph.abilities.AbilityType;
import xiamomc.morph.abilities.MorphAbility;
import xiamomc.morph.abilities.options.FlyOption;
import xiamomc.morph.misc.DisguiseState;
import xiamomc.pluginbase.Annotations.Resolved;

public class FlyAbility extends MorphAbility<FlyOption>
{
    @Override
    public @NotNull NamespacedKey getIdentifier()
    {
        return AbilityType.CAN_FLY;
    }

    @Override
    public boolean applyToPlayer(Player player, DisguiseState state)
    {
        super.applyToPlayer(player, state);

        return updateFlyingAbility(state);
    }

    @Override
    public boolean handle(Player player, DisguiseState state)
    {
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR)
        {
            var nmsPlayer = ((CraftPlayer) player).getHandle();
            var config = options.get(state.getDisguiseIdentifier());

            var data = nmsPlayer.getFoodData();
            var allowFlight = data.foodLevel > config.getMinimumHunger();

            if (player.isFlying())
            {
                data.addExhaustion(0.005f * config.getHungerConsumeMultiplier());

                if (!allowFlight)
                {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
            else if (allowFlight && !player.getAllowFlight())
            {
                player.setAllowFlight(true);
            }
        }

        return super.handle(player, state);
    }

    @Override
    public boolean revokeFromPlayer(Player player, DisguiseState state)
    {
        super.revokeFromPlayer(player, state);

        //??????????????????
        var gamemode = player.getGameMode();

        if (gamemode != GameMode.CREATIVE && gamemode != GameMode.SPECTATOR)
            player.setAllowFlight(false);

        player.setFlySpeed(0.1f);

        return true;
    }

    @Override
    protected FlyOption createOption()
    {
        return new FlyOption();
    }

    private float getTargetFlySpeed(String identifier)
    {
        if (identifier == null) return Float.NaN;

        var value = options.get(identifier);

        if (value != null)
            return value.getFlyingSpeed();
        else
            return Float.NaN;
    }

    public boolean updateFlyingAbility(DisguiseState state)
    {
        var player = state.getPlayer();

        player.setAllowFlight(true);

        if (player.getGameMode() != GameMode.SPECTATOR)
        {
            float speed = getOr(
                    getTargetFlySpeed(state.getDisguiseIdentifier()),
                    s -> !Float.isNaN(s),
                    getTargetFlySpeed(state.getSkillLookupIdentifier()));

            speed = Float.isNaN(speed) ? 0.1f : speed;

            if (speed > 1f) speed = 1;
            else if (speed < -1f) speed = -1;

            player.setFlySpeed(speed);
        }

        return true;
    }

    @Resolved
    private MorphManager manager;

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e)
    {
        var player = e.getPlayer();
        if (!this.appliedPlayers.contains(player)) return;

        var state = manager.getDisguiseStateFor(player);

        if (state != null)
        {
            var flying = player.isFlying();

            //?????????????????????????????????????????????1tick?????????
            this.addSchedule(() ->
            {
                if (appliedPlayers.contains(player))
                {
                    this.updateFlyingAbility(state);

                    if (flying)
                        player.setFlying(true);
                }
            });
        }
        else
        {
            logger.warn(player.getName() + "?????????????????????????????????????????????null");
            this.appliedPlayers.remove(player);
        }
    }
}
