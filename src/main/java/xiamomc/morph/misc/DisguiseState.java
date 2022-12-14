package xiamomc.morph.misc;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import me.libraryaddict.disguise.utilities.parser.DisguiseParser;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.morph.MorphManager;
import xiamomc.morph.MorphPluginObject;
import xiamomc.morph.abilities.AbilityHandler;
import xiamomc.morph.abilities.IMorphAbility;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.network.MorphClientHandler;
import xiamomc.morph.network.commands.S2C.S2CSetSkillCooldownCommand;
import xiamomc.morph.providers.DisguiseProvider;
import xiamomc.morph.skills.IMorphSkill;
import xiamomc.morph.skills.MorphSkillHandler;
import xiamomc.morph.skills.SkillCooldownInfo;
import xiamomc.morph.skills.SkillType;
import xiamomc.morph.skills.impl.NoneMorphSkill;
import xiamomc.morph.storage.offlinestore.OfflineDisguiseState;
import xiamomc.morph.storage.playerdata.PlayerMorphConfiguration;
import xiamomc.pluginbase.Annotations.Resolved;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static xiamomc.morph.misc.DisguiseUtils.itemOrAir;

public class DisguiseState extends MorphPluginObject
{
    public DisguiseState(Player player, @NotNull String id, @NotNull String skillId,
                         Disguise disguiseInstance, boolean isClone, @NotNull DisguiseProvider provider,
                         @Nullable EntityEquipment targetEquipment)
    {
        this.player = player;
        this.playerUniqueID = player.getUniqueId();
        this.provider = provider;

        this.setDisguise(id, skillId, disguiseInstance, isClone, targetEquipment);
    }

    /**
     * ????????????
     */
    private Player player;

    public Player getPlayer()
    {
        return player;
    }

    public void setPlayer(Player p)
    {
        if (!p.getUniqueId().equals(playerUniqueID)) throw new RuntimeException("???????????????UUID??????");

        player = p;
    }

    /**
     * ???????????????UUID???
     */
    private final UUID playerUniqueID;

    public UUID getPlayerUniqueID()
    {
        return playerUniqueID;
    }

    /**
     * ????????????
     */
    private boolean serverSideSelfVisible;

    public boolean getServerSideSelfVisible()
    {
        return serverSideSelfVisible;
    }

    public void setServerSideSelfVisible(boolean val)
    {
        DisguiseAPI.setViewDisguiseToggled(player, val);

        serverSideSelfVisible = val;
    }

    /**
     * ?????????????????????
     */
    private Component displayName;

    public Component getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(Component newName)
    {
        displayName = newName;
    }

    /**
     * ???????????????
     */
    private Disguise disguise;

    public Disguise getDisguise()
    {
        return disguise;
    }

    /**
     * ?????????ID
     */
    private String disguiseIdentifier = SkillType.UNKNOWN.asString();

    public String getDisguiseIdentifier()
    {
        return disguiseIdentifier;
    }

    public EntityType getEntityType()
    {
        return disguise.getType().getEntityType();
    }

    private DisguiseTypes disguiseType;

    public DisguiseTypes getDisguiseType()
    {
        return disguiseType;
    }

    /**
     * ?????????Provider
     */
    private DisguiseProvider provider;

    @NotNull
    public DisguiseProvider getProvider()
    {
        return provider;
    }

    /**
     * ?????????Bossbar
     */
    @Nullable
    private BossBar bossbar;

    @Nullable
    public BossBar getBossbar()
    {
        return bossbar;
    }

    public void setBossbar(BossBar bossbar)
    {
        if (this.bossbar != null)
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(this.bossbar));

