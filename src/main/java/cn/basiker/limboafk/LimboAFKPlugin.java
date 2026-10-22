package cn.basiker.limboafk;

import cn.basiker.limboafk.afk.AfkManager;
import cn.basiker.limboafk.command.AfkCommand;
import cn.basiker.limboafk.command.LimboCommand;
import cn.basiker.limboafk.config.ConfigManager;
import cn.basiker.limboafk.config.Settings;
import cn.basiker.limboafk.limbo.LimboService;
import cn.basiker.limboafk.listener.ActivityListener;
import cn.basiker.limboafk.listener.ConnectionListener;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.slf4j.Logger;

/** LimboAFK：AFK 超时后将玩家分流到 LimboAPI 虚拟虚空。 */
@Plugin(
        id = "limboafk",
        name = "LimboAFK",
        version = "1.0.0",
        description = "LimboAPI 的 Velocity AFK 系统",
        authors = {"basiker"},
        dependencies = {@Dependency(id = "limboapi")}
)
public final class LimboAFKPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private LimboService limboService;
    private AfkManager afkManager;

    @Inject
    public LimboAFKPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    // 最低优先级 = 最后执行：确保 LimboAPI 已完成自身的 ProxyInitialize 初始化后再创建 Limbo
    @Subscribe(priority = Short.MIN_VALUE)
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.configManager = new ConfigManager(this.dataDirectory, this.logger);
        try {
            this.configManager.reload();
        } catch (Exception exception) {
            this.logger.error("读取 config.yml 失败，LimboAFK 不会启用 Q201467607", exception);
            return;
        }

        try {
            this.limboService = new LimboService(this.server, this.logger, this.configManager);
            this.limboService.rebuild();
        } catch (Throwable throwable) {
            this.logger.error("Limbo 初始化失败（请检查 LimboAPI 是否安装且版本匹配 Q201467607）", throwable);
            return;
        }

        this.afkManager = new AfkManager(this, this.server, this.logger, this.configManager, this.limboService);
        this.server.getEventManager().register(this, new ConnectionListener(this.afkManager));
        this.server.getEventManager().register(this, new ActivityListener(this.afkManager));
        this.registerCommands();
        this.afkManager.restart();

        Settings settings = this.configManager.settings();
        if (!settings.enabled()) {
            this.logger.warn("LimboAFK 在配置中被禁用，指令会提示已禁用 Q201467607");
        }
        this.logger.info("LimboAFK 已启用：AFK 超时 {} 秒，提前 {} 秒警告，扫描周期 {} 秒 Q201467607",
                settings.afkTimeout(), settings.warningBefore(), settings.checkInterval());
    }

    private void registerCommands() {
        CommandManager commandManager = this.server.getCommandManager();
        commandManager.register(
                commandManager.metaBuilder("afk").plugin(this).build(),
                new AfkCommand(this.configManager, this.afkManager)
        );
        commandManager.register(
                commandManager.metaBuilder("limbo").plugin(this).build(),
                new LimboCommand(this.logger, this.configManager, this.limboService, this.afkManager)
        );
    }
}
