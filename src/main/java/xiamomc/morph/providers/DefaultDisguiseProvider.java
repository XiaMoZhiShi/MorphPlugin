package xiamomc.morph.providers;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.abilities.AbilityHandler;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.MorphStrings;
import xiamomc.morph.misc.DisguiseState;
import xiamomc.morph.misc.DisguiseTypes;
import xiamomc.morph.misc.DisguiseUtils;
import xiamomc.morph.network.MorphClientHandler;
import xiamomc.morph.network.commands.S2C.AbstractS2CCommand;
import xiamomc.morph.network.commands.S2C.S2CSetEquipCommand;
import xiamomc.morph.skills.MorphSkillHandler;
import xiamomc.morph.skills.SkillType;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Utilities.ColorUtils;

import java.util.List;

/**
 * 提供一个默认的DisguiseProvider
 * 包括自动设置Bossbar、飞行技能和应用针对LibsDisguises的一些workaround
 */
public abstract class DefaultDisguiseProvider extends DisguiseProvider
{
    @Override
    public boolean unMorph(Player player, DisguiseState state)
    {
        super.unMorph(player, state);

        state.getAbilities().forEach(a -> a.revokeFromPlayer(player, state));
        state.setAbilities(List.of());

        return true;
    }

    @Resolved
    private MorphSkillHandler skillHandler;

    @Resolved
    private AbilityHandler abilityHandler;

    @Resolved
    private Scoreboard scoreboard;

    @Resolved
    private MorphClientHandler clientHandler;

    @Override
    public boolean updateDisguise(Player player, DisguiseState state)
    {
        var disguise = state.getDisguise();
        var watcher = disguise.getWatcher();
        var option = clientHandler.getPlayerOption(player);

        if (option.displayDisguiseOnHUD)
        {
            var locale = MessageUtils.getLocale(player);

            //更新actionbar信息
            var msg = state.haveSkill()
                    ? (state.getSkillCooldown() <= 0
                        ? MorphStrings.disguisingWithSkillAvaliableString()
                        : MorphStrings.disguisingWithSkillPreparingString())
                    : MorphStrings.disguisingAsString();

            player.sendActionBar(msg.withLocale(locale).resolve("what", state.getDisplayName()).toComponent(null));
        }

        //发光颜色
        var team = scoreboard.getPlayerTeam(player);
        var playerColor = (team == null || !team.hasColor()) ? NamedTextColor.WHITE : team.color();

        if (state.getDisguiseType() != DisguiseTypes.LD)
        {
            //workaround: 复制实体伪装时会一并复制隐身标签
            //            会导致复制出来的伪装永久隐身
            if (watcher.isInvisible() != player.isInvisible())
                watcher.setInvisible(player.isInvisible());

            //workaround: 伪装不会主动检测玩家有没有发光
            if (watcher.isGlowing() != player.isGlowing())
                watcher.setGlowing(player.isGlowing());

            //设置发光颜色
            if (!state.haveCustomGlowColor())
                disguise.getWatcher().setGlowColor(ColorUtils.toChatColor(playerColor));

            //设置滑翔状态
            if (watcher.isFlyingWithElytra() != player.isGliding())
                watcher.setFlyingWithElytra(player.isGliding());

            //workaround: 复制出来的伪装会忽略玩家Pose
            if (state.shouldHandlePose())
            {
                var pose = DisguiseUtils.toEntityPose(player.getPose());

                if (watcher.getEntityPose() != pose)
                    watcher.setEntityPose(pose);
            }
        }

        return true;
    }

    @Override
    @NotNull
    public List<AbstractS2CCommand<?>> getInitialSyncCommands(DisguiseState state)
    {
        //logger.info("SID: " + state.getSkillLookupIdentifier() + " :: DID: " + state.getDisguiseIdentifier());
        if (skillHandler.hasSpeficSkill(state.getSkillLookupIdentifier(), SkillType.INVENTORY))
        {
            var eqiupment = state.getDisguisedItems();

            var list = new ObjectArrayList<AbstractS2CCommand<?>>();

            this.addIfPresents(eqiupment, list, EquipmentSlot.HAND);
            this.addIfPresents(eqiupment, list, EquipmentSlot.OFF_HAND);
            this.addIfPresents(eqiupment, list, EquipmentSlot.HEAD);
            this.addIfPresents(eqiupment, list, EquipmentSlot.CHEST);
            this.addIfPresents(eqiupment, list, EquipmentSlot.LEGS);
            this.addIfPresents(eqiupment, list, EquipmentSlot.FEET);

            return list;
        }

        return List.of();
    }

