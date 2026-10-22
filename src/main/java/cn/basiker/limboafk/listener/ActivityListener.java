package cn.basiker.limboafk.listener;

import cn.basiker.limboafk.afk.AfkManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;

/** 代理层可观测的玩家动作：聊天、指令、切换服务器。 */
public final class ActivityListener {

    private final AfkManager afkManager;

    public ActivityListener(AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        this.afkManager.touch(event.getPlayer());
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }
        // /afk、/limbo 自身由指令逻辑处理，避免先被当作活动而清掉标记
        String root = event.getCommand().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (root.equals("afk") || root.equals("limbo")) {
            return;
        }
        this.afkManager.touch(player);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        this.afkManager.register(event.getPlayer());
        this.afkManager.touch(event.getPlayer());
    }
}
