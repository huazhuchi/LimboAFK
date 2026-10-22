package cn.basiker.limboafk.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** 配置文本转 Adventure 组件。 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** 同时兼容 & 与 § 颜色码。 */
    public static Component color(String text) {
        return LEGACY.deserialize(text.replace('&', '§'));
    }

    private Text() {
    }
}
