package cn.drcomo.drcomoguibinder.bind;

import cn.drcomo.corelib.database.SQLiteDB;
import cn.drcomo.corelib.database.SQLiteDB.ResultSetHandler;
import cn.drcomo.corelib.util.DebugUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/**
 * 基于 SQLite 的绑定数据仓储。
 */
public final class BindingRepository {

  private static final ResultSetHandler<Binding> BINDING_ROW_MAPPER = BindingRepository::mapBinding;

  private final SQLiteDB database;
  private final DebugUtil logger;

  public BindingRepository(Plugin plugin, DebugUtil logger, String databasePath,
      List<String> schemaScripts) {
    this.logger = logger;
    this.database = new SQLiteDB(plugin, databasePath, schemaScripts);
  }

  public void connect() {
    try {
      database.connect();
      database.initializeSchema();
      logger.debug("SQLite 数据库初始化完成");
    } catch (Exception ex) {
      logger.error("初始化数据库失败", ex);
      throw new IllegalStateException("无法连接数据库", ex);
    }
  }

  public CompletableFuture<List<Binding>> loadAllBindings() {
    String sql = "SELECT player_uuid, main_id, slot_index, sub_id, entry_key, entry_value, updated_at"
        + " FROM gui_bindings";
    return database.queryListAsync(sql, BINDING_ROW_MAPPER).exceptionally(ex -> {
      logger.error("加载全部绑定数据失败", ex);
      throw new IllegalStateException(ex);
    });
  }

  public CompletableFuture<List<Binding>> loadBindings(UUID playerUuid) {
    String sql = "SELECT player_uuid, main_id, slot_index, sub_id, entry_key, entry_value, updated_at"
        + " FROM gui_bindings WHERE player_uuid=?";
    return database.queryListAsync(sql, BINDING_ROW_MAPPER, playerUuid.toString()).exceptionally(ex -> {
      logger.error("加载玩家绑定数据失败: " + playerUuid, ex);
      throw new IllegalStateException(ex);
    });
  }

  public CompletableFuture<Void> upsert(Binding binding) {
    String sql = "INSERT INTO gui_bindings (player_uuid, main_id, slot_index, sub_id, entry_key, entry_value, updated_at)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?)"
        + " ON CONFLICT(player_uuid, main_id, slot_index) DO UPDATE SET"
        + " sub_id=excluded.sub_id, entry_key=excluded.entry_key, entry_value=excluded.entry_value,"
        + " updated_at=excluded.updated_at";
    return database
        .executeUpdateAsync(sql, binding.getPlayerUuid().toString(), binding.getMainId(),
            binding.getSlot(), binding.getSubId(), binding.getEntryKey(), binding.getEntryValue(),
            binding.getUpdatedAt())
        .thenApply(rows -> null)
        .exceptionally(ex -> {
          logger.error("写入绑定记录失败", ex);
          throw new IllegalStateException(ex);
        });
  }

  public CompletableFuture<Void> delete(UUID playerUuid, String mainId, int slot) {
    String sql = "DELETE FROM gui_bindings WHERE player_uuid=? AND main_id=? AND slot_index=?";
    return database.executeUpdateAsync(sql, playerUuid.toString(), mainId, slot).thenApply(rows -> null)
        .exceptionally(ex -> {
          logger.error("删除绑定失败", ex);
          throw new IllegalStateException(ex);
        });
  }

  public CompletableFuture<Void> deleteAllForPlayer(UUID playerUuid) {
    String sql = "DELETE FROM gui_bindings WHERE player_uuid=?";
    return database.executeUpdateAsync(sql, playerUuid.toString()).thenApply(rows -> null)
        .exceptionally(ex -> {
          logger.error("删除玩家全部绑定失败", ex);
          throw new IllegalStateException(ex);
        });
  }

  public void close() {
    try {
      database.disconnect();
    } catch (Exception ex) {
      logger.error("关闭数据库连接失败", ex);
    }
  }

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
}