        this.bossbar = bossbar;
    }

    @Nullable
    private TextColor customGlowColor;

    @Nullable
    public TextColor getCustomGlowColor()
    {
        return customGlowColor;
    }

    public boolean haveCustomGlowColor()
    {
        return customGlowColor != null;
    }

    public void setCustomGlowColor(@Nullable TextColor color)
    {
        this.customGlowColor = color;
    }

    /**
     * ????????????ID
     */
    private String skillLookupIdentifier = "minecraft:@default";

    /**
     * ???????????????????????????ID
     *
     * @return ??????ID
     */
    @NotNull
    public String getSkillLookupIdentifier()
    {
        return skillLookupIdentifier;
    }

    /**
     * ??????????????????ID
     *
     * @param skillID ??????ID
     */
    public void setSkillLookupIdentifier(@NotNull String skillID)
    {
        this.skillLookupIdentifier = skillID;
    }

    private IMorphSkill<?> skill = NoneMorphSkill.instance;

    /**
     * ????????????????????????
     * @param s ????????????
     * @apiNote ?????????????????????null?????????fallback??? {@link NoneMorphSkill#instance}
     */
    public void setSkill(@Nullable IMorphSkill<?> s)
    {
        if (s == null) s = NoneMorphSkill.instance;

        if (this.skill != null)
            skill.onDeEquip(this);

        this.skill = s;
        s.onInitialEquip(this);
    }

    /**
     * ????????????????????????
     * @return {@link IMorphSkill}
     */
    @NotNull
    public IMorphSkill<?> getSkill()
    {
        return skill;
    }

    /**
     * ???????????????????????????
     * @return ????????????????????? {@link NoneMorphSkill} ?????????
     */
    public boolean haveSkill()
    {
        return skill != NoneMorphSkill.instance;
    }

    /**
     * ??????????????????
     */
    private SkillCooldownInfo cooldownInfo;

    public long getSkillCooldown()
    {
        return cooldownInfo == null ? -1 : cooldownInfo.getCooldown();
    }

    public long getSkillLastInvoke()
    {
        return cooldownInfo == null ? Long.MIN_VALUE : cooldownInfo.getLastInvoke();
    }

    public void setSkillCooldown(long val)
    {
        if (haveCooldown())
        {
            cooldownInfo.setCooldown(val);

            if (clientHandler.clientVersionCheck(player, 3))
                clientHandler.sendClientCommand(player, new S2CSetSkillCooldownCommand(val));
        }
    }

    public boolean haveCooldown()
    {
        return cooldownInfo != null;
    }

    public void setCooldownInfo(SkillCooldownInfo info)
    {
        this.cooldownInfo = info;

        if (clientHandler.clientVersionCheck(player, 3))
            clientHandler.sendClientCommand(player, new S2CSetSkillCooldownCommand(info.getCooldown()));
    }

    //region ????????????

    /**
     * ??????????????????Flag
     */
    private final List<IMorphAbility<?>> abilities = new ObjectArrayList<>();

    public List<IMorphAbility<?>> getAbilities()
    {
        return abilities;
    }

    public void setAbilities(@Nullable List<IMorphAbility<?>> newAbilities)
    {
        abilities.clear();

        if (newAbilities != null)
            abilities.addAll(newAbilities);
    }

    /**
     * ????????????????????????????????????
     * @param key {@link NamespacedKey}
     * @return ????????????
     */
    public boolean containsAbility(NamespacedKey key)
    {
        return abilities.stream().anyMatch(a -> a.getIdentifier().equals(key));
    }

    //endregion abilityFlag

    /**
     * ???????????????????????????Pose???
     */
    private boolean shouldHandlePose;
    public boolean shouldHandlePose()
    {
        return shouldHandlePose;
    }

    @Resolved(shouldSolveImmediately = true)
    private AbilityHandler abilityHandler;

    @Resolved(shouldSolveImmediately = true)
    private MorphSkillHandler skillHandler;

    @Resolved(shouldSolveImmediately = true)
    private MorphClientHandler clientHandler;

    //region NBT

    private String cachedNbtString = "{}";

    public String getCachedNbtString()
    {
        return cachedNbtString;
    }

    public void setCachedNbtString(String newNbt)
    {
        if (newNbt == null || newNbt.isEmpty() || newNbt.isBlank()) newNbt = "{}";

        this.cachedNbtString = newNbt;
    }

    //endregion

    //region ProfileNBT

    private String cachedProfileNbtString = "{}";

    public String getProfileNbtString()
    {
        return cachedProfileNbtString;
    }

    public void setCachedProfileNbtString(String newNbt)
    {
        if (newNbt == null || newNbt.isEmpty() || newNbt.isBlank()) newNbt = "{}";

        this.cachedProfileNbtString = newNbt;
    }

    public boolean haveProfile()
    {
        return !cachedProfileNbtString.equals("{}");
    }

    //endregion ProfileNBT

    /**
     * ????????????
     * @param identifier ??????ID
     * @param skillIdentifier ??????ID
     * @param d ????????????
     * @param shouldHandlePose ?????????????????????Pose????????????????????????????????????
     * @param equipment ????????????equipment???????????????????????????
     */
    public void setDisguise(@NotNull String identifier, @NotNull String skillIdentifier,
                            Disguise d, boolean shouldHandlePose, @Nullable EntityEquipment equipment)
    {
        setDisguise(identifier, skillIdentifier, d, shouldHandlePose, true, equipment);
    }

    /**
     * ????????????
     * @param identifier ??????ID
     * @param skillIdentifier ??????ID
     * @param d ????????????
     * @param shouldHandlePose ?????????????????????Pose????????????????????????????????????
     * @param shouldRefreshDisguiseItems ??????????????????????????????
     * @param targetEquipment ????????????equipment???????????????????????????
     */
    public void setDisguise(@NotNull String identifier, @NotNull String skillIdentifier,
                            Disguise d, boolean shouldHandlePose, boolean shouldRefreshDisguiseItems,
                            @Nullable EntityEquipment targetEquipment)
    {
        if (!DisguiseUtils.isTracing(d))
            throw new RuntimeException("???Disguise?????????????????????");

        if (disguise == d) return;

        setCachedProfileNbtString(null);
        setCachedNbtString(null);

        this.disguise = d;
        this.disguiseIdentifier = identifier;
        this.shouldHandlePose = shouldHandlePose;
        setSkillLookupIdentifier(skillIdentifier);

        disguiseType = DisguiseTypes.fromId(identifier);

        var provider = MorphManager.getProvider(identifier);

        this.provider = provider;
        displayName = provider.getDisplayName(identifier, MessageUtils.getLocale(player));

        //??????????????????????????????????????????
        supportsDisguisedItems = skillHandler.hasSpeficSkill(skillIdentifier, SkillType.INVENTORY);

        //??????????????????
        if (shouldRefreshDisguiseItems)
        {
            disguiseArmors = emptyArmorStack;
            handItems = emptyHandItems;

            //??????????????????
            if (supportsDisguisedItems)
            {
                EntityEquipment equipment = targetEquipment != null ? targetEquipment : disguise.getWatcher().getEquipment();

                //??????????????????
                disguiseArmors = new ItemStack[]
                        {
                                itemOrAir(equipment.getBoots()),
                                itemOrAir(equipment.getLeggings()),
                                itemOrAir(equipment.getChestplate()),
                                itemOrAir(equipment.getHelmet())
                        };

                //?????????????????????
                handItems = new ItemStack[]
                        {
                                itemOrAir(equipment.getItemInMainHand()),
                                itemOrAir(equipment.getItemInOffHand())
                        };

                //workaround: ??????????????????????????????????????????????????????????????????????????????????????????
                if (handItems[0].isSimilar(handItems[1]))
                    handItems[1] = itemOrAir(null);

                //??????????????????????????????????????????
                var emptyEquipment = Arrays.stream(disguiseArmors).allMatch(i -> i != null && i.getType().isAir())
                        && Arrays.stream(handItems).allMatch(i -> i != null && i.getType().isAir());

                //??????????????????????????????????????????
                setShowingDisguisedItems(showDisguisedItems || !emptyEquipment);
            }
        }

        //????????????Flag
        var abilities = abilityHandler.getAbilitiesFor(identifier);
        if (abilities != null)
        {
            setAbilities(abilities);
            abilities.forEach(a -> a.applyToPlayer(player, this));
        }

        setSkill(skillHandler.getSkill(this.getSkillLookupIdentifier()));
    }

    /**
     * ???????????????????????????????????????????????????
     */
    @Nullable
    private ItemStack[] disguiseArmors;

    @Nullable
    private ItemStack[] handItems;

    //???null??????????????????????????????
    private final ItemStack[] emptyArmorStack = new ItemStack[]{ null, null, null, null };

    private final ItemStack[] emptyHandItems = new ItemStack[]{ null, null };

    private boolean showDisguisedItems = false;

    private boolean supportsDisguisedItems = false;

    /**
     * ???????????????????????????????????????
     * @return ????????????
     */
    public boolean supportsShowingDefaultItems()
    {
        return supportsDisguisedItems;
    }

    /**
     * ???????????????????????????????????????
     * @return ??????????????????
     */
    public boolean showingDisguisedItems()
    {
        return showDisguisedItems;
    }

    /**
     * ?????????????????????????????????
     * @param value ???
     */
    public void setShowingDisguisedItems(boolean value)
    {
        var watcher = disguise.getWatcher();
        updateEquipment(watcher, value);
        showDisguisedItems = value;
    }

    /**
     * ?????????State???????????????
     * @return ???State???????????????
     */
    public EntityEquipment getDisguisedItems()
    {
        var eq = new DisguiseEquipment();

        var targetStack = disguiseArmors == null
                    ? new ItemStack[]{itemOrAir(null), itemOrAir(null), itemOrAir(null), itemOrAir(null)}
                    : disguiseArmors;

        eq.setArmorContents(targetStack);
        eq.setItemInHand(handItems[0]);
        eq.setItemInOffHand(handItems[1]);

        return eq;
    }

    @ApiStatus.Internal
    public void swapHands()
    {
        if (handItems != null && handItems.length == 2)
        {
            var mainHand = handItems[0];
            var offHand = handItems[1];

            handItems[0] = offHand;
            handItems[1] = mainHand;
        }
    }

    /**
     * ??????????????????????????????
     * @return ???????????????
     */
    public boolean toggleDisguisedItems()
    {
        setShowingDisguisedItems(!showDisguisedItems);

        return showDisguisedItems;
    }

    /**
     * ????????????????????????
     * @param watcher ?????????Watcher
     * @param showDefaults ????????????????????????
     * @apiNote ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private void updateEquipment(FlagWatcher watcher, boolean showDefaults)
    {
        watcher.setArmor(showDefaults ? disguiseArmors : emptyArmorStack);
        watcher.setItemInMainHand(showDefaults ? handItems[0] : emptyHandItems[0]);
        watcher.setItemInOffHand(showDefaults ? handItems[1] : emptyHandItems[1]);
    }

    public DisguiseState createCopy()
    {
        var disguise = this.disguise.clone();
        DisguiseUtils.addTrace(disguise);

        var state = new DisguiseState(player, this.disguiseIdentifier, this.skillLookupIdentifier,
                disguise, shouldHandlePose, provider, getDisguisedItems());

        state.setCachedProfileNbtString(this.cachedProfileNbtString);
        state.setCachedNbtString(this.cachedNbtString);

        return state;
    }

    /**
     * ???????????????????????????
     * @return ???State???????????????
     */
    public OfflineDisguiseState toOfflineState()
    {
        var offlineState = new OfflineDisguiseState();

        offlineState.playerUUID = this.playerUniqueID;
        offlineState.playerName = this.player.getName();

        offlineState.disguiseID = this.getDisguiseIdentifier();
        offlineState.skillID = this.getSkillLookupIdentifier();

        var newDisguise = disguise.clone();

        if (supportsDisguisedItems)
            updateEquipment(newDisguise.getWatcher(), true);

        offlineState.disguiseData = DisguiseParser.parseToString(newDisguise);
        offlineState.shouldHandlePose = this.shouldHandlePose;
        offlineState.showingDisguisedItems = this.showDisguisedItems;
        offlineState.nbtString = this.cachedNbtString;
        offlineState.profileString = this.cachedProfileNbtString;

        return offlineState;
    }

    /**
     * ??????????????????????????????
     * @param offlineState ????????????
     * @return DisguiseState?????????
     */
    public static DisguiseState fromOfflineState(OfflineDisguiseState offlineState, PlayerMorphConfiguration configuration)
    {
        if (!offlineState.isValid())
            throw new RuntimeException("??????????????????");

        var player = Bukkit.getPlayer(offlineState.playerUUID);

        if (player == null) throw new RuntimeException("????????????" + offlineState.playerUUID + "???????????????");

        //todo: ?????????????????????????????????
        var state = new DisguiseState(player,
                offlineState.disguiseID, offlineState.skillID == null ? offlineState.disguiseID : offlineState.skillID,
                offlineState.disguise, offlineState.shouldHandlePose, MorphManager.getProvider(offlineState.disguiseID),
                null);

        state.setCachedProfileNbtString(offlineState.profileString);
        state.setCachedNbtString(offlineState.nbtString);

        if (state.supportsDisguisedItems)
            state.setShowingDisguisedItems(offlineState.showingDisguisedItems);

        return state;
    }
}
