package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.sound.SoundManager;
import cn.drcomo.corelib.util.ColorUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.config.GuiActionDefinition;
import cn.drcomo.drcomoguibinder.config.GuiActionType;
import cn.drcomo.drcomoguibinder.config.GuiDefinition;
import cn.drcomo.drcomoguibinder.config.GuiItemDefinition;
import cn.drcomo.corelib.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 根据动作定义执行对应的业务逻辑。
 */
public final class GuiActionExecutor {

    private final DebugUtil logger;
    private final MessageService messageService;
    private final PlaceholderAPIUtil placeholderUtil;
    private final SoundManager soundManager;
    private final GuiEngine guiEngine;

    public GuiActionExecutor(DebugUtil logger,
                             MessageService messageService,
                             PlaceholderAPIUtil placeholderUtil,
                             SoundManager soundManager,
                             GuiEngine guiEngine) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.placeholderUtil = Objects.requireNonNull(placeholderUtil, "placeholderUtil");
        this.soundManager = Objects.requireNonNull(soundManager, "soundManager");
        this.guiEngine = Objects.requireNonNull(guiEngine, "guiEngine");
    }

    public void execute(Player player,
                        GuiDefinition definition,
                        GuiItemDefinition item,
                        List<GuiActionDefinition> actions,
                        Map<String, String> basePlaceholders) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>(basePlaceholders);
        placeholders.put("menu", definition.getId());
        placeholders.put("item", item.getId());
        placeholders.put("player", player.getName());

        for (GuiActionDefinition action : actions) {
            try {
                executeSingle(player, definition, action, placeholders);
            } catch (Exception ex) {
                logger.error("执行菜单动作时发生异常: " + ex.getMessage(), ex);
            }
        }
    }

    private void executeSingle(Player player,
                               GuiDefinition definition,
                               GuiActionDefinition action,
                               Map<String, String> placeholders) {
        GuiActionType type = action.getType();
        if (type == null) {
            return;
        }
        switch (type) {
            case MESSAGE -> sendMessage(player, action.getMessageKey(), placeholders);
            case MESSAGE_TEXT -> sendMessageTemplate(player, action.getMessageText(), placeholders);
            case PLAYER_COMMAND -> runCommandAsPlayer(player, action.getCommand(), placeholders);
            case CONSOLE_COMMAND -> runCommandAsConsole(player, action.getCommand(), placeholders);
            case OPEN -> openOtherMenu(player, action.getTargetMenu(), placeholders);
            case CLOSE -> guiEngine.closeForPlayer(player, true);
            case SOUND -> playSound(player, action.getSoundKey(), definition);
            default -> logger.warn("未识别的动作类型: " + type);
        }
    }

    private void sendMessage(Player player, String key, Map<String, String> placeholders) {
        if (key == null || key.isEmpty()) {
            logger.warn("动作缺少消息键，已跳过。");
            return;
        }
        messageService.send(player, key, placeholders);
    }

    private void sendMessageTemplate(Player player, String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return;
        }
        String parsed = placeholderUtil.parse(player, template, placeholders);
        parsed = ColorUtil.translateColors(parsed);
        player.sendMessage(parsed);
    }

    private void runCommandAsPlayer(Player player, String command, Map<String, String> placeholders) {
        String parsed = parseCommand(player, command, placeholders);
        if (parsed == null || parsed.isEmpty()) {
            return;
        }
        player.performCommand(parsed.startsWith("/") ? parsed.substring(1) : parsed);
    }

    private void runCommandAsConsole(Player player, String command, Map<String, String> placeholders) {
        String parsed = parseCommand(player, command, placeholders);
        if (parsed == null || parsed.isEmpty()) {
            return;
        }
        CommandSender console = Bukkit.getConsoleSender();
        boolean success = Bukkit.dispatchCommand(console, parsed.startsWith("/") ? parsed.substring(1) : parsed);
        if (!success) {
            messageService.send(player, "action.command-failed", Map.of("command", parsed));
        }
    }

    private String parseCommand(Player player, String command, Map<String, String> placeholders) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        return placeholderUtil.parse(player, command, placeholders);
    }

    private void openOtherMenu(Player player, String target, Map<String, String> placeholders) {
        if (target == null || target.isEmpty()) {
            logger.warn("open 动作缺少 target，已忽略。");
            return;
        }
        boolean opened = guiEngine.openMenu(player, target, placeholders);
        if (!opened) {
            messageService.send(player, "action.open-target-missing", Map.of("id", target));
        }
    }

    private void playSound(Player player, String key, GuiDefinition definition) {
        String actualKey = (key == null || key.isEmpty()) ? definition.getOpenSound() : key;
        if (actualKey == null || actualKey.isEmpty()) {
            return;
        }
        soundManager.playSound(player, actualKey);
    }
}
