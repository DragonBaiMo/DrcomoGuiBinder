package cn.drcomo.drcomoguibinder.bind;

import cn.drcomo.drcomoguibinder.database.DatabaseProvider;
import cn.drcomo.drcomoguibinder.database.DatabaseProvider.ResultSetHandler;
import cn.drcomo.drcomoguibinder.database.SqlQueryManager;
import cn.drcomo.corelib.util.DebugUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 绑定数据仓储，支持 SQLite 和 MySQL。
 * 使用 SqlQueryManager 解决不同数据库间的语法差异问题。
 */
public final class BindingRepository {

  private static final ResultSetHandler<Binding> BINDING_ROW_MAPPER = BindingRepository::mapBinding;

  private final DatabaseProvider database;
  private final DebugUtil logger;
  private final SqlQueryManager sqlManager;

  /**
   * 构造绑定数据仓储
   *
   * @param database 数据库提供者
   * @param logger 日志工具
   */
  public BindingRepository(DatabaseProvider database, DebugUtil logger) {
    this.database = database;
    this.logger = logger;
    // 根据数据库类型创建相应的 SQL 管理器
    this.sqlManager = new SqlQueryManager(SqlQueryManager.getDatabaseType(database));

    logger.debug("绑定数据仓储已初始化，数据库类型: " + database.getDatabaseType());
  }

  public void connect() {
    try {
      database.connect();
      database.initializeSchema();
      logger.debug(database.getDatabaseType() + " 数据库初始化完成");
    } catch (Exception ex) {
      logger.error("初始化数据库失败", ex);
      throw new IllegalStateException("无法连接数据库", ex);
    }
  }

  public CompletableFuture<List<Binding>> loadAllBindings() {
    String sql = sqlManager.getSelectAllBindingsSql();
    return database.queryListAsync(sql, BINDING_ROW_MAPPER)
        .whenComplete((bindings, ex) -> {
          if (ex != null) {
            logger.error("加载全部绑定数据失败", ex);
          } else {
            logger.debug("加载全部绑定数据完成，数量: " + bindings.size());
          }
        })
        .exceptionally(ex -> {
          throw new IllegalStateException("无法加载绑定数据", ex);
        });
  }

  public CompletableFuture<List<Binding>> loadBindings(UUID playerUuid) {
    String sql = sqlManager.getSelectPlayerBindingsSql();
    return database.queryListAsync(sql, BINDING_ROW_MAPPER, playerUuid.toString())
        .whenComplete((bindings, ex) -> {
          if (ex != null) {
            logger.error("加载玩家绑定数据失败: " + playerUuid, ex);
          } else {
            logger.debug("加载玩家绑定数据完成: " + playerUuid + ", 数量: " + bindings.size());
          }
        })
        .exceptionally(ex -> {
          throw new IllegalStateException("无法加载玩家绑定数据", ex);
        });
  }

  public CompletableFuture<Void> upsert(Binding binding) {
    // 使用 SqlQueryManager 获取适合当前数据库的 UPSERT 语句
    String sql = sqlManager.getUpsertBindingSql();
    return database
        .executeUpdateAsync(sql, binding.getPlayerUuid().toString(), binding.getMainId(),
            binding.getSlot(), binding.getSubId(), binding.getEntryKey(), binding.getEntryValue(),
            binding.getUpdatedAt())
        .whenComplete((rows, ex) -> {
          if (ex != null) {
            logger.error("写入绑定记录失败: " + binding.getPlayerUuid() + ", " + binding.getMainId() + ":" + binding.getSlot(), ex);
          } else {
            logger.debug("绑定记录已更新: " + binding.getPlayerUuid() + ", " + binding.getMainId() + ":" + binding.getSlot());
          }
        })
        .thenApply(rows -> (Void) null)
        .exceptionally(ex -> {
          throw new RuntimeException("无法写入绑定记录", ex);
        });
  }

  public CompletableFuture<Void> delete(UUID playerUuid, String mainId, int slot) {
    String sql = sqlManager.getDeleteBindingSql();
    return database.executeUpdateAsync(sql, playerUuid.toString(), mainId, slot)
        .whenComplete((rows, ex) -> {
          if (ex != null) {
            logger.error("删除绑定失败: " + playerUuid + ", " + mainId + ":" + slot, ex);
          } else {
            logger.debug("绑定已删除: " + playerUuid + ", " + mainId + ":" + slot + ", 受影响行数: " + rows);
          }
        })
        .thenApply(rows -> (Void) null)
        .exceptionally(ex -> {
          throw new RuntimeException("无法删除绑定记录", ex);
        });
  }

