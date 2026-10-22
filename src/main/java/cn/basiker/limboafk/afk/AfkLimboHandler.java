package cn.basiker.limboafk.afk;

import cn.basiker.limboafk.config.Settings;
import cn.basiker.limboafk.util.Text;
import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.title.Title;

/** Limbo 会话回调：只在虚空内触发，负责入场提示与“任意动作即返回”。 */
final class AfkLimboHandler implements LimboSessionHandler {

    /** 水平位移阈值（平方）：0.5 格。忽略 Y，避免冻结失效时下落导致误判。 */
    private static final double MOVE_THRESHOLD_SQUARED = 0.25D;
    /** 转头阈值：2 度。 */
    private static final float ROTATE_THRESHOLD = 2.0F;

    private final AfkManager manager;
    private final AfkSession session;
    private final Settings settings;

    private boolean spawned;
    private float lastYaw;
    private float lastPitch;

    AfkLimboHandler(AfkManager manager, AfkSession session, Settings settings) {
        this.manager = manager;
        this.session = session;
        this.settings = settings;
    }

    @Override
    public void onSpawn(Limbo server, LimboPlayer limboPlayer) {
        this.spawned = true;
        this.session.limboPlayer = limboPlayer;
        this.lastYaw = this.settings.limbo().yaw();
        this.lastPitch = this.settings.limbo().pitch();

        limboPlayer.setGameMode(this.gameMode());
        if (this.settings.limbo().freezeFalling()) {
            limboPlayer.disableFalling();
        }
        if (this.settings.limbo().emptyInventory()) {
            this.manager.clearInventory(limboPlayer);
        }

        Player player = limboPlayer.getProxyPlayer();
        Settings.MessageSettings messages = this.settings.messages();
        player.showTitle(Title.title(
                Text.color(messages.limboTitle()),
                Text.color(messages.limboSubtitle()),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));
        player.sendActionBar(Text.color(messages.limboActionBar()));
        player.sendMessage(Text.color(messages.prefix() + messages.limboChat()
                .replace("%player%", player.getUsername())
                .replace("%server%", this.originName())));
    }

    @Override
    public void onMove(double posX, double posY, double posZ) {
        if (!this.spawned) {
            return;
        }
        double deltaX = posX - this.settings.limbo().spawnX();
        double deltaY = posY - this.settings.limbo().spawnY();
        double deltaZ = posZ - this.settings.limbo().spawnZ();
        double distance = deltaX * deltaX + deltaZ * deltaZ;
        // 冻结坠落时玩家应静止悬浮，可安全地把纵向移动也视为有效动作；
        // 未冻结时忽略 Y，避免持续下落导致刚进入 Limbo 就被判定为移动。
        if (this.settings.limbo().freezeFalling()) {
            distance += deltaY * deltaY;
        }
        if (distance > MOVE_THRESHOLD_SQUARED) {
            this.manager.leaveLimbo(this.session, "移动");
        }
    }

    @Override
    public void onMove(double posX, double posY, double posZ, float yaw, float pitch) {
        this.onMove(posX, posY, posZ);
        this.onRotate(yaw, pitch);
    }

    @Override
    public void onRotate(float yaw, float pitch) {
        if (!this.spawned) {
            return;
        }
        float deltaYaw = Math.abs(yaw - this.lastYaw) % 360.0F;
        if (deltaYaw > 180.0F) {
            deltaYaw = 360.0F - deltaYaw;
        }
        float deltaPitch = Math.abs(pitch - this.lastPitch);
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        if (deltaYaw > ROTATE_THRESHOLD || deltaPitch > ROTATE_THRESHOLD) {
            this.manager.leaveLimbo(this.session, "转头");
        }
    }

    @Override
    public void onChat(String chat) {
        // 指令同样通过该回调到达（已带 "/"）
        this.manager.leaveLimbo(this.session, chat.startsWith("/") ? "指令" : "聊天");
    }

    @Override
    public void onDisconnect() {
        this.manager.onLimboDisconnect(this.session);
    }

    /** 关闭饥饿时使用旁观模式（客户端隐藏生命/饥饿条），否则冒险模式。 */
    private GameMode gameMode() {
        if (this.settings.limbo().disableHunger()) {
            return GameMode.SPECTATOR;
        }
        return this.settings.limbo().disableDamage() ? GameMode.ADVENTURE : GameMode.SURVIVAL;
    }

    private String originName() {
        return this.session.origin == null ? "未知" : this.session.origin.getServerInfo().getName();
    }
}
