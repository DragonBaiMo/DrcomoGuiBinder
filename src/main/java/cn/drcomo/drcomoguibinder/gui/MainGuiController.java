package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.gui.PaginatedGui;
import cn.drcomo.corelib.gui.interfaces.ClickAction;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.bind.Binding;
import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.drcomoguibinder.config.GuiConfigService;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.config.model.MainGuiDef;
import cn.drcomo.drcomoguibinder.config.model.MainSlotDef;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
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

/**
 * 管理 Main GUI 的渲染与交互。
 */
public final class MainGuiController extends PaginatedGui {

  private final GUISessionManager sessions;
  private final GuiActionDispatcher dispatcher;
  private final GuiConfigService configService;
  private final BindingService bindingService;
  private final ItemTemplateRenderer renderer;
  private final cn.drcomo.corelib.gui.session.PlayerSessionManager<BindSession> bindSessions;
  private SubGuiController subGuiController;
  private final DebugUtil logger;
  private final cn.drcomo.corelib.message.MessageService messageService;
  private final boolean parseValueOnRender;
  private final long clickCooldownMs;
  private final Map<UUID, String> currentMain = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

  public MainGuiController(GUISessionManager sessions, GuiActionDispatcher dispatcher,
      GuiConfigService configService, BindingService bindingService,
      ItemTemplateRenderer renderer,
      cn.drcomo.corelib.gui.session.PlayerSessionManager<BindSession> bindSessions,
      DebugUtil logger,
      cn.drcomo.corelib.message.MessageService messageService, boolean parseValueOnRender,
      long clickCooldownMs) {
    super(sessions, dispatcher, 54, 45, 53);
    this.sessions = sessions;
    this.dispatcher = dispatcher;
    this.configService = configService;
    this.bindingService = bindingService;
    this.renderer = renderer;
    this.bindSessions = bindSessions;
    this.logger = logger;
    this.messageService = messageService;
    this.parseValueOnRender = parseValueOnRender;
    this.clickCooldownMs = clickCooldownMs;
  }

  public void setSubGuiController(SubGuiController subGuiController) {
    this.subGuiController = subGuiController;
  }

  public void openMain(Player player, String mainId) {
    MainGuiDef def = configService.getMain(mainId);
    if (def == null) {
      messageService.send(player, "messages.main-not-found", Map.of("id", mainId));
      return;
    }
    currentMain.put(player.getUniqueId(), mainId);
    lastClick.put(player.getUniqueId(), 0L);
    super.open(player, sessionId(player));
  }

  public void refreshSlot(Player player, String mainId, int slot) {
    if (!mainId.equals(currentMain.get(player.getUniqueId()))) {
      return;
    }
    MainGuiDef def = configService.getMain(mainId);
    if (def == null) {
      return;
    }
    MainSlotDef slotDef = def.findSlot(slot);
    if (slotDef == null) {
      return;
    }
    Inventory inv = player.getOpenInventory().getTopInventory();
    if (inv == null) {
      return;
    }
    ItemStack item = resolveSlotDisplay(player, def, slotDef);
    inv.setItem(slotDef.getSlot(), item);
  }

  @Override
  protected int getTotalItemCount(Player player) {
    MainGuiDef def = currentDefinition(player);
    return def == null ? 0 : def.getSlots().size();
  }

  @Override
  protected Inventory createInventory(Player player) {
    MainGuiDef def = currentDefinition(player);
    int size = def == null ? 54 : def.getSize();
    String title = def == null ? "&c未配置的界面" : def.getTitle();
    return Bukkit.createInventory(player, size, cn.drcomo.corelib.color.ColorUtil.translateColors(title));
  }

  @Override
  protected void renderPage(Player player, Inventory inv, int page, int totalPages) {
    MainGuiDef def = currentDefinition(player);
    if (def == null) {
      inv.clear();
      return;
    }
    String sessionId = sessionId(player);
    dispatcher.unregister(sessionId);
    inv.clear();
    for (MainSlotDef slotDef : def.getSlots()) {
      ItemStack item = resolveSlotDisplay(player, def, slotDef);
      inv.setItem(slotDef.getSlot(), item);
      registerClick(sessionId, slotDef);
    }
  }

