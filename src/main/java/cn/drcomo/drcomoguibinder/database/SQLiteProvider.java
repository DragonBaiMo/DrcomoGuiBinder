package cn.drcomo.drcomoguibinder.database;

import cn.drcomo.corelib.database.SQLiteDB;
import cn.drcomo.corelib.util.DebugUtil;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/**
 * SQLite 数据库提供者，基于 DrcomoCoreLib 的 SQLiteDB 实现。
 * 充分利用 DrcomoCoreLib 的连接池、事务管理和性能监控功能。
 */
public final class SQLiteProvider implements DatabaseProvider {

    private final SQLiteDB sqliteDB;
    private final DebugUtil logger;

    /**
     * 构造 SQLite 提供者
     *
     * @param plugin 插件实例
     * @param logger 日志工具
     * @param databasePath 数据库文件路径
     * @param schemaScripts 初始化脚本文件名列表
     */
    public SQLiteProvider(Plugin plugin, DebugUtil logger, String databasePath, List<String> schemaScripts) {
        this.logger = logger;
        this.sqliteDB = new SQLiteDB(plugin, databasePath, schemaScripts);

        // 配置连接池参数以提升性能
        sqliteDB.getConfig()
            .maximumPoolSize(15)
            .connectionTestQuery("SELECT 1");

        logger.debug("SQLite 提供者已创建，数据库路径: " + databasePath);
    }

    @Override
    public void connect() throws SQLException {
        try {
            sqliteDB.connect();
            logger.info("SQLite 连接成功，连接池状态: " + sqliteDB.getPoolStatus().getTotalConnections() + " 个连接");
        } catch (Exception ex) {
            logger.error("SQLite 连接失败", ex);
            throw new SQLException("无法连接到 SQLite 数据库", ex);
        }
    }

    @Override
    public void disconnect() {
        try {
            // 记录性能统计信息
            var metrics = sqliteDB.getMetrics();
            logger.info("SQLite 断开连接，执行统计 - 总语句数: " + metrics.getExecutedStatements() +
                       ", 平均耗时: " + metrics.getAverageExecutionTimeMillis() + "ms");

            sqliteDB.disconnect();
            logger.debug("SQLite 连接已关闭");
        } catch (Exception ex) {
            logger.warn("关闭 SQLite 连接时发生异常: " + ex.getMessage());
        }
    }

    @Override
    public void initializeSchema() throws SQLException {
        try {
            sqliteDB.initializeSchema();
            logger.info("SQLite 表结构初始化完成");
        } catch (Exception ex) {
            logger.error("SQLite 表结构初始化失败", ex);
            throw new SQLException("无法初始化 SQLite 表结构", ex);
        }
    }

    @Override
    public boolean isConnectionValid() {
        return sqliteDB.isConnectionValid();
    }

    @Override
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try {
            int result = sqliteDB.executeUpdate(sql, params);
            logger.debug("SQLite 更新完成，受影响行数: " + result);
            return result;
        } catch (Exception ex) {
            logger.error("SQLite 执行更新失败: " + sql, ex);
            throw new SQLException("SQLite 更新操作失败", ex);
        }
    }

    @Override
    public <T> T queryOne(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        try {
            // 直接使用 DrcomoCoreLib 的 ResultSetHandler，接口兼容
            SQLiteDB.ResultSetHandler<T> sqliteHandler = handler::handle;
            T result = sqliteDB.queryOne(sql, sqliteHandler, params);
            logger.debug("SQLite 单行查询完成，结果: " + (result != null ? "找到" : "未找到"));
            return result;
        } catch (Exception ex) {
            logger.error("SQLite 单行查询失败: " + sql, ex);
            throw new SQLException("SQLite 查询操作失败", ex);
        }
    }

    @Override
    public <T> List<T> queryList(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        try {
            // 直接使用 DrcomoCoreLib 的 ResultSetHandler，接口兼容
            SQLiteDB.ResultSetHandler<T> sqliteHandler = handler::handle;
            List<T> results = sqliteDB.queryList(sql, sqliteHandler, params);
            logger.debug("SQLite 多行查询完成，结果数量: " + results.size());
            return results;
        } catch (Exception ex) {
            logger.error("SQLite 多行查询失败: " + sql, ex);
            throw new SQLException("SQLite 查询操作失败", ex);
        }
    }

    @Override
    public CompletableFuture<Integer> executeUpdateAsync(String sql, Object... params) {
        return sqliteDB.executeUpdateAsync(sql, params)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("SQLite 异步更新失败: " + sql, ex);
                } else {
                    logger.debug("SQLite 异步更新完成，受影响行数: " + result);
                }
            })
            .exceptionally(ex -> {
                throw new RuntimeException("SQLite 异步更新操作失败", ex);
            });
    }

    @Override
    public <T> CompletableFuture<T> queryOneAsync(String sql, ResultSetHandler<T> handler, Object... params) {
        // 直接使用方法引用，更简洁
        SQLiteDB.ResultSetHandler<T> sqliteHandler = handler::handle;
        return sqliteDB.queryOneAsync(sql, sqliteHandler, params)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("SQLite 异步单行查询失败: " + sql, ex);
                } else {
                    logger.debug("SQLite 异步单行查询完成，结果: " + (result != null ? "找到" : "未找到"));
                }
            })
            .exceptionally(ex -> {
                throw new RuntimeException("SQLite 异步查询操作失败", ex);
            });
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(String sql, ResultSetHandler<T> handler, Object... params) {
        // 直接使用方法引用，更简洁
        SQLiteDB.ResultSetHandler<T> sqliteHandler = handler::handle;
        return sqliteDB.queryListAsync(sql, sqliteHandler, params)
            .whenComplete((results, ex) -> {
                if (ex != null) {
                    logger.error("SQLite 异步多行查询失败: " + sql, ex);
                } else {
                    logger.debug("SQLite 异步多行查询完成，结果数量: " + results.size());
                }
            })
            .exceptionally(ex -> {
                throw new RuntimeException("SQLite 异步查询操作失败", ex);
            });
    }

    @Override
    public String getDatabaseType() {
        return "SQLite";
    }

    /**
     * 获取 DrcomoCoreLib 的 SQLiteDB 实例，供高级操作使用
     *
     * @return SQLiteDB 实例
     */
    public SQLiteDB getSQLiteDB() {
        return sqliteDB;
    }

    /**
     * 执行事务操作，利用 DrcomoCoreLib 的事务管理
     *
     * @param operation 事务操作
     * @throws SQLException 执行失败时抛出异常
     */
    public void executeInTransaction(SQLiteDB.SQLRunnable operation) throws SQLException {
        try {
            sqliteDB.transaction(operation);
            logger.debug("SQLite 事务执行完成");
        } catch (Exception ex) {
            logger.error("SQLite 事务执行失败", ex);
            throw new SQLException("SQLite 事务操作失败", ex);
        }
    }

    /**
     * 异步执行事务操作
     *
     * @param operation 事务操作
     * @return CompletableFuture
     */
    public CompletableFuture<Void> executeInTransactionAsync(SQLiteDB.SQLRunnable operation) {
        return sqliteDB.transactionAsync(operation)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("SQLite 异步事务执行失败", ex);
                } else {
                    logger.debug("SQLite 异步事务执行完成");
                }
            });
    }
}
