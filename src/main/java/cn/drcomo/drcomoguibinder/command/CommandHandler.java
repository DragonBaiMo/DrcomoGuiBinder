package cn.drcomo.drcomoguibinder.command;

import cn.drcomo.corelib.async.TaskQueueStatus;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.bind.Binding;
import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.drcomoguibinder.config.GuiConfigService;
import cn.drcomo.drcomoguibinder.config.model.MainGuiDef;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
import cn.drcomo.drcomoguibinder.event.GuiBindEvent;
import cn.drcomo.drcomoguibinder.event.GuiClearEvent;
import cn.drcomo.drcomoguibinder.gui.MainGuiController;
import cn.drcomo.drcomoguibinder.papi.BinderPlaceholderExpansion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 主指令处理。
 */
public final class CommandHandler implements CommandExecutor, TabCompleter {

  private final Plugin plugin;
  private final GuiConfigService configService;
  private final BindingService bindingService;
  private final MainGuiController mainGuiController;
  private final MessageService messageService;
  private final BinderPlaceholderExpansion placeholderExpansion;
  private final DebugUtil logger;
  private final PlaceholderAPIUtil placeholderUtil;
  private final boolean resolveAtBind;

  public CommandHandler(Plugin plugin, GuiConfigService configService,
      BindingService bindingService, MainGuiController mainGuiController,
      MessageService messageService, BinderPlaceholderExpansion placeholderExpansion,
      DebugUtil logger, PlaceholderAPIUtil placeholderUtil, boolean resolveAtBind) {
    this.plugin = plugin;
    this.configService = configService;
    this.bindingService = bindingService;
    this.mainGuiController = mainGuiController;
    this.messageService = messageService;
    this.placeholderExpansion = placeholderExpansion;
    this.logger = logger;
    this.placeholderUtil = placeholderUtil;
    this.resolveAtBind = resolveAtBind;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    return switch (sub) {
      case "open" -> handleOpen(sender, args);
      case "clear" -> handleClear(sender, args);
      case "bind" -> handleBind(sender, args);
      case "list" -> handleList(sender, args);
      case "reload" -> handleReload(sender);
      case "save" -> handleSave(sender);
      case "diagnostics" -> handleDiagnostics(sender);
      case "help" -> {
        sendHelp(sender);
        yield true;
      }
      default -> {
        sendHelp(sender);
        yield true;
      }
    };
  }

  private boolean handleOpen(CommandSender sender, String[] args) {
    if (args.length < 2) {
      messageService.send(sender, "messages.command-usage-open");
      return true;
    }
    String mainId = args[1];
    if (configService.getMain(mainId) == null) {
      messageService.send(sender, "messages.main-not-found", Map.of("id", mainId));
      return true;
    }
    Player target;
    if (args.length >= 3) {
      if (!sender.hasPermission("drcomo.guibinder.open.others")) {
        messageService.send(sender, "messages.no-permission");
        return true;
      }
      target = Bukkit.getPlayer(args[2]);
      if (target == null) {
        messageService.send(sender, "messages.player-offline", Map.of("player", args[2]));
        return true;
      }
    } else {
      if (!(sender instanceof Player player)) {
        messageService.send(sender, "messages.need-player");
        return true;
      }
      target = player;
      if (!sender.hasPermission("drcomo.guibinder.open")) {
        messageService.send(sender, "messages.no-permission");
        return true;
      }
    }
    mainGuiController.openMain(target, mainId);
    if (!target.equals(sender)) {
      messageService.send(sender, "messages.open-others", Map.of("player", target.getName(), "main", mainId));
    }
    return true;
  }

