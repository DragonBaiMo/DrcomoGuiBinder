package cn.drcomo.drcomoguibinder.database;

/**
 * SQL 语句管理器，处理不同数据库之间的语法差异。
 *
 * 主要解决 SQLite 和 MySQL 之间的语法不兼容问题，
 * 如 UPSERT 语句的不同实现方式。
 */
public final class SqlQueryManager {

    private final DatabaseType databaseType;

    /**
     * 数据库类型枚举
     */
    public enum DatabaseType {
        SQLITE, MYSQL
    }

    /**
     * 构造 SQL 语句管理器
     *
     * @param databaseType 数据库类型
     */
    public SqlQueryManager(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    /**
     * 根据数据库提供者获取对应的数据库类型
     *
     * @param provider 数据库提供者
     * @return 数据库类型
     */
    public static DatabaseType getDatabaseType(DatabaseProvider provider) {
        String type = provider.getDatabaseType().toLowerCase();
        switch (type) {
            case "sqlite":
                return DatabaseType.SQLITE;
            case "mysql":
                return DatabaseType.MYSQL;
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + type);
        }
    }

    /**
     * 获取查询所有绑定的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getSelectAllBindingsSql() {
        return "SELECT player_uuid, main_id, slot_index, sub_id, entry_key, entry_value, updated_at"
                + " FROM gui_bindings";
    }

    /**
     * 获取查询指定玩家绑定的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getSelectPlayerBindingsSql() {
        return "SELECT player_uuid, main_id, slot_index, sub_id, entry_key, entry_value, updated_at"
                + " FROM gui_bindings WHERE player_uuid=?";
    }

    /**
     * 获取 UPSERT（插入或更新）绑定的 SQL 语句
     *
     * SQLite 使用 ON CONFLICT...DO UPDATE 语法
     * MySQL 使用 ON DUPLICATE KEY UPDATE 语法
     *
     * @return SQL 语句
     */
    public String getUpsertBindingSql() {
        String baseInsert = "INSERT INTO gui_bindings (player_uuid, main_id, slot_index, sub_id, entry_key, entry_value, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";

        switch (databaseType) {
            case SQLITE:
                return baseInsert
                        + " ON CONFLICT(player_uuid, main_id, slot_index) DO UPDATE SET"
                        + " sub_id=excluded.sub_id, entry_key=excluded.entry_key, entry_value=excluded.entry_value,"
                        + " updated_at=excluded.updated_at";

            case MYSQL:
                return baseInsert
                        + " ON DUPLICATE KEY UPDATE"
                        + " sub_id=VALUES(sub_id), entry_key=VALUES(entry_key), entry_value=VALUES(entry_value),"
                        + " updated_at=VALUES(updated_at)";

            default:
                throw new IllegalStateException("不支持的数据库类型: " + databaseType);
        }
    }

    /**
     * 获取删除指定绑定的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getDeleteBindingSql() {
        return "DELETE FROM gui_bindings WHERE player_uuid=? AND main_id=? AND slot_index=?";
    }

    /**
     * 获取删除玩家所有绑定的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getDeleteAllPlayerBindingsSql() {
        return "DELETE FROM gui_bindings WHERE player_uuid=?";
    }

    /**
     * 获取批量插入绑定的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getBatchInsertBindingSql() {
        return "INSERT INTO gui_bindings (player_uuid, main_id, slot_index, sub_id, entry_key, entry_value, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * 获取统计绑定数量的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getCountBindingsSql() {
        return "SELECT COUNT(*) FROM gui_bindings";
    }

    /**
     * 获取统计指定玩家绑定数量的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getCountPlayerBindingsSql() {
        return "SELECT COUNT(*) FROM gui_bindings WHERE player_uuid=?";
    }

    /**
     * 获取清空所有绑定的 SQL 语句
     *
     * @return SQL 语句
     */
    public String getTruncateBindingsSql() {
        switch (databaseType) {
            case SQLITE:
                return "DELETE FROM gui_bindings";
            case MYSQL:
                return "TRUNCATE TABLE gui_bindings";
            default:
                throw new IllegalStateException("不支持的数据库类型: " + databaseType);
        }
    }

    /**
     * 获取当前数据库类型
     *
     * @return 数据库类型
     */
    public DatabaseType getDatabaseType() {
        return databaseType;
    }
}