  public CompletableFuture<Void> deleteAllForPlayer(UUID playerUuid) {
    String sql = sqlManager.getDeleteAllPlayerBindingsSql();
    return database.executeUpdateAsync(sql, playerUuid.toString())
        .whenComplete((rows, ex) -> {
          if (ex != null) {
            logger.error("删除玩家全部绑定失败: " + playerUuid, ex);
          } else {
            logger.debug("玩家全部绑定已删除: " + playerUuid + ", 受影响行数: " + rows);
          }
        })
        .thenApply(rows -> (Void) null)
        .exceptionally(ex -> {
          throw new RuntimeException("无法删除玩家绑定记录", ex);
        });
  }

  /**
   * 获取绑定数量统计
   *
   * @return 绑定总数
   */
  public CompletableFuture<Integer> getBindingCount() {
    String sql = sqlManager.getCountBindingsSql();
    return database.queryOneAsync(sql, rs -> rs.getInt(1))
        .whenComplete((count, ex) -> {
          if (ex != null) {
            logger.error("统计绑定数量失败", ex);
          } else {
            logger.debug("绑定总数: " + count);
          }
        })
        .exceptionally(ex -> {
          throw new RuntimeException("无法统计绑定数量", ex);
        });
  }

  /**
   * 获取指定玩家的绑定数量统计
   *
   * @param playerUuid 玩家 UUID
   * @return 玩家绑定数量
   */
  public CompletableFuture<Integer> getPlayerBindingCount(UUID playerUuid) {
    String sql = sqlManager.getCountPlayerBindingsSql();
    return database.queryOneAsync(sql, rs -> rs.getInt(1), playerUuid.toString())
        .whenComplete((count, ex) -> {
          if (ex != null) {
            logger.error("统计玩家绑定数量失败: " + playerUuid, ex);
          } else {
            logger.debug("玩家绑定数量: " + playerUuid + " -> " + count);
          }
        })
        .exceptionally(ex -> {
          throw new RuntimeException("无法统计玩家绑定数量", ex);
        });
  }

  /**
   * 清空所有绑定数据（谨慎使用）
   *
   * @return CompletableFuture
   */
  public CompletableFuture<Void> truncateBindings() {
    String sql = sqlManager.getTruncateBindingsSql();
    return database.executeUpdateAsync(sql)
        .whenComplete((rows, ex) -> {
          if (ex != null) {
            logger.error("清空绑定数据失败", ex);
          } else {
            logger.warn("已清空所有绑定数据，受影响行数: " + rows);
          }
        })
        .thenApply(rows -> (Void) null)
        .exceptionally(ex -> {
          throw new RuntimeException("无法清空绑定数据", ex);
        });
  }

  /**
   * 关闭数据库连接
   */
  public void close() {
    try {
      logger.info("关闭数据库连接...");
      database.disconnect();
      logger.debug("数据库连接已关闭");
    } catch (Exception ex) {
      logger.error("关闭数据库连接失败", ex);
    }
  }

  /**
   * 将 ResultSet 中的一行数据映射为 Binding 对象
   *
   * @param rs 结果集
   * @return Binding 对象
   * @throws SQLException 数据库异常
   */
  private static Binding mapBinding(ResultSet rs) throws SQLException {
    UUID player = UUID.fromString(rs.getString("player_uuid"));
    String mainId = rs.getString("main_id");
    int slot = rs.getInt("slot_index");
    String subId = rs.getString("sub_id");
    String entryKey = rs.getString("entry_key");
    String entryValue = rs.getString("entry_value");
    long updatedAt = rs.getLong("updated_at");
    return new Binding(player, mainId, slot, subId, entryKey, entryValue, updatedAt);
  }

  /**
   * 获取 SQL 查询管理器，供高级操作使用
   *
   * @return SqlQueryManager 实例
   */
  public SqlQueryManager getSqlManager() {
    return sqlManager;
  }
}
