package xiamomc.morph.providers;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.reflection.ReflectionManager;
import net.kyori.adventure.text.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.MorphStrings;
import xiamomc.morph.misc.DisguiseInfo;
import xiamomc.morph.misc.DisguiseState;
import xiamomc.morph.misc.DisguiseTypes;
import xiamomc.morph.misc.MorphGameProfile;

import java.util.List;

public class PlayerDisguiseProvider extends DefaultDisguiseProvider
{
    @Override
    public @NotNull String getNameSpace()
    {
        return DisguiseTypes.PLAYER.getNameSpace();
    }

    @Override
    public @NotNull DisguiseResult morph(Player player, DisguiseInfo disguiseInfo, @Nullable Entity targetEntity)
    {
        if (getMorphManager().getBannedDisguises().contains("minecraft:player"))
        {
            player.sendMessage(MessageUtils.prefixes(player, MorphStrings.disguiseBannedOrNotSupportedString()));
            return DisguiseResult.fail();
        }

        var id = disguiseInfo.getIdentifier();

        if (DisguiseTypes.fromId(id) != DisguiseTypes.PLAYER)
            return DisguiseResult.fail();

        var result = getCopy(disguiseInfo, targetEntity);
        var disguise = result.success() ? result.disguise() : new PlayerDisguise(disguiseInfo.playerDisguiseTargetName);

        DisguiseAPI.disguiseEntity(player, disguise);

        return DisguiseResult.success(disguise, result.isCopy());
    }

    @Override
    public void postConstructDisguise(DisguiseState state, @Nullable Entity targetEntity)
    {
        super.postConstructDisguise(state, targetEntity);

        var mainHandItem = state.getPlayer().getItemInHand();

        var compound = new CompoundTag();
        MorphGameProfile gameProfile = null;

        //??????????????????????????????????????????profile
        if (mainHandItem.getType() == Material.PLAYER_HEAD)
        {
            var profile = ((SkullMeta) mainHandItem.getItemMeta()).getPlayerProfile();
            var player = state.getPlayer();

            if (profile == null)
            {
                player.sendMessage(MessageUtils.prefixes(player, MorphStrings.invalidSkinString()));
                return;
            }

            gameProfile = new MorphGameProfile(profile);

            if (gameProfile.getName().equals(DisguiseTypes.PLAYER.toStrippedId(state.getDisguiseIdentifier())))
            {
                //?????????????????????????????????????????????
                var disguise = (PlayerDisguise) state.getDisguise();
                var wrappedProfile = WrappedGameProfile.fromHandle(gameProfile);

                var LDprofile = ReflectionManager.getGameProfileWithThisSkin(wrappedProfile.getUUID(), wrappedProfile.getName(), wrappedProfile);

                //LD??????????????????profile??????????????????????????????????????????????????????
                DisguiseAPI.addGameProfile(LDprofile.toString(), LDprofile);
                disguise.setSkin(LDprofile);
                DisguiseUtilities.removeGameProfile(LDprofile.toString());
            }
        }

        //???????????????????????????????????????
        if (gameProfile == null)
        {
            var watcher = ((PlayerDisguise) state.getDisguise()).getWatcher();

            var profile = watcher.getSkin().getHandle();

            if (profile instanceof GameProfile gProfile)
            {
                gameProfile = new MorphGameProfile(gProfile);
                gameProfile.setName(DisguiseTypes.PLAYER.toStrippedId(state.getDisguiseIdentifier()));
            }
        }

        //?????????profile??????null?????????state???profile???nbt
        if (gameProfile != null)
        {
            NbtUtils.writeGameProfile(compound, gameProfile);
            state.setCachedProfileNbtString(compound.toString());
        }
    }

    @Override
    public List<String> getAllAvailableDisguises()
    {
        var onlinePlayers = Bukkit.getOnlinePlayers();

        var list = new ObjectArrayList<String>();
        onlinePlayers.forEach(p -> list.add(DisguiseTypes.PLAYER.toId(p.getName())));

        return list;
    }

    @Override
    public boolean canConstruct(DisguiseInfo info, Entity targetEntity, @Nullable DisguiseState theirState)
    {
        if (theirState != null)
        {
            var type = DisguiseTypes.fromId(theirState.getDisguiseIdentifier());

            return type == DisguiseTypes.PLAYER
                    && type.toStrippedId(theirState.getDisguiseIdentifier()).equals(info.playerDisguiseTargetName);
        }

        if (!(targetEntity instanceof Player targetPlayer))
            return false;

        return targetPlayer.getName().equals(info.playerDisguiseTargetName);
    }

    @Override
    protected boolean canCopyDisguise(DisguiseInfo info, Entity targetEntity,
                                      @Nullable DisguiseState theirState, @NotNull Disguise theirDisguise)
    {
        if (theirDisguise instanceof PlayerDisguise playerDisguise)
            return playerDisguise.getName().equals(info.playerDisguiseTargetName);

        return false;
    }

    @Override
    public boolean validForClient(DisguiseState state)
    {
        return true;
    }

    @Override
    public @Nullable CompoundTag getNbtCompound(DisguiseState state, Entity targetEntity)
    {
        if (!(targetEntity instanceof Player targetPlayer)) return null;

        if (!targetPlayer.getName().equals(DisguiseTypes.PLAYER.toStrippedId(state.getDisguiseIdentifier()))) return null;

        return super.getNbtCompound(state, targetEntity);
    }

    @Override
    public boolean unMorph(Player player, DisguiseState state)
    {
        return super.unMorph(player, state);
    }

    @Override
    public Component getDisplayName(String disguiseIdentifier, String locale)
    {
        //?????????????????????????????????
        Component finalName;
        var playerName = DisguiseTypes.PLAYER.toStrippedId(disguiseIdentifier);
        var player = Bukkit.getPlayerExact(playerName);

        if (player != null)
            finalName = player.displayName();
        else
            finalName = Component.text(playerName);

        return finalName;
    }
}
