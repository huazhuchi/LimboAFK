package cn.basiker.limboafk.command;

import cn.basiker.limboafk.afk.AfkManager;
import cn.basiker.limboafk.config.ConfigManager;
import cn.basiker.limboafk.config.Settings;
import cn.basiker.limboafk.util.Text;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

/** /afk：手动开启或取消 AFK 标记。 */
public final class AfkCommand implements SimpleCommand {

    private final ConfigManager config;
    private final AfkManager afkManager;

    public AfkCommand(ConfigManager config, AfkManager afkManager) {
        this.config = config;
        this.afkManager = afkManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        Settings settings = this.config.settings();
        if (!(source instanceof Player player)) {
            reply(source, settings, settings.messages().playersOnly());
            return;
        }
        if (!settings.enabled() || !settings.commands().afkEnabled()) {
            reply(source, settings, settings.messages().commandDisabled());
            return;
        }
        if (!player.hasPermission(settings.permissions().afk())) {
            reply(source, settings, settings.messages().noPermission());
            return;
        }
        this.afkManager.markAfk(player);
    }

    /** 权限在 execute 中手动判断，以便返回可配置的中文提示。 */
    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private static void reply(CommandSource source, Settings settings, String body) {
        source.sendMessage(Text.color(settings.messages().prefix() + body));
    }
}
