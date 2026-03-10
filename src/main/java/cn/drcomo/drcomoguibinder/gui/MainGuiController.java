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
import cn.drcomo.drcomoguibinder.config.model.ClearClickType;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.GuiSlotType;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.config.model.MainGuiDef;
import cn.drcomo.drcomoguibinder.config.model.MainSlotDef;
import cn.drcomo.drcomoguibinder.config.model.MainSlotEntryDef;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
import cn.drcomo.drcomoguibinder.gui.session.BindSession;
import cn.drcomo.drcomoguibinder.template.ItemTemplateRenderer;
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
  private final PlaceholderConditionEvaluator conditionEvaluator;
  private final PlaceholderAPIUtil placeholderUtil;
  private final boolean parseValueOnRender;
  private final long clickCooldownMs;
  private ClearClickType clearClickType;
  private final Plugin plugin;
  private final Map<UUID, String> currentMain = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

  public MainGuiController(GUISessionManager sessions, GuiActionDispatcher dispatcher,
      GuiConfigService configService, BindingService bindingService,
      ItemTemplateRenderer renderer,
      cn.drcomo.corelib.gui.session.PlayerSessionManager<BindSession> bindSessions,
      DebugUtil logger,
      cn.drcomo.corelib.message.MessageService messageService,
      PlaceholderConditionEvaluator conditionEvaluator,
      PlaceholderAPIUtil placeholderUtil,
      boolean parseValueOnRender,
      long clickCooldownMs,
      ClearClickType clearClickType,
      Plugin plugin) {
    super(sessions, dispatcher, 54, 45, 53);
    this.sessions = sessions;
    this.dispatcher = dispatcher;
    this.configService = configService;
    this.bindingService = bindingService;
    this.renderer = renderer;
    this.bindSessions = bindSessions;
    this.logger = logger;
    this.messageService = messageService;
    this.conditionEvaluator = conditionEvaluator;
    this.placeholderUtil = placeholderUtil;
    this.parseValueOnRender = parseValueOnRender;
    this.clickCooldownMs = clickCooldownMs;
    this.clearClickType = clearClickType;
    this.plugin = plugin;
  }

  public void setSubGuiController(SubGuiController subGuiController) {
    this.subGuiController = subGuiController;
  }

  /**
   * 更新清除绑定的点击类型配置。
   *
   * @param clearClickType 新的清除点击类型
   */
  public void setClearClickType(ClearClickType clearClickType) {
    this.clearClickType = clearClickType;
    logger.info("清除点击类型已更新为: " + clearClickType);
  }

  /**
   * 获取当前的清除点击类型配置。
   *
   * @return 清除点击类型
   */
  public ClearClickType getClearClickType() {
    return clearClickType;
  }

  /**
   * 从 GuiListener 调用的清除绑定处理方法。
   * 绕过 dispatcher 的 dangerous 检查，直接处理清除请求。
   *
   * @param player 玩家
   * @param slot 点击的槽位
   */
  public void handleClearClickFromListener(Player player, int slot) {
    String mainId = currentMain.get(player.getUniqueId());
    if (mainId == null) {
      logger.warn("玩家 " + player.getName() + " 尝试清除绑定，但 mainId 为空");
      return;
    }

    MainGuiDef def = configService.getMain(mainId);
    if (def == null) {
      logger.error("玩家 " + player.getName() + " 当前主界面 " + mainId + " 不存在");
      messageService.send(player, "messages.main-not-found", Map.of("id", mainId));
      return;
    }

    MainSlotDef slotDef = def.findSlot(slot);
    if (slotDef == null) {
      logger.debug("玩家 " + player.getName() + " 点击的槽位 " + slot + " 未配置");
      return;
    }

    // 检查槽位是否配置了 Sub（只有配置了 Sub 的槽位才能清除绑定）
    if (slotDef.getSubId() == null || slotDef.getSubId().isEmpty()) {
      return;
    }

    // 检查选择条件
    if (!checkChooseConditions(player, slotDef)) {
      messageService.send(player, "messages.slot-no-permission", Map.of());
      return;
    }

    handleClearClick(player, mainId, slot);
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
    // 解析占位符（支持 PAPI 和 ItemsAdder 格式）
    String parsedTitle = placeholderUtil.parse(player, title);
    return Bukkit.createInventory(player, size, cn.drcomo.corelib.color.ColorUtil.translateColors(parsedTitle));
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
      // 检查显示条件
      if (!checkDisplayConditions(player, slotDef)) {
        continue; // 不满足显示条件，跳过该槽位
      }

      ItemStack item = resolveSlotDisplay(player, def, slotDef);
      inv.setItem(slotDef.getSlot(), item);

      // 装饰槽只放置物品，不注册点击事件
      if (slotDef.getType() != GuiSlotType.DECORATION) {
        registerClick(sessionId, slotDef);
      }
    }
  }

  private void registerClick(String sessionId, MainSlotDef slotDef) {
    ClickAction action = context -> {
      Player player = context.player();

      // 点击冷却检查
      long now = System.currentTimeMillis();
      long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
      if (clickCooldownMs > 0 && now - last < clickCooldownMs) {
        messageService.send(player, "messages.click-too-fast", Map.of());
        return;
      }
      lastClick.put(player.getUniqueId(), now);

      String mainId = currentMain.get(player.getUniqueId());
      if (mainId == null) {
        logger.warn("玩家 " + player.getName() + " 点击了主界面槽位，但 mainId 为空");
        return;
      }

      MainGuiDef def = configService.getMain(mainId);
      if (def == null) {
        logger.error("玩家 " + player.getName() + " 当前主界面 " + mainId + " 不存在");
        messageService.send(player, "messages.main-not-found", Map.of("id", mainId));
        return;
      }

      // 调试日志：输出槽位类型
      logger.debug("玩家 " + player.getName() + " 点击槽位 " + slotDef.getSlot()
          + ", 类型=" + slotDef.getType() + ", actions数量=" + slotDef.getActions().size());

      // 处理 ACTION 类型槽位
      if (slotDef.getType() == GuiSlotType.ACTION) {
        handleActionClick(player, slotDef);
        return;
      }

      // 检查槽位是否配置了 Sub
      if (slotDef.getSubId() == null || slotDef.getSubId().isEmpty()) {
        messageService.send(player, "messages.slot-no-sub", Map.of("slot", String.valueOf(slotDef.getSlot())));
        return;
      }

      // 检查选择条件
      if (!checkChooseConditions(player, slotDef)) {
        messageService.send(player, "messages.slot-no-permission", Map.of());
        return;
      }

      // 检查条目级别的选择条件（新增：多条目动态选择支持）
      if (slotDef.hasEntries()) {
        MainSlotEntryDef matchedEntry = findMatchedEntry(player, slotDef);
        if (matchedEntry != null && !checkEntryChooseConditions(player, matchedEntry)) {
          messageService.send(player, "messages.entry-no-permission", Map.of());
          return;
        }
      }

      // 检查是否为清除绑定点击
      logger.debug("清除点击检查: clearClickType=" + clearClickType + ", clickType=" + context.clickType()
          + ", matches=" + (clearClickType != ClearClickType.DISABLED ? clearClickType.matches(context) : "N/A"));
      if (clearClickType != ClearClickType.DISABLED && clearClickType.matches(context)) {
        handleClearClick(player, mainId, slotDef.getSlot());
        return;
      }

      SubGuiDef sub = configService.getSub(slotDef.getSubId());
      if (sub == null) {
        logger.error("玩家 " + player.getName() + " 点击的槽位指向的 Sub " + slotDef.getSubId() + " 不存在");
        messageService.send(player, "messages.sub-not-found",
            Map.of("id", slotDef.getSubId()));
        return;
      }

      logger.info("玩家 " + player.getName() + " 打开了 Sub 界面: " + sub.getId());
      bindSessions.createSession(player, new BindSession(mainId, slotDef.getSlot(), sub.getId()));
      if (subGuiController != null) {
        subGuiController.openSub(player, sub);
      }
    };
    dispatcher.registerForSlot(sessionId, slotDef.getSlot(), action);
  }

  /**
   * 处理 ACTION 类型槽位的点击事件。
   * 执行配置的命令列表，支持 [op]、[console]、[player] 前缀。
   */
  private void handleActionClick(Player player, MainSlotDef slotDef) {
    // 检查选择条件
    if (!checkChooseConditions(player, slotDef)) {
      messageService.send(player, "messages.slot-no-permission", Map.of());
      return;
    }

    List<String> actions = slotDef.getActions();
    if (actions.isEmpty()) {
      logger.warn("玩家 " + player.getName() + " 点击了 ACTION 槽位，但未配置任何动作");
      return;
    }

    for (String action : actions) {
      executeAction(player, action);
    }
  }

  /**
   * 执行单条动作命令。
   * 支持前缀: [op] 以 OP 权限执行, [console] 以控制台执行, [player] 以玩家身份执行（默认）。
   */
  private void executeAction(Player player, String action) {
    if (action == null || action.isEmpty()) {
      return;
    }

    // 解析占位符
    String command = placeholderUtil.parse(player, action);

    // 解析执行类型前缀
    String lowerAction = command.toLowerCase();
    if (lowerAction.startsWith("[op]")) {
      String cmd = command.substring(4).trim();
      executeAsOp(player, cmd);
    } else if (lowerAction.startsWith("[console]")) {
      String cmd = command.substring(9).trim();
      executeAsConsole(cmd);
    } else if (lowerAction.startsWith("[player]")) {
      String cmd = command.substring(8).trim();
      executeAsPlayer(player, cmd);
    } else {
      // 默认以玩家身份执行
      executeAsPlayer(player, command);
    }
  }

  /**
   * 以玩家身份执行命令。
   */
  private void executeAsPlayer(Player player, String command) {
    logger.debug("玩家 " + player.getName() + " 执行命令: " + command);
    Bukkit.dispatchCommand(player, command);
  }

  /**
   * 以 OP 权限执行命令（临时赋予 OP）。
   */
  private void executeAsOp(Player player, String command) {
    logger.debug("玩家 " + player.getName() + " 以 OP 权限执行命令: " + command);
    boolean wasOp = player.isOp();
    try {
      if (!wasOp) {
        player.setOp(true);
      }
      Bukkit.dispatchCommand(player, command);
    } finally {
      if (!wasOp) {
        player.setOp(false);
      }
    }
  }

  /**
   * 以控制台身份执行命令。
   */
  private void executeAsConsole(String command) {
    logger.debug("控制台执行命令: " + command);
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
  }

  private ItemStack resolveSlotDisplay(Player player, MainGuiDef mainDef, MainSlotDef slotDef) {
    Binding binding = bindingService.get(player.getUniqueId(), mainDef.getId(), slotDef.getSlot());

    // 构建基础占位符
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("main", mainDef.getId());
    placeholders.put("slot", String.valueOf(slotDef.getSlot()));
    if (slotDef.getId() != null && !slotDef.getId().isEmpty()) {
      placeholders.put("slotId", slotDef.getId());
    }

    if (binding == null) {
      // 未绑定：使用 entries 机制选择显示模板
      return resolveUnboundDisplay(player, slotDef, placeholders);
    } else {
      // 已绑定：使用 entries 机制 + 绑定数据渲染
      return resolveBoundDisplay(player, mainDef, slotDef, binding, placeholders);
    }
  }

  /**
   * 解析未绑定状态的显示。
   * 优先从 entries 中选择满足条件的最高优先级条目。
   */
  private ItemStack resolveUnboundDisplay(Player player, MainSlotDef slotDef,
      Map<String, String> placeholders) {
    // 如果有 entries，按优先级查找满足条件的条目
    if (slotDef.hasEntries()) {
      for (MainSlotEntryDef entry : slotDef.getEntries()) {
        if (checkEntryDisplayConditions(player, entry)) {
          ItemTemplate template = entry.getDisplayEmpty();
          if (template != null) {
            return renderer.render(template, player, placeholders, true);
          }
        }
      }
    }

    // Fallback：使用槽位级别的 displayEmpty
    return renderer.render(slotDef.getDisplayEmpty(), player, placeholders, true);
  }

  /**
   * 解析已绑定状态的显示。
   * 优先使用 entries 中满足条件的条目的 displayBound，
   * 否则 fallback 到 Sub 条目的 resolveDisplay()。
   */
  private ItemStack resolveBoundDisplay(Player player, MainGuiDef mainDef, MainSlotDef slotDef,
      Binding binding, Map<String, String> placeholders) {
    // 补充绑定相关占位符
    placeholders.put("key", binding.getEntryKey());
    placeholders.put("value", binding.getEntryValue());
    placeholders.put("sub", binding.getSubId());

    // 获取绑定的 Sub 条目（用于获取额外属性）
    SubGuiDef sub = configService.getSub(binding.getSubId());
    EntryDef subEntry = sub != null ? sub.findEntryByKey(binding.getEntryKey()) : null;

    // 补充 Sub 条目的 material 占位符
    if (subEntry != null && subEntry.getDisplay() != null) {
      String material = subEntry.getDisplay().getMaterial();
      if (material != null) {
        placeholders.put("material", material);
      }
    }

    // 如果有 entries，按优先级查找满足条件的条目
    if (slotDef.hasEntries()) {
      for (MainSlotEntryDef entry : slotDef.getEntries()) {
        if (checkEntryDisplayConditions(player, entry)) {
          // 优先使用 displayBound
          if (entry.hasDisplayBound()) {
            return renderer.render(entry.getDisplayBound(), player, placeholders, parseValueOnRender);
          }
          // 无 displayBound 时跳出循环，使用 fallback
          break;
        }
      }
    }

    // Fallback：使用 Sub 条目的 resolveDisplay（现有逻辑）
    if (subEntry != null) {
      ItemTemplate template = subEntry.resolveDisplay(mainDef.getId());
      return renderer.render(template, player, placeholders, parseValueOnRender);
    }

    // 最终 Fallback：使用 displayEmpty
    return renderer.render(slotDef.getDisplayEmpty(), player, placeholders, true);
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

  /**
   * 处理主界面清除绑定点击事件。
   *
   * @param player 玩家
   * @param mainId 主界面 ID
   * @param slot 槽位
   */
  private void handleClearClick(Player player, String mainId, int slot) {
    UUID playerUUID = player.getUniqueId();

    // 检查当前是否有绑定
    Binding existingBinding = bindingService.get(playerUUID, mainId, slot);
    if (existingBinding == null) {
      logger.debug("玩家 " + player.getName() + " 尝试清除绑定，但当前无绑定: mainId=" + mainId + ", slot=" + slot);
      messageService.send(player, "messages.main-clear-empty", Map.of());
      return;
    }

    logger.info("玩家 " + player.getName() + " 在主界面清除绑定: mainId=" + mainId + ", slot=" + slot);

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
        messageService.send(player, "messages.main-clear-success",
            Map.of("main", mainId, "slot", String.valueOf(slot)));

        // 刷新当前槽位显示
        refreshSlot(player, mainId, slot);
      });
    });
  }

  /**
   * 检查槽位的显示条件是否满足。
   *
   * @param player 玩家
   * @param slotDef 槽位定义
   * @return 如果满足显示条件返回 true，否则返回 false
   */
  private boolean checkDisplayConditions(Player player, MainSlotDef slotDef) {
    if (!slotDef.getConditions().hasDisplayConditions()) {
      return true; // 无显示条件限制，直接显示
    }

    try {
      return conditionEvaluator.checkAllLines(player, slotDef.getConditions().getDisplay());
    } catch (Exception e) {
      logger.error("检查主界面槽位显示条件失败: slot=" + slotDef.getSlot(), e);
      return false; // 发生异常时默认不显示
    }
  }

  /**
   * 检查槽位的选择条件是否满足。
   *
   * @param player 玩家
   * @param slotDef 槽位定义
   * @return 如果满足选择条件返回 true，否则返回 false
   */
  private boolean checkChooseConditions(Player player, MainSlotDef slotDef) {
    if (!slotDef.getConditions().hasChooseConditions()) {
      return true; // 无选择条件限制，直接允许选择
    }

    try {
      return conditionEvaluator.checkAllLines(player, slotDef.getConditions().getChoose());
    } catch (Exception e) {
      logger.error("检查主界面槽位选择条件失败: slot=" + slotDef.getSlot(), e);
      return false; // 发生异常时默认不允许选择
    }
  }

  /**
   * 检查 MainSlotEntryDef 的显示条件。
   *
   * @param player 玩家
   * @param entry 条目定义
   * @return 如果满足显示条件返回 true，否则返回 false
   */
  private boolean checkEntryDisplayConditions(Player player, MainSlotEntryDef entry) {
    if (!entry.getConditions().hasDisplayConditions()) {
      return true; // 无显示条件限制，直接显示
    }

    try {
      return conditionEvaluator.checkAllLines(player, entry.getConditions().getDisplay());
    } catch (Exception e) {
      logger.error("检查 Main 槽位条目显示条件失败", e);
      return false; // 发生异常时默认不显示
    }
  }

  /**
   * 检查 MainSlotEntryDef 的选择条件。
   *
   * @param player 玩家
   * @param entry 条目定义
   * @return 如果满足选择条件返回 true，否则返回 false
   */
  private boolean checkEntryChooseConditions(Player player, MainSlotEntryDef entry) {
    if (!entry.getConditions().hasChooseConditions()) {
      return true; // 无选择条件限制，直接允许选择
    }

    try {
      return conditionEvaluator.checkAllLines(player, entry.getConditions().getChoose());
    } catch (Exception e) {
      logger.error("检查 Main 槽位条目选择条件失败", e);
      return false; // 发生异常时默认不允许选择
    }
  }

  /**
   * 查找当前满足显示条件的最高优先级条目。
   *
   * @param player 玩家
   * @param slotDef 槽位定义
   * @return 满足条件的条目，如果没有则返回 null
   */
  private MainSlotEntryDef findMatchedEntry(Player player, MainSlotDef slotDef) {
    for (MainSlotEntryDef entry : slotDef.getEntries()) {
      if (checkEntryDisplayConditions(player, entry)) {
        return entry;
      }
    }
    return null;
  }
}
