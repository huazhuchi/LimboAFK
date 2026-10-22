package cn.basiker.limboafk.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.elytrium.limboapi.api.chunk.Dimension;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/** config.yml 的加载、默认文件释放与热重载。 */
public final class ConfigManager {

    private final Path file;
    private final Logger logger;
    private volatile Settings settings;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.file = dataDirectory.resolve("config.yml");
        this.logger = logger;
    }

    public Settings settings() {
        return this.settings;
    }

    /** 读取配置；首次启动时释放内置模板。 */
    public void reload() throws IOException {
        if (Files.notExists(this.file)) {
            this.saveDefault();
        }

        ConfigurationNode root = YamlConfigurationLoader.builder().path(this.file).build().load();
        this.settings = new Settings(
                root.node("enabled").getBoolean(true),
                root.node("auto-afk").getBoolean(true),
                root.node("afk-timeout").getInt(300),
                root.node("warning-before").getInt(30),
                root.node("check-interval").getInt(5),
                new Settings.CommandSettings(
                        root.node("commands", "afk-enabled").getBoolean(true),
                        root.node("commands", "limbo-enabled").getBoolean(true)
                ),
                new Settings.PermissionSettings(
                        permission(root, "afk", "limboafk.afk"),
                        permission(root, "reload", "limboafk.reload"),
                        permission(root, "bypass", "limboafk.bypass")
                ),
                new Settings.MessageSettings(
                        root.node("messages", "prefix").getString("&8[&bLimboAFK&8] &7"),
                        root.node("messages", "afk-marked").getString("&e已标记为 AFK，&c%seconds% &e秒后进入 Limbo。"),
                        root.node("messages", "afk-cancelled").getString("&a已取消 AFK 状态。"),
                        root.node("messages", "afk-warning").getString("&e检测到你长时间未操作，&c%seconds% &e秒后将进入 Limbo。"),
                        root.node("messages", "limbo-title").getString("&c&lAFK"),
                        root.node("messages", "limbo-subtitle").getString("&7你已被传送到 Limbo"),
                        root.node("messages", "limbo-action-bar").getString("&e移动、转头或输入指令即可返回"),
                        root.node("messages", "limbo-chat").getString("&7你因长时间未操作被传送到 Limbo。\n&e移动、转头、聊天或输入指令即可返回原服务器。"),
                        root.node("messages", "limbo-returned").getString("&a已返回服务器 &f%server%&a。"),
                        root.node("messages", "no-server").getString("&c原服务器不可用，且没有可用的后备服务器。"),
                        root.node("messages", "limbo-failed").getString("&c进入 Limbo 失败，请稍后再试。"),
                        root.node("messages", "reloaded").getString("&a配置已重载。"),
                        root.node("messages", "reload-failed").getString("&c重载失败，请查看控制台。"),
                        root.node("messages", "no-permission").getString("&c你没有权限执行此操作。"),
                        root.node("messages", "command-disabled").getString("&c该指令已在配置中禁用。"),
                        root.node("messages", "players-only").getString("&c该指令只能由玩家执行。"),
                        root.node("messages", "usage").getString("&e用法：/limbo reload")
                ),
                new Settings.LimboSettings(
                        dimension(root.node("limbo", "dimension").getString("THE_END")),
                        root.node("limbo", "spawn-x").getDouble(0.0),
                        root.node("limbo", "spawn-y").getDouble(100.0),
                        root.node("limbo", "spawn-z").getDouble(0.0),
                        (float) root.node("limbo", "yaw").getDouble(0.0),
                        (float) root.node("limbo", "pitch").getDouble(0.0),
                        root.node("limbo", "empty-inventory").getBoolean(true),
                        root.node("limbo", "disable-damage").getBoolean(true),
                        root.node("limbo", "disable-hunger").getBoolean(true),
                        root.node("limbo", "freeze-falling").getBoolean(true)
                )
        );
    }

    private static String permission(ConfigurationNode root, String key, String fallback) {
        String value = root.node("permissions", key).getString(fallback);
        return value == null || value.isBlank() ? fallback : value;
    }

    private Dimension dimension(String name) {
        try {
            return Dimension.valueOf((name == null ? "THE_END" : name).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            this.logger.warn("未知的 Limbo 维度 '{}'，已回退到 THE_END", name);
            return Dimension.THE_END;
        }
    }

    private void saveDefault() throws IOException {
        Files.createDirectories(this.file.getParent());
        try (InputStream in = ConfigManager.class.getResourceAsStream("/config.yml")) {
            if (in == null) {
                throw new IOException("内置 config.yml 缺失");
            }
            Files.copy(in, this.file);
        }
    }
}
