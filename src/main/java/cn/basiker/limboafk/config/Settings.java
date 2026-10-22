package cn.basiker.limboafk.config;

import net.elytrium.limboapi.api.chunk.Dimension;

/** 配置快照：不可变，重载时整体替换，避免运行中读到半新半旧的值。 */
public record Settings(
        boolean enabled,
        boolean autoAfk,
        int afkTimeout,
        int warningBefore,
        int checkInterval,
        CommandSettings commands,
        PermissionSettings permissions,
        MessageSettings messages,
        LimboSettings limbo
) {

    public Settings {
        afkTimeout = Math.max(1, afkTimeout);
        // 警告必须发生在超时之前
        warningBefore = Math.max(0, Math.min(warningBefore, afkTimeout - 1));
        checkInterval = Math.max(1, checkInterval);
    }

    public record CommandSettings(boolean afkEnabled, boolean limboEnabled) {
    }

    public record PermissionSettings(String afk, String reload, String bypass) {
    }

    public record MessageSettings(
            String prefix,
            String afkMarked,
            String afkCancelled,
            String afkWarning,
            String limboTitle,
            String limboSubtitle,
            String limboActionBar,
            String limboChat,
            String limboReturned,
            String noServer,
            String limboFailed,
            String reloaded,
            String reloadFailed,
            String noPermission,
            String commandDisabled,
            String playersOnly,
            String usage
    ) {
    }

    public record LimboSettings(
            Dimension dimension,
            double spawnX,
            double spawnY,
            double spawnZ,
            float yaw,
            float pitch,
            boolean emptyInventory,
            boolean disableDamage,
            boolean disableHunger,
            boolean freezeFalling
    ) {
    }
}
