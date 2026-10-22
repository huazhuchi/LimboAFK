package cn.basiker.limboafk.afk;

import cn.basiker.limboafk.config.ConfigManager;
import cn.basiker.limboafk.config.Settings;
import cn.basiker.limboafk.limbo.LimboService;
import cn.basiker.limboafk.util.Text;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.material.VirtualItem;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.slf4j.Logger;

/** AFK 状态机：计时、警告、送入/退出 Limbo 与返回原服务器。 */
public final class AfkManager {

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final ConfigManager config;
    private final LimboService limboService;
    private final LimboFactory factory;
    private final Map<UUID, AfkSession> sessions = new ConcurrentHashMap<>();

    private volatile ScheduledTask task;

    public AfkManager(Object plugin, ProxyServer proxy, Logger logger, ConfigManager config, LimboService limboService) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.limboService = limboService;
        this.factory = limboService.factory();
    }

    /** 启动或按新配置重启扫描任务。 */
    public void restart() {
        if (this.task != null) {
            this.task.cancel();
        }
        int interval = this.config.settings().checkInterval();
        this.task = this.proxy.getScheduler()
                .buildTask(this.plugin, this::tick)
                .delay(Duration.ofSeconds(1))
                .repeat(Duration.ofSeconds(interval))
                .schedule();
    }

    public void register(Player player) {
        this.sessions.computeIfAbsent(player.getUniqueId(), id -> new AfkSession(id, System.currentTimeMillis()));
    }

    public void unregister(UUID uuid) {
        this.sessions.remove(uuid);
    }

    /** 代理层可观测的有效动作：重置计时并清除 AFK 标记。 */
    public void touch(Player player) {
        AfkSession session = this.sessions.get(player.getUniqueId());
        if (session == null || session.inLimbo) {
            return;
        }
        boolean wasAfk = session.manualAfk || session.warned;
        session.lastActivity = System.currentTimeMillis();
        session.manualAfk = false;
        session.warned = false;
        if (wasAfk) {
            this.send(player, this.config.settings().messages().afkCancelled());
        }
    }

    /** /afk：开启或取消手动 AFK 标记。 */
    public void markAfk(Player player) {
        AfkSession session = this.sessions.computeIfAbsent(
                player.getUniqueId(), id -> new AfkSession(id, System.currentTimeMillis()));
        if (session.inLimbo) {
            leaveLimbo(session, "指令");
            return;
        }

        boolean cancelled;
        synchronized (session) {
            cancelled = session.manualAfk;
            session.manualAfk = !cancelled;
            session.warned = false;
            session.lastActivity = System.currentTimeMillis();
        }

        Settings settings = this.config.settings();
        if (cancelled) {
            this.send(player, settings.messages().afkCancelled());
            return;
        }
        this.send(player, settings.messages().afkMarked()
                .replace("%seconds%", String.valueOf(settings.afkTimeout())));
        this.logger.info("玩家 {} 手动标记 AFK", player.getUsername());
    }

    private void tick() {
        try {
            Settings settings = this.config.settings();
            if (settings == null || !settings.enabled()) {
                return;
            }

            long now = System.currentTimeMillis();
            for (AfkSession session : this.sessions.values()) {
                Optional<Player> optional = this.proxy.getPlayer(session.uuid);
                if (optional.isEmpty()) {
                    this.sessions.remove(session.uuid);
                    continue;
                }
                if (session.inLimbo) {
                    continue;
                }

                Player player = optional.get();
                boolean manual = session.manualAfk;
                // 自动检测关闭时，只处理手动 /afk 的倒计时
                if (!settings.autoAfk() && !manual) {
                    continue;
                }
                // bypass 只拦截自动检测，手动 /afk 仍然生效
                if (!manual && player.hasPermission(settings.permissions().bypass())) {
                    continue;
                }

                long idleSeconds = (now - session.lastActivity) / 1000L;
                if (idleSeconds >= settings.afkTimeout()) {
                    enterLimbo(player, session, settings);
                    continue;
                }

                long warnAt = settings.afkTimeout() - settings.warningBefore();
                if (settings.warningBefore() > 0 && !session.warned && idleSeconds >= warnAt) {
                    session.warned = true;
                    long remaining = settings.afkTimeout() - idleSeconds;
                    this.send(player, settings.messages().afkWarning()
                            .replace("%seconds%", String.valueOf(remaining)));
                    this.logger.info("玩家 {} 触发 AFK 预警，{} 秒后进入 Limbo", player.getUsername(), remaining);
                }
            }
        } catch (Throwable throwable) {
            this.logger.error("AFK 检查任务异常", throwable);
        }
    }

    private void enterLimbo(Player player, AfkSession session, Settings settings) {
        synchronized (session) {
            if (session.inLimbo) {
                return;
            }
            session.inLimbo = true;
            session.manualAfk = false;
            session.warned = false;
            session.origin = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);
        }

        try {
            this.limboService.spawn(player, new AfkLimboHandler(this, session, settings));
        } catch (Throwable throwable) {
            synchronized (session) {
                session.inLimbo = false;
            }
            this.send(player, settings.messages().limboFailed());
            this.logger.error("玩家 {} 送入 Limbo 失败", player.getUsername(), throwable);
            return;
        }

        this.logger.info("玩家 {} 因 AFK 进入 Limbo（原服务器：{}）",
                player.getUsername(), serverName(session.origin));
    }

    /** 退出 Limbo 并回到进入前的服务器；重复调用安全。 */
    void leaveLimbo(AfkSession session, String reason) {
        LimboPlayer limboPlayer;
        synchronized (session) {
            if (!session.inLimbo) {
                return;
            }
            session.inLimbo = false;
            session.lastActivity = System.currentTimeMillis();
            session.manualAfk = false;
            session.warned = false;
            limboPlayer = session.limboPlayer;
            session.limboPlayer = null;
        }

        Optional<Player> optional = this.proxy.getPlayer(session.uuid);
        if (optional.isEmpty()) {
            return;
        }

        Player player = optional.get();
        Settings settings = this.config.settings();
        RegisteredServer target = resolveReturnServer(session.origin);
        if (target == null) {
            player.disconnect(Text.color(settings.messages().prefix() + settings.messages().noServer()));
            this.logger.warn("玩家 {} 离开 Limbo 时没有可用服务器，已断开连接", player.getUsername());
            return;
        }

        player.sendMessage(Text.color(settings.messages().prefix() + settings.messages().limboReturned()
                .replace("%server%", target.getServerInfo().getName())));
        if (limboPlayer != null) {
            limboPlayer.disconnect(target);
        } else {
            player.createConnectionRequest(target).fireAndForget();
        }
        this.logger.info("玩家 {} 离开 Limbo（{}），返回服务器 {}",
                player.getUsername(), reason, target.getServerInfo().getName());
    }

    void onLimboDisconnect(AfkSession session) {
        synchronized (session) {
            if (!session.inLimbo) {
                return;
            }
            session.inLimbo = false;
            session.limboPlayer = null;
        }
        String name = this.proxy.getPlayer(session.uuid).map(Player::getUsername).orElse(session.uuid.toString());
        this.logger.info("玩家 {} 在 Limbo 中断开连接", name);
    }

    /** 清空背包槽位（0-45 覆盖背包、盔甲与副手）。 */
    void clearInventory(LimboPlayer limboPlayer) {
        VirtualItem air = this.factory.getItem("minecraft:air");
        if (air == null) {
            return;
        }
        for (int slot = 0; slot <= 45; slot++) {
            limboPlayer.setInventory(slot, air, 0, 0, (CompoundBinaryTag) null);
        }
    }

    private RegisteredServer resolveReturnServer(RegisteredServer origin) {
        if (origin != null) {
            Optional<RegisteredServer> current = this.proxy.getServer(origin.getServerInfo().getName());
            if (current.isPresent()) {
                return current.get();
            }
        }
        for (String name : this.proxy.getConfiguration().getAttemptConnectionOrder()) {
            Optional<RegisteredServer> fallback = this.proxy.getServer(name);
            if (fallback.isPresent()) {
                return fallback.get();
            }
        }
        return null;
    }

    private void send(Player player, String body) {
        player.sendMessage(Text.color(this.config.settings().messages().prefix() + body));
    }

    private static String serverName(RegisteredServer server) {
        return server == null ? "未知" : server.getServerInfo().getName();
    }
}
