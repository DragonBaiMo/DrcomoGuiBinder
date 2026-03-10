package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.gui.PaginatedGui;
import cn.drcomo.corelib.gui.interfaces.ClickAction;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.hook.placeholder.parse.PlaceholderConditionEvaluator;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.bind.Binding;
import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.drcomoguibinder.config.GuiConfigService;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.GuiSlotType;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
import cn.drcomo.drcomoguibinder.event.GuiBindEvent;
import cn.drcomo.drcomoguibinder.gui.session.BindSession;
import cn.drcomo.drcomoguibinder.template.ItemTemplateRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  private final PlaceholderConditionEvaluator conditionEvaluator;
  private final boolean resolveValueAtBind;
  private MainGuiController mainGuiController;
  private final Plugin plugin;
  private final Map<UUID, String> currentSub = new ConcurrentHashMap<>();

  public SubGuiController(GUISessionManager sessions, GuiActionDispatcher dispatcher,
      GuiConfigService configService, BindingService bindingService,
      ItemTemplateRenderer renderer,
      cn.drcomo.corelib.gui.session.PlayerSessionManager<BindSession> bindSessions,
      DebugUtil logger, cn.drcomo.corelib.message.MessageService messageService,
      PlaceholderAPIUtil placeholderUtil, PlaceholderConditionEvaluator conditionEvaluator,
      boolean resolveValueAtBind, MainGuiController mainGuiController, Plugin plugin) {
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
    this.conditionEvaluator = conditionEvaluator;
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
    // 解析占位符（支持 PAPI 和 ItemsAdder 格式）
    String parsedTitle = placeholderUtil.parse(player, title);
    return Bukkit.createInventory(player, size, cn.drcomo.corelib.color.ColorUtil.translateColors(parsedTitle));
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

    // 分离特殊槽位和普通条目
    List<EntryDef> specialEntries = new ArrayList<>();  // 装饰/返回/清除
    List<EntryDef> normalEntries = new ArrayList<>();   // 普通职业条目

    for (EntryDef entry : def.getEntries()) {
      if (entry.getType() == GuiSlotType.DECORATION
          || entry.getType() == GuiSlotType.RETURN
          || entry.getType() == GuiSlotType.CLEAR) {
        specialEntries.add(entry);
      } else if (checkDisplayConditions(player, entry)) {
        normalEntries.add(entry);
      }
    }

    // 渲染特殊槽位（保持原位）
    for (EntryDef entry : specialEntries) {
      ItemTemplate template = entry.getDisplay();
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("key", entry.getKey());
      placeholders.put("value", entry.getValue());
      placeholders.put("sub", def.getId());
      placeholders.put("main", session.getMainId());
      ItemStack item = renderer.render(template, player, placeholders, true);
      inv.setItem(entry.getSlot(), item);

      // 装饰槽不注册点击事件
      if (entry.getType() != GuiSlotType.DECORATION) {
        registerClick(sessionId, entry, session, entry.getSlot());
      }
    }

    // 按 slot 分组，每个 slot 只保留优先级最高的条目
    Map<Integer, EntryDef> slotToEntry = new HashMap<>();
    for (EntryDef entry : normalEntries) {
      int slot = entry.getSlot();
      EntryDef existing = slotToEntry.get(slot);
      if (existing == null || entry.getPriority() > existing.getPriority()) {
        slotToEntry.put(slot, entry);
      }
    }

    // 收集去重后的条目并按优先级降序排序
    List<EntryDef> sortedEntries = new ArrayList<>(slotToEntry.values());
    sortedEntries.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

    // 收集配置中所有普通条目的 slot 列表（按配置顺序，去重）
    List<Integer> availableSlots = new ArrayList<>();
    for (EntryDef entry : def.getEntries()) {
      if (entry.getType() == GuiSlotType.DEFAULT && !availableSlots.contains(entry.getSlot())) {
        availableSlots.add(entry.getSlot());
      }
    }

    // 按优先级顺序依次填充到可用槽位
    for (int i = 0; i < sortedEntries.size() && i < availableSlots.size(); i++) {
      EntryDef entry = sortedEntries.get(i);
      int targetSlot = availableSlots.get(i);

      ItemTemplate template = entry.getDisplay();
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("key", entry.getKey());
      placeholders.put("value", entry.getValue());
      placeholders.put("sub", def.getId());
      placeholders.put("main", session.getMainId());
      ItemStack item = renderer.render(template, player, placeholders, true);
      inv.setItem(targetSlot, item);

      registerClick(sessionId, entry, session, targetSlot);
    }
  }

  private void registerClick(String sessionId, EntryDef entry, BindSession session, int targetSlot) {
    ClickAction action = context -> {
      Player player = context.player();
      BindSession currentSession = bindSessions.getSession(player);
      if (currentSession == null) {
        logger.warn("玩家 " + player.getName() + " 点击了 Sub 界面，但会话丢失");
        messageService.send(player, "messages.session-missing", Map.of());
        return;
      }
      
      // 处理返回槽
      if (entry.getType() == GuiSlotType.RETURN) {
        handleReturnClick(player, currentSession);
        return;
      }
      
      // 处理清除绑定槽
      if (entry.getType() == GuiSlotType.CLEAR) {
        handleClearClick(player, currentSession);
        return;
      }
      
      // 处理默认绑定槽
      if (entry.getType() == GuiSlotType.DEFAULT) {
        handleBindClick(player, currentSession, entry);
        return;
      }
    };
    dispatcher.registerForSlot(sessionId, targetSlot, action);
  }
  
  /**
   * 处理返回槽点击事件。
   */
  private void handleReturnClick(Player player, BindSession session) {
    if (session == null || session.getMainId() == null || session.getMainId().isEmpty()) {
      logger.error("玩家 " + player.getName() + " 点击返回槽，但会话数据不完整");
      messageService.send(player, "messages.return-no-session", Map.of());
      player.closeInventory();
      return;
    }
    
    logger.info("玩家 " + player.getName() + " 点击返回槽，返回主界面: " + session.getMainId());
    String mainId = session.getMainId();
    currentSub.remove(player.getUniqueId());
    bindSessions.destroySession(player);
    sessions.closeSession(player);
    
    if (mainGuiController != null) {
      mainGuiController.openMain(player, mainId);
      messageService.send(player, "messages.return-success", Map.of("mainId", mainId));
    } else {
      logger.error("主界面控制器未初始化，无法返回");
      player.closeInventory();
    }
  }
  
  /**
   * 处理清除绑定槽点击事件。
   */
  private void handleClearClick(Player player, BindSession session) {
    if (session == null || session.getMainId() == null || session.getMainId().isEmpty()) {
      logger.error("玩家 " + player.getName() + " 点击清除绑定槽，但会话数据不完整");
      messageService.send(player, "messages.clear-no-session", Map.of());
      player.closeInventory();
      return;
    }
    
    UUID playerUUID = player.getUniqueId();
    String mainId = session.getMainId();
    int slot = session.getSlot();
    
    logger.info("玩家 " + player.getName() + " 点击清除绑定槽: mainId=" + mainId + ", slot=" + slot);
    
    // 检查当前是否有绑定
    Binding existingBinding = bindingService.get(playerUUID, mainId, slot);
    if (existingBinding == null) {
      logger.info("玩家 " + player.getName() + " 尝试清除绑定，但当前无绑定");
      messageService.send(player, "messages.clear-no-binding", Map.of());
      return;
    }
    
    // 执行解绑操作
    bindingService.clear(playerUUID, mainId, slot).whenComplete((success, ex) -> {
      if (ex != null) {
        logger.error("清除绑定失败: mainId=" + mainId + ", slot=" + slot, ex);
        Bukkit.getScheduler().runTask(plugin, () -> {
          messageService.send(player, "messages.clear-failed", Map.of());
        });
        return;
      }
      
      Bukkit.getScheduler().runTask(plugin, () -> {
        logger.info("玩家 " + player.getName() + " 成功清除绑定: mainId=" + mainId + ", slot=" + slot);
        messageService.send(player, "messages.clear-success", Map.of());
        
        // 清理会话并关闭子界面
        currentSub.remove(playerUUID);
        bindSessions.destroySession(player);
        sessions.closeSession(player);
        
        // 打开主界面并刷新槽位
        if (mainGuiController != null) {
          mainGuiController.openMain(player, mainId);
          mainGuiController.refreshSlot(player, mainId, slot);
        } else {
          logger.error("主界面控制器未初始化，无法返回");
          player.closeInventory();
        }
      });
    });
  }
  
  /**
   * 处理绑定槽点击事件。
   */
  private void handleBindClick(Player player, BindSession session, EntryDef entry) {
    // 检查选择条件
    if (!checkChooseConditions(player, entry)) {
      messageService.send(player, "messages.choose-condition-failed", Map.of());
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
        event.getSubId(), entry.getKey(), event.getEntryValue());
    bindingService.bind(binding).whenComplete((b, ex) -> {
      if (ex != null) {
        logger.error("写入绑定失败", ex);
        messageService.send(player, "messages.bind-failed", Map.of());
        return;
      }
      Bukkit.getScheduler().runTask(plugin, () -> {
        logger.info("玩家 " + player.getName() + " 成功绑定: key=" + binding.getEntryKey() + ", value=" + binding.getEntryValue());
        messageService.send(player, "messages.bind-success",
            Map.of("key", binding.getEntryKey(), "value", binding.getEntryValue()));
        currentSub.remove(player.getUniqueId());
        bindSessions.destroySession(player);
        sessions.closeSession(player);
        mainGuiController.openMain(player, binding.getMainId());
        mainGuiController.refreshSlot(player, binding.getMainId(), binding.getSlot());
      });
    });
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

  /**
   * 检查条目的显示条件是否满足。
   *
   * @param player 玩家
   * @param entry 条目定义
   * @return 如果满足显示条件返回 true，否则返回 false
   */
  private boolean checkDisplayConditions(Player player, EntryDef entry) {
    if (!entry.getConditions().hasDisplayConditions()) {
      return true; // 无显示条件限制，直接显示
    }
    
    try {
      return conditionEvaluator.checkAllLines(player, entry.getConditions().getDisplay());
    } catch (Exception e) {
      logger.error("检查显示条件失败: " + entry.getKey(), e);
      return false; // 发生异常时默认不显示
    }
  }

  /**
   * 检查条目的选择条件是否满足。
   *
   * @param player 玩家
   * @param entry 条目定义
   * @return 如果满足选择条件返回 true，否则返回 false
   */
  private boolean checkChooseConditions(Player player, EntryDef entry) {
    if (!entry.getConditions().hasChooseConditions()) {
      return true; // 无选择条件限制，直接允许选择
    }
    
    try {
      return conditionEvaluator.checkAllLines(player, entry.getConditions().getChoose());
    } catch (Exception e) {
      logger.error("检查选择条件失败: " + entry.getKey(), e);
      return false; // 发生异常时默认不允许选择
    }
  }
}
