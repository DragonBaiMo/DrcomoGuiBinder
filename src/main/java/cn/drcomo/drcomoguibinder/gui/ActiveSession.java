package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.drcomoguibinder.config.GuiDefinition;

import java.util.Map;
import java.util.UUID;

/**
 * 运行时会话信息，用于追踪玩家当前打开的菜单及其占位符上下文。
 */
public final class ActiveSession {

    private final String sessionId;
    private final UUID playerId;
    private final GuiDefinition definition;
    private final Map<String, String> placeholders;

    public ActiveSession(String sessionId,
                         UUID playerId,
                         GuiDefinition definition,
                         Map<String, String> placeholders) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.definition = definition;
        this.placeholders = placeholders;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public GuiDefinition getDefinition() {
        return definition;
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }
}
