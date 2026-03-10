package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.gui.ClickContext;
import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.config.model.ClearClickType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 统一监听 GUI 相关事件并交由 GuiActionDispatcher 处理。
 */
public final class GuiListener implements Listener {

  private final GuiActionDispatcher dispatcher;
  private final GUISessionManager sessionManager;
  private final DebugUtil logger;
  private final MainGuiController mainGuiController;
  private final SubGuiController subGuiController;

  public GuiListener(GuiActionDispatcher dispatcher, GUISessionManager sessionManager,
      DebugUtil logger, MainGuiController mainGuiController,
      SubGuiController subGuiController) {
    this.dispatcher = dispatcher;
    this.sessionManager = sessionManager;
    this.logger = logger;
    this.mainGuiController = mainGuiController;
    this.subGuiController = subGuiController;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (!sessionManager.hasSession(player)) {
      return;
    }
    // 仅处理"顶部 GUI"库存的点击，避免玩家背包（底部栏）点击被错误映射
    // 说明：当 GUI 不是 6 行（非 54 格）时，Bukkit 的全视图包含顶部 GUI + 底部背包。
    // 此处明确要求：只有当点击发生在当前会话绑定的顶部库存时，才交给分发器处理。
    if (event.getClickedInventory() == null) {
      return;
    }
    var topInv = event.getView().getTopInventory();
    if (!sessionManager.validateSessionInventory(player, topInv)) {
      return; // 顶部库存并非当前会话的 GUI，忽略
    }

    // 关键：无条件取消事件，防止物品被默认逻辑移动/吞噬
    // 无论点击的是顶部GUI还是底部背包，都必须取消，防止Shift+点击把物品塞进GUI
    event.setCancelled(true);

    // 如果点击的是底部背包而非顶部GUI，不需要进一步处理点击逻辑
    if (event.getClickedInventory() != topInv) {
      return;
    }

    try {
      ClickContext context = ClickContext.from(event, sessionManager);

      // 在主界面中，优先处理清除绑定点击（绕过 dispatcher 的 dangerous 检查）
      String sessionId = sessionManager.getCurrentSessionId(player);
      if (sessionId != null && sessionId.startsWith("main:")) {
        ClearClickType clearClickType = mainGuiController.getClearClickType();
        if (clearClickType != ClearClickType.DISABLED && clearClickType.matches(context)) {
          int slot = event.getSlot();
          logger.debug("清除点击拦截: player=" + player.getName() + ", slot=" + slot
              + ", clickType=" + event.getClick() + ", clearClickType=" + clearClickType);
          mainGuiController.handleClearClickFromListener(player, slot);
          return;
        }
      }

      dispatcher.handleClick(context, event);
    } catch (Exception ex) {
      logger.error("处理 GUI 点击时发生异常", ex);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player && sessionManager.hasSession(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    if (!sessionManager.hasSession(player)) {
      return;
    }
    String sessionId = sessionManager.getCurrentSessionId(player);
    if (sessionManager.validateSessionInventory(player, event.getInventory())) {
      if (sessionId != null) {
        if (sessionId.startsWith("main:")) {
          mainGuiController.handleClose(player);
        } else if (sessionId.startsWith("sub:")) {
          subGuiController.handleClose(player);
        }
      }
      // 移除会导致无限递归的 closeSession 调用
      // sessionManager.closeSession(player);
    }
  }
}
