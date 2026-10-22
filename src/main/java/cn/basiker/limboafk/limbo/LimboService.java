package cn.basiker.limboafk.limbo;

import cn.basiker.limboafk.config.ConfigManager;
import cn.basiker.limboafk.config.Settings;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import org.slf4j.Logger;

/** 负责创建并持有一个可复用的 Limbo 实例，以及把玩家送入该实例。 */
public final class LimboService {

    private final Logger logger;
    private final ConfigManager config;
    private final LimboFactory factory;

    private volatile Limbo limbo;

    public LimboService(ProxyServer proxy, Logger logger, ConfigManager config) {
        this.logger = logger;
        this.config = config;
        this.factory = proxy.getPluginManager().getPlugin("limboapi")
                .flatMap(PluginContainer::getInstance)
                .filter(LimboFactory.class::isInstance)
                .map(LimboFactory.class::cast)
                .orElseThrow(() -> new IllegalStateException("未找到 LimboAPI，请先安装该插件"));
    }

    public LimboFactory factory() {
        return this.factory;
    }

    /** 按当前配置重建 Limbo；旧实例会在其中玩家离开后自行释放。 */
    public void rebuild() {
        Settings.LimboSettings settings = this.config.settings().limbo();
        VirtualWorld world = this.factory.createVirtualWorld(
                settings.dimension(),
                settings.spawnX(), settings.spawnY(), settings.spawnZ(),
                settings.yaw(), settings.pitch()
        );
        // 只创建一个空区块：客户端能完成地形加载，同时保持纯虚空
        world.getChunkOrNew((int) Math.floor(settings.spawnX()) >> 4, (int) Math.floor(settings.spawnZ()) >> 4);

        Limbo newLimbo = this.factory.createLimbo(world)
                .setName("LimboAFK")
                .setShouldRespawn(true)
                .setShouldRejoin(true)
                .setReducedDebugInfo(true);

        Limbo oldLimbo = this.limbo;
        this.limbo = newLimbo;
        if (oldLimbo != null) {
            oldLimbo.dispose();
        }

        this.logger.info("Limbo 已就绪：维度 {}，出生点 ({}, {}, {})",
                settings.dimension(), settings.spawnX(), settings.spawnY(), settings.spawnZ());
    }

    public void spawn(Player player, LimboSessionHandler handler) {
        Limbo current = this.limbo;
        if (current == null) {
            throw new IllegalStateException("Limbo 尚未初始化");
        }
        current.spawnPlayer(player, handler);
        this.logger.debug("正在把玩家 {} 送入 Limbo", player.getUsername());
    }
}