  private boolean handleClear(CommandSender sender, String[] args) {
    if (!sender.hasPermission("drcomo.guibinder.clear")) {
      messageService.send(sender, "messages.no-permission");
      return true;
    }
    if (args.length < 3) {
      messageService.send(sender, "messages.command-usage-clear");
      return true;
    }
    String mainId = args[1];
    MainGuiDef mainDef = configService.getMain(mainId);
    if (mainDef == null) {
      messageService.send(sender, "messages.main-not-found", Map.of("id", mainId));
      return true;
    }
    int slot;
    try {
      slot = Integer.parseInt(args[2]);
    } catch (NumberFormatException ex) {
      messageService.send(sender, "messages.invalid-slot", Map.of("slot", args[2]));
      return true;
    }
    if (mainDef.findSlot(slot) == null) {
      messageService.send(sender, "messages.slot-not-configured",
          Map.of("main", mainId, "slot", String.valueOf(slot)));
      return true;
    }
    OfflinePlayer target;
    if (args.length >= 4) {
      target = Bukkit.getOfflinePlayer(args[3]);
    } else if (sender instanceof Player player) {
      target = player;
    } else {
      messageService.send(sender, "messages.need-player");
      return true;
    }
    UUID uuid = target.getUniqueId();
    String targetName = target.getName() != null ? target.getName() : uuid.toString();
    bindingService.clear(uuid, mainId, slot).whenComplete((result, throwable) ->
        Bukkit.getScheduler().runTask(plugin, () -> {
          if (throwable != null) {
            logger.error("清除绑定失败", throwable);
            messageService.send(sender, "messages.clear-failed", Map.of());
            return;
          }
          if (!result) {
            messageService.send(sender, "messages.clear-empty", Map.of());
            return;
          }
          messageService.send(sender, "messages.clear-success",
              Map.of("player", targetName, "main", mainId, "slot", String.valueOf(slot)));
          Bukkit.getPluginManager().callEvent(new GuiClearEvent(
              sender instanceof Player p ? p : null, uuid, mainId, slot));
          if (target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null) {
              mainGuiController.refreshSlot(online, mainId, slot);
            }
          }
        }));
    return true;
  }

  private boolean handleBind(CommandSender sender, String[] args) {
    if (!sender.hasPermission("drcomo.guibinder.bind")) {
      messageService.send(sender, "messages.no-permission");
      return true;
    }
    if (args.length < 5) {
      messageService.send(sender, "messages.command-usage-bind");
      return true;
    }
    String mainId = args[1];
    MainGuiDef mainDef = configService.getMain(mainId);
    if (mainDef == null) {
      messageService.send(sender, "messages.main-not-found", Map.of("id", mainId));
      return true;
    }
    int slot;
    try {
      slot = Integer.parseInt(args[2]);
    } catch (NumberFormatException ex) {
      messageService.send(sender, "messages.invalid-slot", Map.of("slot", args[2]));
      return true;
    }
    String subId = args[3];
    String entryKey = args[4];
    OfflinePlayer target;
    if (args.length >= 6) {
      target = Bukkit.getOfflinePlayer(args[5]);
    } else if (sender instanceof Player player) {
      target = player;
    } else {
      messageService.send(sender, "messages.need-player");
      return true;
    }
    SubGuiDef sub = configService.getSub(subId);
    if (sub == null) {
      messageService.send(sender, "messages.sub-not-found", Map.of("id", subId));
      return true;
    }
    EntryDef entry = sub.findEntryByKey(entryKey);
    if (entry == null) {
      messageService.send(sender, "messages.entry-not-found", Map.of("key", entryKey));
      return true;
    }
    UUID uuid = target.getUniqueId();
    Player targetPlayer = target.getPlayer();
    String valueToStore = resolveAtBind
        ? placeholderUtil.parse(targetPlayer, entry.getValue())
        : entry.getValue();
    GuiBindEvent event = new GuiBindEvent(targetPlayer, mainId, slot, subId, entryKey, valueToStore);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      messageService.send(sender, "messages.bind-cancelled", Map.of());
      return true;
    }
    Binding binding = Binding.now(uuid, event.getMainId(), event.getSlot(), event.getSubId(),
        event.getEntryKey(), event.getEntryValue());
    bindingService.bind(binding).whenComplete((ignored, throwable) ->
        Bukkit.getScheduler().runTask(plugin, () -> {
          if (throwable != null) {
            logger.error("手动绑定失败", throwable);
            messageService.send(sender, "messages.bind-failed", Map.of());
            return;
          }
          Map<String, String> placeholders = Map.of(
              "key", binding.getEntryKey(),
              "value", binding.getEntryValue()
          );
          messageService.send(sender, "messages.bind-success", placeholders);
          if (targetPlayer != null) {
            if (!targetPlayer.equals(sender)) {
              messageService.send(targetPlayer, "messages.bind-success", placeholders);
            }
            mainGuiController.refreshSlot(targetPlayer, binding.getMainId(), binding.getSlot());
          }
        }));
    return true;
  }

  private boolean handleList(CommandSender sender, String[] args) {
    OfflinePlayer target;
    if (args.length >= 2) {
      target = Bukkit.getOfflinePlayer(args[1]);
    } else if (sender instanceof Player player) {
      target = player;
    } else {
      messageService.send(sender, "messages.need-player");
      return true;
    }
    String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
    List<Binding> bindings = bindingService.listPlayer(target.getUniqueId());
    bindings.sort(Comparator.comparing(Binding::getMainId).thenComparingInt(Binding::getSlot));
    messageService.send(sender, "messages.list-header", Map.of("player", targetName));
    if (bindings.isEmpty()) {
      messageService.send(sender, "messages.list-empty");
      return true;
    }
    for (Binding binding : bindings) {
      messageService.send(sender, "messages.list-entry",
          Map.of("main", binding.getMainId(), "slot", String.valueOf(binding.getSlot()),
              "key", binding.getEntryKey(), "value", binding.getEntryValue()));
    }
    return true;
  }

  private boolean handleReload(CommandSender sender) {
    if (!sender.hasPermission("drcomo.guibinder.reload")) {
      messageService.send(sender, "messages.no-permission");
      return true;
    }
    configService.reloadAll();
    messageService.reloadLanguages();
    placeholderExpansion.registerAll();
    messageService.send(sender, "messages.reload-success");
    return true;
  }

  private boolean handleSave(CommandSender sender) {
    if (!sender.hasPermission("drcomo.guibinder.save")) {
      messageService.send(sender, "messages.no-permission");
      return true;
    }
    bindingService.flush().whenComplete((v, ex) -> {
      if (ex != null) {
        logger.error("刷新写队列失败", ex);
        messageService.send(sender, "messages.save-failed");
        return;
      }
      messageService.send(sender, "messages.save-success");
    });
    return true;
  }

  private boolean handleDiagnostics(CommandSender sender) {
    if (!sender.hasPermission("drcomo.guibinder.diagnostics")) {
      messageService.send(sender, "messages.no-permission");
      return true;
    }
    int cacheSize = bindingService.getCacheSize();
    int pending = bindingService.getPendingWrites();
    TaskQueueStatus status = bindingService.getAsyncManager().getQueueStatus();
    messageService.send(sender, "messages.diagnostics",
        Map.of("cache", String.valueOf(cacheSize), "pending", String.valueOf(pending),
            "queue", String.valueOf(status.getTotal())));
    return true;
  }

  private void sendHelp(CommandSender sender) {
    messageService.send(sender, "messages.help-header");
    messageService.send(sender, "messages.help-open");
    messageService.send(sender, "messages.help-clear");
    messageService.send(sender, "messages.help-bind");
    messageService.send(sender, "messages.help-list");
    messageService.send(sender, "messages.help-reload");
    messageService.send(sender, "messages.help-save");
    messageService.send(sender, "messages.help-diagnostics");
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias,
      String[] args) {
    if (args.length == 1) {
      return filter(List.of("open", "clear", "bind", "list", "reload", "save", "diagnostics",
          "help"), args[0]);
    }
    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "open" -> {
        if (args.length == 2) {
          return filter(new ArrayList<>(configService.getMainIds()), args[1]);
        }
        if (args.length == 3) {
          return filter(onlinePlayers(), args[2]);
        }
      }
      case "clear", "bind" -> {
        if (args.length == 2) {
          return filter(new ArrayList<>(configService.getMainIds()), args[1]);
        }
        if (args.length == 3) {
          MainGuiDef def = configService.getMain(args[1]);
          if (def != null) {
            List<String> slots = def.getSlots().stream()
                .map(slotDef -> String.valueOf(slotDef.getSlot()))
                .toList();
            return filter(new ArrayList<>(slots), args[2]);
          }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("bind")) {
          return filter(new ArrayList<>(configService.getSubIds()), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("clear")) {
          return filter(onlinePlayers(), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("bind")) {
          SubGuiDef sub = configService.getSub(args[3]);
          if (sub != null) {
            List<String> keys = sub.getEntries().stream()
                .map(EntryDef::getKey)
                .toList();
            return filter(new ArrayList<>(keys), args[4]);
          }
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("bind")) {
          return filter(onlinePlayers(), args[5]);
        }
      }
      case "list" -> {
        if (args.length == 2) {
          return filter(onlinePlayers(), args[1]);
        }
      }
      default -> {
      }
    }
    return Collections.emptyList();
  }

  private List<String> onlinePlayers() {
    List<String> players = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      players.add(player.getName());
    }
    return players;
  }

  private List<String> filter(List<String> source, String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      source.sort(String::compareToIgnoreCase);
      return source;
    }
    List<String> result = new ArrayList<>();
    for (String s : source) {
      if (s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
        result.add(s);
      }
    }
    result.sort(String::compareToIgnoreCase);
    return result;
  }
}
