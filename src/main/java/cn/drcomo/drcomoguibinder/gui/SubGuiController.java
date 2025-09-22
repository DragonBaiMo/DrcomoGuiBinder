package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.gui.PaginatedGui;
import cn.drcomo.corelib.gui.interfaces.ClickAction;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.bind.Binding;
import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.drcomoguibinder.config.GuiConfigService;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
import cn.drcomo.drcomoguibinder.event.GuiBindEvent;
import cn.drcomo.drcomoguibinder.gui.session.BindSession;
import cn.drcomo.drcomoguibinder.template.ItemTemplateRenderer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 管理 Sub GUI 的渲染与绑定逻辑。
 */
public final class SubGuiController extends PaginatedGui {

  private final GUISessionManager sessions;
  private final GuiActionDispatcher dispatcher;
  private final GuiConfigService configService;
  private final BindingService bindingService;
  private final ItemTemplateRenderer renderer;
  private final cn.drcomo.corelib.gui.session.PlayerSessionManager<BindSession> bindSessions;
  private final DebugUtil logger;
  private final cn.drcomo.corelib.message.MessageService messageService;
  private final PlaceholderAPIUtil placeholderUtil;
  private final boolean resolveValueAtBind;
  private MainGuiController mainGuiController;
  private final Plugin plugin;
  private final Map<UUID, String> currentSub = new ConcurrentHashMap<>();

  public SubGuiController(GUISessionManager sessions, GuiActionDispatcher dispatcher,
      GuiConfigService configService, BindingService bindingService,
      ItemTemplateRenderer renderer,
      cn.drcomo.corelib.gui.session.PlayerSessionManager<BindSession> bindSessions,
      DebugUtil logger, cn.drcomo.corelib.message.MessageService messageService,
      PlaceholderAPIUtil placeholderUtil, boolean resolveValueAtBind,
      MainGuiController mainGuiController, Plugin plugin) {
    super(sessions, dispatcher, 54, 45, 53);
    this.sessions = sessions;
    this.dispatcher = dispatcher;
    this.configService = configService;
    this.bindingService = bindingService;
    this.renderer = renderer;
    this.bindSessions = bindSessions;
    this.logger = logger;
    this.messageService = messageService;
    this.placeholderUtil = placeholderUtil;
    this.resolveValueAtBind = resolveValueAtBind;
    this.mainGuiController = mainGuiController;
    this.plugin = plugin;
  }

  public void setMainGuiController(MainGuiController mainGuiController) {
    this.mainGuiController = mainGuiController;
  }

  public void openSub(Player player, SubGuiDef subDef) {
    currentSub.put(player.getUniqueId(), subDef.getId());
    super.open(player, sessionId(player));
  }

  @Override
  protected int getTotalItemCount(Player player) {
    SubGuiDef def = currentDefinition(player);
    return def == null ? 0 : def.getEntries().size();
  }

  @Override
  protected Inventory createInventory(Player player) {
    SubGuiDef def = currentDefinition(player);
    int size = def == null ? 54 : def.getSize();
    String title = def == null ? "&c子界面缺失" : def.getTitle();
    return Bukkit.createInventory(player, size, cn.drcomo.corelib.color.ColorUtil.translateColors(title));
  }

  @Override
  protected void renderPage(Player player, Inventory inv, int page, int totalPages) {
    SubGuiDef def = currentDefinition(player);
    if (def == null) {
      inv.clear();
      return;
    }
    BindSession session = bindSessions.getSession(player);
    if (session == null) {
      messageService.send(player, "messages.session-missing", Map.of());
      player.closeInventory();
      return;
    }
    String sessionId = sessionId(player);
    dispatcher.unregister(sessionId);
    inv.clear();
    for (EntryDef entry : def.getEntries()) {
      ItemTemplate template = entry.getDisplay();
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("key", entry.getKey());
      placeholders.put("value", entry.getValue());
      placeholders.put("sub", def.getId());
      placeholders.put("main", session.getMainId());
      ItemStack item = renderer.render(template, player, placeholders, true);
      inv.setItem(entry.getSlot(), item);
      registerClick(sessionId, entry);
    }
  }

  private void registerClick(String sessionId, EntryDef entry) {
    ClickAction action = context -> {
      Player player = context.player();
      BindSession session = bindSessions.getSession(player);
      if (session == null) {
        messageService.send(player, "messages.session-missing", Map.of());
        return;
      }
      String valueToStore = resolveValueAtBind
          ? placeholderUtil.parse(player, entry.getValue())
          : entry.getValue();
      GuiBindEvent event = new GuiBindEvent(player, session.getMainId(), session.getSlot(),
          session.getSubId(), entry.getKey(), valueToStore);
      Bukkit.getPluginManager().callEvent(event);
      if (event.isCancelled()) {
        messageService.send(player, "messages.bind-cancelled", Map.of());
        return;
      }
      Binding binding = Binding.now(player.getUniqueId(), event.getMainId(), event.getSlot(),
          event.getSubId(), event.getEntryKey(), event.getEntryValue());
      bindingService.bind(binding).whenComplete((b, ex) -> {
        if (ex != null) {
          logger.error("写入绑定失败", ex);
          messageService.send(player, "messages.bind-failed", Map.of());
          return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
          messageService.send(player, "messages.bind-success",
              Map.of("key", binding.getEntryKey(), "value", binding.getEntryValue()));
          currentSub.remove(player.getUniqueId());
          bindSessions.destroySession(player);
          sessions.closeSession(player);
          mainGuiController.openMain(player, binding.getMainId());
          mainGuiController.refreshSlot(player, binding.getMainId(), binding.getSlot());
        });
      });
    };
    dispatcher.registerForSlot(sessionId, entry.getSlot(), action);
  }

  private SubGuiDef currentDefinition(Player player) {
    String id = currentSub.get(player.getUniqueId());
    return id == null ? null : configService.getSub(id);
  }

  private String sessionId(Player player) {
    return "sub:" + player.getUniqueId();
  }

  public void handleClose(Player player) {
    currentSub.remove(player.getUniqueId());
    bindSessions.destroySession(player);
  }

  public void handleConfigReload() {
    Map<UUID, String> snapshot = new HashMap<>(currentSub);
    for (Entry<UUID, String> entry : snapshot.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player == null) {
        continue;
      }
      String sessionKey = sessions.getCurrentSessionId(player);
      if (sessionKey == null || !sessionKey.equals(sessionId(player))) {
        continue;
      }
      BindSession bindSession = bindSessions.getSession(player);
      SubGuiDef def = configService.getSub(entry.getValue());
      if (def == null || bindSession == null) {
        messageService.send(player, "messages.sub-not-found", Map.of("id", entry.getValue()));
        sessions.closeSession(player);
        handleClose(player);
        player.closeInventory();
        if (bindSession != null) {
          mainGuiController.openMain(player, bindSession.getMainId());
        }
        continue;
      }
      openSub(player, def);
    }
  }
}
