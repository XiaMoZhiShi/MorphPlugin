package xiamomc.morph;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.entity.Player;
import xiamomc.morph.interfaces.IManagePlayerData;
import xiamomc.morph.interfaces.IManageRequests;
import xiamomc.morph.messages.CommandStrings;
import xiamomc.morph.messages.MessageUtils;
import xiamomc.morph.messages.RequestStrings;
import xiamomc.morph.misc.DisguiseTypes;
import xiamomc.morph.misc.RequestInfo;
import xiamomc.morph.misc.permissions.CommonPermissions;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;

import java.util.List;

public class RequestManager extends MorphPluginObject implements IManageRequests
{
    //region Implementation of IManageRequests

    @Resolved
    private IManagePlayerData data;

    @Initializer
    private void load()
    {
        this.addSchedule(this::update);
    }

    private void update()
    {
        //更新请求
        var requests = new ObjectArrayList<>(this.requests);
        for (var r : requests)
        {
            r.ticksRemain -= 1;
            if (r.ticksRemain <= 0) this.requests.remove(r);
        }

        this.addSchedule(this::update);
    }

    private final List<RequestInfo> requests = new ObjectArrayList<>();

    @Override
    public void createRequest(Player source, Player target)
    {
        if (!source.hasPermission(CommonPermissions.SEND_REQUEST))
        {
            source.sendMessage(MessageUtils.prefixes(source, CommandStrings.noPermissionMessage()));
            return;
        }

        if (requests.stream()
                .anyMatch(i -> i.sourcePlayer.getUniqueId() == source.getUniqueId()
                        && i.targetPlayer.getUniqueId() == target.getUniqueId()))
        {
            source.sendMessage(MessageUtils.prefixes(source, RequestStrings.requestAlreadySentString()));
            return;
        }

        var req = new RequestInfo();
        req.sourcePlayer = source;
        req.targetPlayer = target;
        req.ticksRemain = 1200;

        requests.add(req);

        target.sendMessage(MessageUtils.prefixes(target, RequestStrings.requestReceivedString()
                .resolve("who", source.getName())));

        target.sendMessage(MessageUtils.prefixes(target, RequestStrings.requestReceivedAcceptString()
                .resolve("who", source.getName())));

        target.sendMessage(MessageUtils.prefixes(target, RequestStrings.requestReceivedDenyString()
                .resolve("who", source.getName())));

        source.sendMessage(MessageUtils.prefixes(source, RequestStrings.requestSendString()));
    }

    /**
     * 接受请求
     * @param source 请求接受方
     * @param target 请求发起方
     */
    @Override
    public void acceptRequest(Player source, Player target)
    {
        if (!source.hasPermission(CommonPermissions.ACCEPT_REQUEST))
        {
            source.sendMessage(MessageUtils.prefixes(source, CommandStrings.noPermissionMessage()));
            return;
        }

        var req = requests.stream()
                .filter(i -> i.sourcePlayer.getUniqueId().equals(target.getUniqueId())
                        && i.targetPlayer.getUniqueId().equals(source.getUniqueId())).findFirst().orElse(null);

        if (req == null)
        {
            source.sendMessage(MessageUtils.prefixes(source, RequestStrings.requestNotFound()));
            return;
        }

        req.ticksRemain = -1;

        data.grantMorphToPlayer(target, DisguiseTypes.PLAYER.toId(source.getName()));
        data.grantMorphToPlayer(source, DisguiseTypes.PLAYER.toId(target.getName()));

        target.sendMessage(MessageUtils.prefixes(target, RequestStrings.targetAcceptedString().resolve("who", source.getName())));
        source.sendMessage(MessageUtils.prefixes(source, RequestStrings.sourceAcceptedString().resolve("who", target.getName())));
    }

    /**
     * 拒绝请求
     * @param source 请求接受方
     * @param target 请求发起方
     */
    @Override
    public void denyRequest(Player source, Player target)
    {
        if (!source.hasPermission(CommonPermissions.DENY_REQUEST))
        {
            source.sendMessage(MessageUtils.prefixes(source, CommandStrings.noPermissionMessage()));
            return;
        }

        var req = requests.stream()
                .filter(i -> i.sourcePlayer.getUniqueId().equals(target.getUniqueId())
                        && i.targetPlayer.getUniqueId().equals(source.getUniqueId())).findFirst().orElse(null);

        if (req == null)
        {
            source.sendMessage(MessageUtils.prefixes(source, RequestStrings.requestNotFound()));

            //"未找到目标请求，可能已经过期？"
            return;
        }

        req.ticksRemain = -1;

        target.sendMessage(MessageUtils.prefixes(target, RequestStrings.targetDeniedString().resolve("who", source.getName())));
        source.sendMessage(MessageUtils.prefixes(source, RequestStrings.sourceAcceptedString().resolve("who", target.getName())));
    }

    @Override
    public List<RequestInfo> getAvaliableRequestFor(Player player)
    {
        return requests.stream()
                .filter(t -> t.targetPlayer.getUniqueId().equals(player.getUniqueId()))
                .toList();
    }

    //endregion Implementation of IManageRequests
}
