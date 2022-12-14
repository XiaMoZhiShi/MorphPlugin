package xiamomc.morph.skills.impl;

import org.bukkit.Difficulty;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.SkillStrings;
import xiamomc.morph.skills.MorphSkill;
import xiamomc.morph.skills.SkillType;
import xiamomc.morph.storage.skill.ISkillOption;
import xiamomc.morph.storage.skill.NoOpConfiguration;
import xiamomc.morph.storage.skill.SkillConfiguration;

public class SummonFangsMorphSkill extends MorphSkill<NoOpConfiguration>
{
    @Override
    public int executeSkill(Player player, SkillConfiguration configuration, NoOpConfiguration option)
    {
        var targetEntity = player.getTargetEntity(16);

        var summonVex = targetEntity != null && targetEntity.getLocation().distance(player.getLocation()) > 8;
        var world = player.getWorld();

        if (summonVex)
        {
            if (world.getDifficulty() == Difficulty.PEACEFUL)
            {
                sendDenyMessageToPlayer(player, SkillStrings.difficultyIsPeacefulString()
                        .withLocale(MessageUtils.getLocale(player))
                        .toComponent(null));

                return 10;
            }

            var shouldTarget = targetEntity instanceof LivingEntity;

            var location = player.getEyeLocation();
            var targetAmount = 3;

            for (int i = 0; i < targetAmount; i++)
            {
                var vex  = world.spawn(location, Vex.class, CreatureSpawnEvent.SpawnReason.NATURAL);

                if (shouldTarget)
                    vex.setTarget((LivingEntity) targetEntity);

                vex.setPersistent(false);
            }
        }
        else
        {
            var location = player.getLocation();
            var direction = player.getEyeLocation().getDirection();

            var targetFangs = 16;
            Location oldLocation = null;

            for (int i = 0; i < targetFangs; i++)
            {
                location.add(direction.getX(), 0, direction.getZ());

                //????????????????????????
                if (world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ()).getType().isAir())
                {
                    var blockDown = world.rayTraceBlocks(location, new Vector(0, -1, 0), 8);
                    Block newBlock;

                    //??????????????????????????????
                    newBlock = blockDown == null ? null : blockDown.getHitBlock();

                    //???????????????
                    if (newBlock != null)
                    {
                        var newY = getTopY(newBlock);
                        location.setY(newY);
                    }
                    else
                        break;
                }

                if (oldLocation != null && oldLocation.getBlockY() == location.getBlockY())
                {
                    //trace????????????????????????????????????????????????????????????????????????????????????
                    var traceDirection = location.clone().subtract(oldLocation).toVector();

                    if (traceDirection.lengthSquared() > 0.0)
                    {
                        var traceResult = world.rayTraceBlocks(location, traceDirection, oldLocation.distance(location) + 1, FluidCollisionMode.NEVER, true);

                        if (traceResult != null)
                            break;
                    }
                }

                //????????????
                var loc = location.clone();
                oldLocation = location.clone();

                //?????????????????????
                this.addSchedule(() ->
                {
                    var fang = world.spawn(loc, EvokerFangs.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
                    fang.setOwner(player);
                }, i);
            }
        }

        return configuration.getCooldown();
    }

    @Override
    public @NotNull NamespacedKey getIdentifier()
    {
        return SkillType.EVOKER;
    }

    private final NoOpConfiguration option = new NoOpConfiguration();

    @Override
    public NoOpConfiguration getOption()
    {
        return option;
    }
}
