package cn.drcomo.drcomoguibinder.bind;

import cn.drcomo.corelib.async.AsyncTaskManager;
import cn.drcomo.corelib.util.DebugUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 管理内存缓存与数据库写入的绑定服务。
 */
public final class BindingService {

  private final Map<BindingKey, Binding> cache = new ConcurrentHashMap<>();
  private final BindingRepository repository;
  private final AsyncTaskManager asyncManager;
  private final DebugUtil logger;
  private final ExecutorService executor;
  private final AtomicInteger pendingWrites = new AtomicInteger();

  /** 绑定变更时的回调（用于清除占位符缓存） */
  private volatile Consumer<UUID> bindingChangeListener;

  private volatile CompletableFuture<Void> writeChain = CompletableFuture.completedFuture(null);

  public BindingService(BindingRepository repository, AsyncTaskManager asyncManager,
      DebugUtil logger) {
    this.repository = repository;
    this.asyncManager = asyncManager;
    this.logger = logger;
    this.executor = asyncManager.getExecutor();
  }

  /**
   * 设置绑定变更监听器（用于清除占位符缓存）。
   *
   * @param listener 接收变更玩家 UUID 的回调
   */
  public void setBindingChangeListener(Consumer<UUID> listener) {
    this.bindingChangeListener = listener;
  }

  /**
   * 通知绑定变更。
   */
  private void notifyBindingChange(UUID playerUuid) {
    Consumer<UUID> listener = bindingChangeListener;
    if (listener != null && playerUuid != null) {
      listener.accept(playerUuid);
    }
  }

  public CompletableFuture<Void> loadAll() {
    return repository.loadAllBindings().thenAccept(bindings -> {
      cache.clear();
      for (Binding binding : bindings) {
        cache.put(new BindingKey(binding.getPlayerUuid(), binding.getMainId(), binding.getSlot()),
            binding);
      }
      logger.info("已加载绑定记录: " + bindings.size());
    });
  }

  public Binding get(UUID playerUuid, String mainId, int slot) {
    return cache.get(new BindingKey(playerUuid, mainId, slot));
  }

  public List<Binding> listPlayer(UUID playerUuid) {
    List<Binding> result = new ArrayList<>();
    cache.forEach((key, value) -> {
      if (playerUuid.equals(key.getPlayerUuid())) {
        result.add(value);
      }
    });
    return result;
  }

  public List<Binding> listPlayerMain(UUID playerUuid, String mainId) {
    List<Binding> result = new ArrayList<>();
    cache.forEach((key, value) -> {
      if (playerUuid.equals(key.getPlayerUuid())
          && mainId.equalsIgnoreCase(key.getMainId())) {
        result.add(value);
      }
    });
    return result;
  }

  public CompletableFuture<Binding> bind(Binding binding) {
    BindingKey key = new BindingKey(binding.getPlayerUuid(), binding.getMainId(), binding.getSlot());
    cache.put(key, binding);
    notifyBindingChange(binding.getPlayerUuid());
    return enqueueWrite(() -> repository.upsert(binding)).thenApply(v -> binding);
  }

  public CompletableFuture<Boolean> clear(UUID playerUuid, String mainId, int slot) {
    BindingKey key = new BindingKey(playerUuid, mainId, slot);
    Binding removed = cache.remove(key);
    if (removed == null) {
      return CompletableFuture.completedFuture(false);
    }
    notifyBindingChange(playerUuid);
    return enqueueWrite(() -> repository.delete(playerUuid, mainId, slot)).thenApply(v -> true);
  }

  public AsyncTaskManager getAsyncManager() {
    return asyncManager;
  }

  public CompletableFuture<Void> clearAll(UUID playerUuid) {
    cache.keySet().removeIf(key -> playerUuid.equals(key.getPlayerUuid()));
    notifyBindingChange(playerUuid);
    return enqueueWrite(() -> repository.deleteAllForPlayer(playerUuid));
  }

  public int getCacheSize() {
    return cache.size();
  }

  public int getPendingWrites() {
    return pendingWrites.get();
  }

  public CompletableFuture<Void> flush() {
    CompletableFuture<Void> chainSnapshot;
    synchronized (this) {
      chainSnapshot = writeChain;
    }
    return chainSnapshot;
  }

  /**
   * 刷新指定玩家的绑定缓存（跨服同步）
   *
   * @param playerUuid 玩家 UUID
   * @return 刷新操作的 CompletableFuture
   */
  public CompletableFuture<Void> refreshPlayerCache(UUID playerUuid) {
    return repository.loadBindings(playerUuid).thenAccept(bindings -> {
      // 先清除该玩家的旧缓存
      cache.keySet().removeIf(key -> playerUuid.equals(key.getPlayerUuid()));

      // 加载最新数据到缓存
      for (Binding binding : bindings) {
        cache.put(new BindingKey(binding.getPlayerUuid(), binding.getMainId(), binding.getSlot()),
            binding);
      }
      logger.debug("已刷新玩家缓存: " + playerUuid + ", 绑定数量: " + bindings.size());
    }).exceptionally(ex -> {
      logger.error("刷新玩家缓存失败: " + playerUuid, ex);
      return null;
    });
  }

  private CompletableFuture<Void> enqueueWrite(TaskSupplier supplier) {
    CompletableFuture<Void> next;
    synchronized (this) {
      pendingWrites.incrementAndGet();
      writeChain = writeChain.thenComposeAsync(v -> supplier.get(), executor)
          .whenComplete((v, ex) -> pendingWrites.decrementAndGet());
      next = writeChain;
    }
    return next;
  }

  @FunctionalInterface
  private interface TaskSupplier {
    CompletableFuture<Void> get();
  }
}
