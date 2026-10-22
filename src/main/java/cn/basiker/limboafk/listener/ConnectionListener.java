package cn.basiker.limboafk.listener;

import cn.basiker.limboafk.afk.AfkManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;

/** 登录时建立 AFK 会话，断开时清理。 */
public final class ConnectionListener {

    private final AfkManager afkManager;

    public ConnectionListener(AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        this.afkManager.register(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.afkManager.unregister(event.getPlayer().getUniqueId());
    }
}