    private void addIfPresents(EntityEquipment equipment, ObjectArrayList<AbstractS2CCommand<?>> list, EquipmentSlot slot)
    {
        var item = equipment.getItem(slot);

        if (item.getType() != Material.AIR)
            list.add(new S2CSetEquipCommand(item, slot));
    }

    @Override
    public void postConstructDisguise(DisguiseState state, @Nullable Entity targetEntity)
    {
        var watcher = state.getDisguise().getWatcher();
        var disguiseTypeLD = state.getDisguise().getType();

        var disguise = state.getDisguise();

        disguise.setKeepDisguiseOnPlayerDeath(true);

        //workaround: 伪装已死亡的LivingEntity
        if (targetEntity instanceof LivingEntity living && living.getHealth() <= 0)
            ((LivingWatcher) watcher).setHealth(1);

        if (state.shouldHandlePose() && targetEntity instanceof Player targetPlayer)
        {
            //如果目标实体是玩家，并且此玩家的伪装类型和我们的一样，那么复制他们的装备
            var theirState = getMorphManager().getDisguiseStateFor(targetPlayer);

            Disguise theirDisguise;

            //优先从DisguiseState判断是否为同类
            if (theirState != null)
            {
                theirDisguise = theirState.getDisguise();

                if (theirState.getDisguiseIdentifier().equals(state.getDisguiseIdentifier()))
                    DisguiseUtils.tryCopyArmorStack(targetPlayer, disguise.getWatcher(), theirDisguise.getWatcher());
            }
            else
            {
                theirDisguise = DisguiseAPI.getDisguise(targetPlayer);

                //不是玩家伪装，判断类型是否一样
                //否则，判断双方伪装的名称是否一致
                if (disguiseTypeLD != DisguiseType.PLAYER)
                {
                    if (disguiseTypeLD.equals(theirDisguise.getType()))
                        DisguiseUtils.tryCopyArmorStack(targetPlayer, disguise.getWatcher(), theirDisguise.getWatcher());
                }
                else if (theirDisguise instanceof PlayerDisguise theirPlayerDisguise
                            && disguise instanceof PlayerDisguise ourPlayerDisguise
                            && theirPlayerDisguise.getName().equals(ourPlayerDisguise.getName()))
                {
                    DisguiseUtils.tryCopyArmorStack(targetPlayer, disguise.getWatcher(), theirDisguise.getWatcher());
                }
            }
        }

        //被动技能
        var abilities = abilityHandler.getAbilitiesFor(state.getSkillLookupIdentifier());
        state.setAbilities(abilities);

        //发光颜色
        ChatColor glowColor = null;
        Entity teamTargetEntity = targetEntity;
        var disguiseID = state.getDisguiseIdentifier();
        var morphDisguiseType = DisguiseTypes.fromId(disguiseID);

        switch (morphDisguiseType)
        {
            //LD伪装为玩家时会自动复制他们当前的队伍到名字里
            //为了确保一致，除非targetEntity不是null，不然尝试将teamTargetEntity设置为目标玩家
            case PLAYER ->
            {
                if (teamTargetEntity == null)
                    teamTargetEntity = Bukkit.getPlayerExact(DisguiseTypes.PLAYER.toStrippedId(disguiseID));
            }

            //LD的伪装直接从伪装flag里获取发光颜色
            //只要和LD直接打交道事情就变得玄学了起来..
            case LD ->
            {
                glowColor = watcher.getGlowColor();
                teamTargetEntity = null;
            }
        }

        //从teamTargetEntity获取发光颜色
        if (teamTargetEntity != null)
        {
            var team = scoreboard.getEntityTeam(teamTargetEntity);

            //如果伪装是复制来的，并且目标实体有伪装，则将颜色设置为他们伪装的发光颜色
            if (state.shouldHandlePose() && DisguiseAPI.isDisguised(teamTargetEntity))
                glowColor = DisguiseAPI.getDisguise(teamTargetEntity).getWatcher().getGlowColor();
            else
                glowColor = team == null ? null : ColorUtils.toChatColor(team.color()); //否则，尝试设置成我们自己的
        }

        //设置发光颜色
        state.setCustomGlowColor(ColorUtils.fromChatColor(glowColor));
        watcher.setGlowColor(glowColor);
    }
}
