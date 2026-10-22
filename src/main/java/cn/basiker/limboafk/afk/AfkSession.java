package cn.basiker.limboafk.afk;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.UUID;
import net.elytrium.limboapi.api.player.LimboPlayer;

/** 单个玩家的 AFK 状态；事件线程与调度线程共享，字段使用 volatile。 */
final class AfkSession {

    final UUID uuid;
    volatile long lastActivity;
    volatile boolean manualAfk;
    volatile boolean warned;
    volatile boolean inLimbo;
    /** 进入 Limbo 前所在的服务器，用于返回。 */
    volatile RegisteredServer origin;
    volatile LimboPlayer limboPlayer;

    AfkSession(UUID uuid, long now) {
        this.uuid = uuid;
        this.lastActivity = now;
    }
}
