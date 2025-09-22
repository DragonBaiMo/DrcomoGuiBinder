package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.gui.ClickContext;
import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.gui.GuiManager;
import cn.drcomo.drcomoguibinder.config.PluginConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听 Bukkit 事件并桥接到核心库提供的调度器。
 */
public final class GuiListener implements Listener {

    private final GUISessionManager sessionManager;
    private final GuiActionDispatcher dispatcher;
    private final GuiManager guiManager;
    private final GuiEngine guiEngine;
    private final PluginConfig pluginConfig;
    private final Set<UUID> closingPlayers = ConcurrentHashMap.newKeySet();

    public GuiListener(GUISessionManager sessionManager,
                       GuiActionDispatcher dispatcher,
                       GuiManager guiManager,
                       GuiEngine guiEngine,
                       PluginConfig pluginConfig) {
        this.sessionManager = sessionManager;
        this.dispatcher = dispatcher;
        this.guiManager = guiManager;
        this.guiEngine = guiEngine;
        this.pluginConfig = pluginConfig;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!sessionManager.hasSession(player)) {
            return;
        }
        if (!sessionManager.isSessionInventoryClick(player, event)) {
            if (event.isShiftClick() || event.getClick().isKeyboardClick()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        ClickContext context = ClickContext.from(event, sessionManager);
        if (context.isDangerous()) {
            guiManager.clearCursor(player, event);
        }
        dispatcher.handleClick(context, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!sessionManager.hasSession(player)) {
            return;
        }
        if (sessionManager.validateSessionInventory(player, event.getInventory())) {
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
        if (!sessionManager.validateSessionInventory(player, event.getInventory())) {
            return;
        }
        if (!closingPlayers.add(player.getUniqueId())) {
            return;
        }
        try {
            guiEngine.handleInventoryClose(player);
        } finally {
            closingPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!pluginConfig.isCloseOnQuit()) {
            return;
        }
        guiEngine.closeForPlayer(event.getPlayer(), false);
    }
}
