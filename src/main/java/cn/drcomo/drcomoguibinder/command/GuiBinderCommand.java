package cn.drcomo.drcomoguibinder.command;

import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.drcomoguibinder.config.PluginConfig;
import cn.drcomo.drcomoguibinder.gui.GuiEngine;
import cn.drcomo.drcomoguibinder.DrcomoGuiBinder;
import cn.drcomo.drcomoguibinder.config.GuiDefinition;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统一处理 /guibinder 指令。
 */
public final class GuiBinderCommand implements CommandExecutor, TabCompleter {

    private final DrcomoGuiBinder plugin;
    private final MessageService messageService;
    private final GuiEngine guiEngine;
    private final PluginConfig pluginConfig;
    public GuiBinderCommand(DrcomoGuiBinder plugin,
                            MessageService messageService,
                            GuiEngine guiEngine,
                            PluginConfig pluginConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.guiEngine = Objects.requireNonNull(guiEngine, "guiEngine");
        this.pluginConfig = Objects.requireNonNull(pluginConfig, "pluginConfig");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "open" -> handleOpen(sender, args);
            case "list" -> handleList(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(pluginConfig.getPermissions().reloadPermission())) {
            messageService.send(sender, "command.no-permission", Map.of());
            return;
        }
        long start = System.currentTimeMillis();
        plugin.reloadEverything();
        long cost = System.currentTimeMillis() - start;
        messageService.send(sender, "command.reload-success", Map.of("time", String.valueOf(cost)));
        if (pluginConfig.getCommandSettings().notifyReloadSuccess()) {
            String notice = messageService.parseWithDelimiter("command.reload-success", null, Map.of("time", String.valueOf(cost)), "{", "}");
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission(pluginConfig.getPermissions().reloadPermission()))
                    .forEach(p -> p.sendMessage(notice));
        }
    }

    private void handleOpen(CommandSender sender, String[] args) {
        if (!sender.hasPermission(pluginConfig.getPermissions().openPermission())) {
            messageService.send(sender, "command.no-permission", Map.of());
            return;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String menuId = args[1].toLowerCase(Locale.ROOT);
        if (args.length >= 3) {
            if (!sender.hasPermission(pluginConfig.getPermissions().openOthersPermission())) {
                messageService.send(sender, "command.no-permission", Map.of());
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                messageService.send(sender, "command.invalid-target", Map.of("player", args[2]));
                return;
            }
            boolean opened = guiEngine.openMenu(target, menuId);
            if (!opened) {
                messageService.send(sender, "gui.missing", Map.of("id", menuId));
                return;
            }
            messageService.send(sender, "command.open-other", Map.of("target", target.getName(), "id", menuId));
            return;
        }
        if (!(sender instanceof Player player)) {
            if (!pluginConfig.getCommandSettings().allowConsoleOpen()) {
                messageService.send(sender, "command.player-only", Map.of());
                return;
            }
            messageService.send(sender, "command.unknown", Map.of());
            return;
        }
        boolean opened = guiEngine.openMenu(player, menuId);
        if (!opened) {
            messageService.send(sender, "gui.missing", Map.of("id", menuId));
            return;
        }
        messageService.send(sender, "command.open-self", Map.of("id", menuId));
    }

    private void handleList(CommandSender sender) {
        Map<String, GuiDefinition> defs = guiEngine.getDefinitions();
        if (defs.isEmpty()) {
            messageService.send(sender, "gui.load-start", Map.of());
            return;
        }
        messageService.send(sender, "command.list-header", Map.of());
        defs.forEach((id, definition) ->
                messageService.send(sender, "command.list-entry", Map.of(
                        "id", id,
                        "title", definition.getTitle()
                )));
    }

    private void sendHelp(CommandSender sender) {
        messageService.sendList(sender, messageService.getList("command.help"), Map.of(), "{", "}");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("list", "open", "reload"), new ArrayList<>());
        }
        if (args.length == 2 && "open".equalsIgnoreCase(args[0])) {
            return StringUtil.copyPartialMatches(args[1], new ArrayList<>(guiEngine.getDefinitions().keySet()), new ArrayList<>());
        }
        if (args.length == 3 && "open".equalsIgnoreCase(args[0])) {
            return StringUtil.copyPartialMatches(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
