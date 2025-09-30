package cn.drcomo.drcomoguibinder.listener;

import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.corelib.util.DebugUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家进服监听器，用于跨服同步绑定缓存
 */
public final class PlayerJoinListener implements Listener {

  private final BindingService bindingService;
  private final DebugUtil logger;

  // 节流：记录上次刷新时间（毫秒），5秒内同一玩家不重复刷新
  private final Map<UUID, Long> lastRefreshTime = new ConcurrentHashMap<>();
  private static final long THROTTLE_MS = 5000L;

  public PlayerJoinListener(BindingService bindingService, DebugUtil logger) {
    this.bindingService = bindingService;
    this.logger = logger;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerJoin(PlayerJoinEvent event) {
    UUID playerUuid = event.getPlayer().getUniqueId();

    // 节流检查
    long now = System.currentTimeMillis();
    Long lastTime = lastRefreshTime.get(playerUuid);
    if (lastTime != null && (now - lastTime) < THROTTLE_MS) {
      logger.debug("跳过重复刷新: " + playerUuid + "，距上次刷新不足 " + THROTTLE_MS + " 毫秒");
      return;
    }

    // 更新刷新时间并触发异步刷新
    lastRefreshTime.put(playerUuid, now);

    bindingService.refreshPlayerCache(playerUuid).whenComplete((v, ex) -> {
      if (ex != null) {
        logger.error("玩家进服刷新缓存失败: " + event.getPlayer().getName(), ex);
      } else {
        logger.debug("玩家进服刷新缓存完成: " + event.getPlayer().getName());
      }
    });
  }
}