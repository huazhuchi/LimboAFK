package cn.basiker.limboafk.command;

import cn.basiker.limboafk.afk.AfkManager;
import cn.basiker.limboafk.config.ConfigManager;
import cn.basiker.limboafk.config.Settings;
import cn.basiker.limboafk.limbo.LimboService;
import cn.basiker.limboafk.util.Text;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

/** /limbo reload：重载配置并重建 Limbo。 */
public final class LimboCommand implements SimpleCommand {

    private final Logger logger;
    private final ConfigManager config;
    private final LimboService limboService;
    private final AfkManager afkManager;

    public LimboCommand(Logger logger, ConfigManager config, LimboService limboService, AfkManager afkManager) {
        this.logger = logger;
        this.config = config;
        this.limboService = limboService;
        this.afkManager = afkManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        Settings settings = this.config.settings();
        if (!settings.enabled() || !settings.commands().limboEnabled()) {
            reply(source, settings, settings.messages().commandDisabled());
            return;
        }
        if (!source.hasPermission(settings.permissions().reload())) {
            reply(source, settings, settings.messages().noPermission());
            return;
        }

        String[] args = invocation.arguments();
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            reply(source, settings, settings.messages().usage());
            return;
        }

        try {
            this.config.reload();
            this.limboService.rebuild();
            this.afkManager.restart();
            reply(source, this.config.settings(), this.config.settings().messages().reloaded());
            this.logger.info("{} 重载了 LimboAFK 配置", sourceName(source));
        } catch (Exception exception) {
            reply(source, settings, settings.messages().reloadFailed());
            this.logger.error("LimboAFK 配置重载失败", exception);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private static String sourceName(CommandSource source) {
        return source instanceof Player player ? player.getUsername() : "控制台";
    }

    private static void reply(CommandSource source, Settings settings, String body) {
        source.sendMessage(Text.color(settings.messages().prefix() + body));
    }
}