  private void registerClick(String sessionId, MainSlotDef slotDef) {
    ClickAction action = context -> {
      Player player = context.player();
      long now = System.currentTimeMillis();
      long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
      if (clickCooldownMs > 0 && now - last < clickCooldownMs) {
        messageService.send(player, "messages.click-too-fast", Map.of());
        return;
      }
      lastClick.put(player.getUniqueId(), now);
      String mainId = currentMain.get(player.getUniqueId());
      if (mainId == null) {
        return;
      }
      MainGuiDef def = configService.getMain(mainId);
      if (def == null) {
        messageService.send(player, "messages.main-not-found", Map.of("id", mainId));
        return;
      }
      if (slotDef.getSubId() == null) {
        messageService.send(player, "messages.slot-no-sub", Map.of("slot", String.valueOf(slotDef.getSlot())));
        return;
      }
      SubGuiDef sub = configService.getSub(slotDef.getSubId());
      if (sub == null) {
        messageService.send(player, "messages.sub-not-found",
            Map.of("id", slotDef.getSubId()));
        return;
      }
      bindSessions.createSession(player, new BindSession(mainId, slotDef.getSlot(), sub.getId()));
      if (subGuiController != null) {
        subGuiController.openSub(player, sub);
      }
    };
    dispatcher.registerForSlot(sessionId, slotDef.getSlot(), action);
  }

  private ItemStack resolveSlotDisplay(Player player, MainGuiDef mainDef, MainSlotDef slotDef) {
    Binding binding = bindingService.get(player.getUniqueId(), mainDef.getId(), slotDef.getSlot());
    if (binding == null) {
      return renderer.render(slotDef.getDisplayEmpty(), player, Map.of(
          "main", mainDef.getId(),
          "slot", String.valueOf(slotDef.getSlot())
      ), true);
    }
    SubGuiDef sub = configService.getSub(binding.getSubId());
    if (sub == null) {
      return renderer.render(slotDef.getDisplayEmpty(), player, Map.of(), true);
    }
    EntryDef entry = sub.findEntryByKey(binding.getEntryKey());
    if (entry == null) {
      return renderer.render(slotDef.getDisplayEmpty(), player, Map.of(), true);
    }
    ItemTemplate template = entry.resolveDisplay(mainDef.getId());
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("main", mainDef.getId());
    placeholders.put("slot", String.valueOf(slotDef.getSlot()));
    placeholders.put("sub", sub.getId());
    placeholders.put("key", binding.getEntryKey());
    placeholders.put("value", binding.getEntryValue());
    return renderer.render(template, player, placeholders, parseValueOnRender);
  }

  private MainGuiDef currentDefinition(Player player) {
    String id = currentMain.get(player.getUniqueId());
    return id == null ? null : configService.getMain(id);
  }

  private String sessionId(Player player) {
    return "main:" + player.getUniqueId();
  }

  public void handleClose(Player player) {
    currentMain.remove(player.getUniqueId());
    lastClick.remove(player.getUniqueId());
    bindSessions.destroySession(player);
  }

  public void handleConfigReload() {
    Map<UUID, String> snapshot = new HashMap<>(currentMain);
    for (Entry<UUID, String> entry : snapshot.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player == null) {
        continue;
      }
      String sessionKey = sessions.getCurrentSessionId(player);
      if (sessionKey == null || !sessionKey.equals(sessionId(player))) {
        continue;
      }
      String mainId = entry.getValue();
      MainGuiDef def = configService.getMain(mainId);
      if (def == null) {
        messageService.send(player, "messages.main-not-found", Map.of("id", mainId));
        sessions.closeSession(player);
        handleClose(player);
        player.closeInventory();
        continue;
      }
      openMain(player, mainId);
    }
  }
}
