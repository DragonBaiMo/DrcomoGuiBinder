package cn.drcomo.drcomoguibinder.config;

import java.util.Map;
import java.util.Objects;

/**
 * 描述单个槽位点击后要执行的动作定义。
 */
public final class GuiActionDefinition {

    private final GuiActionType type;
    private final String messageKey;
    private final String messageText;
    private final String command;
    private final String targetMenu;
    private final String soundKey;

    private GuiActionDefinition(GuiActionType type,
                                String messageKey,
                                String messageText,
                                String command,
                                String targetMenu,
                                String soundKey) {
        this.type = type;
        this.messageKey = messageKey;
        this.messageText = messageText;
        this.command = command;
        this.targetMenu = targetMenu;
        this.soundKey = soundKey;
    }

    public static GuiActionDefinition fromMap(Map<String, Object> raw, String menuId, String itemId) {
        Objects.requireNonNull(raw, "raw");
        String typeRaw = String.valueOf(raw.getOrDefault("type", "")).trim().toUpperCase();
        GuiActionType type;
        try {
            type = GuiActionType.valueOf(typeRaw);
        } catch (IllegalArgumentException ex) {
            // 兼容 YAML 中常见的短横线写法
            if ("PLAYER-COMMAND".equalsIgnoreCase(typeRaw) || "PLAYER_COMMAND".equalsIgnoreCase(typeRaw)) {
                type = GuiActionType.PLAYER_COMMAND;
            } else if ("CONSOLE-COMMAND".equalsIgnoreCase(typeRaw) || "CONSOLE_COMMAND".equalsIgnoreCase(typeRaw)) {
                type = GuiActionType.CONSOLE_COMMAND;
            } else if ("MESSAGE-TEXT".equalsIgnoreCase(typeRaw) || "MESSAGE_TEXT".equalsIgnoreCase(typeRaw)) {
                type = GuiActionType.MESSAGE_TEXT;
            } else {
                throw new IllegalArgumentException("动作类型无效: " + typeRaw + " (菜单=" + menuId + ", 物品=" + itemId + ")");
            }
        }

        String messageKey = valueOrNull(raw.get("key"));
        String messageText = valueOrNull(raw.get("text"));
        String command = valueOrNull(raw.get("command"));
        String targetMenu = valueOrNull(raw.get("target"));
        String soundKey = valueOrNull(raw.get("sound"));
        return new GuiActionDefinition(type, messageKey, messageText, command, targetMenu, soundKey);
    }

    private static String valueOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    public GuiActionType getType() {
        return type;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getMessageText() {
        return messageText;
    }

    public String getCommand() {
        return command;
    }

    public String getTargetMenu() {
        return targetMenu;
    }

    public String getSoundKey() {
        return soundKey;
    }
}
