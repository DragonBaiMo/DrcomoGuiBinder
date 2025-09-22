package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.gui.ClickContext;
import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.util.DebugUtil;
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
    try {
      ClickContext context = ClickContext.from(event, sessionManager);
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
