package cn.drcomo.drcomoguibinder.database;

import cn.drcomo.corelib.async.AsyncTaskManager;
import cn.drcomo.corelib.util.DebugUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.plugin.Plugin;

/**
 * MySQL 数据库提供者，采用 DrcomoCoreLib 的设计理念。
 * 使用简单的 JDBC 连接，配合 DrcomoCoreLib 的异步任务管理。
 */
public final class MySQLProvider implements DatabaseProvider {

    private final Plugin plugin;
    private final DebugUtil logger;
    private final AsyncTaskManager asyncManager;

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final List<String> schemaScripts;

    private Connection connection;

    // 性能监控计数器，借鉴 DrcomoCoreLib 的设计
    private final AtomicLong executedStatements = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    /**
     * 构造 MySQL 提供者
     *
     * @param plugin 插件实例
     * @param logger 日志工具
     * @param asyncManager 异步任务管理器
     * @param host MySQL 主机地址
     * @param port MySQL 端口
     * @param database 数据库名称
     * @param username 用户名
     * @param password 密码
     * @param schemaScripts 初始化脚本文件名列表
     */
    public MySQLProvider(Plugin plugin, DebugUtil logger, AsyncTaskManager asyncManager,
                        String host, int port, String database, String username, String password,
                        List<String> schemaScripts) {
        this.plugin = plugin;
        this.logger = logger;
        this.asyncManager = asyncManager;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.schemaScripts = schemaScripts != null ? schemaScripts : List.of("schema-mysql.sql");

        logger.debug("MySQL 提供者已创建，连接: " + host + ":" + port + "/" + database);
    }

    @Override
    public void connect() throws SQLException {
        try {
            String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=UTC",
                    host, port, database);

            this.connection = DriverManager.getConnection(url, username, password);
            this.connection.setAutoCommit(false);

            logger.info("MySQL 连接成功: " + host + ":" + port + "/" + database);

        } catch (Exception ex) {
            logger.error("MySQL 连接失败", ex);
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeEx) {
                    logger.warn("关闭失败连接时发生异常: " + closeEx.getMessage());
                }
                connection = null;
            }
            throw new SQLException("无法连接到 MySQL 数据库", ex);
        }
    }

    @Override
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                // 记录性能统计信息，类似 DrcomoCoreLib 的做法
                long avgTime = executedStatements.get() > 0 ?
                    totalExecutionTime.get() / executedStatements.get() : 0;

                logger.info("MySQL 断开连接，执行统计 - 总语句数: " + executedStatements.get() +
                           ", 平均耗时: " + avgTime + "ms");

                connection.close();
                connection = null;
                logger.debug("MySQL 连接已关闭");
            }
        } catch (Exception ex) {
            logger.warn("关闭 MySQL 连接时发生异常: " + ex.getMessage());
        }
    }

    @Override
    public void initializeSchema() throws SQLException {
        if (schemaScripts == null || schemaScripts.isEmpty()) {
            logger.debug("没有指定 MySQL 初始化脚本");
            return;
        }

        try {
            // 显式切换到目标数据库，避免在未选定 schema 下执行建表
            try (var useStmt = connection.prepareStatement("USE `" + database + "`")) {
                useStmt.execute();
            }
            for (String scriptName : schemaScripts) {
                logger.debug("执行 MySQL 初始化脚本: " + scriptName);
                executeSchemaScript(scriptName);
            }

            connection.commit();

            // 初始化后进行一次表存在性校验，尽早发现问题
            if (!checkTableExists("gui_bindings")) {
                logger.error("初始化后未找到目标表: " + database + ".gui_bindings");
                throw new SQLException("初始化脚本执行后仍未检测到表: gui_bindings");
            }

            logger.info("MySQL 表结构初始化完成");
        } catch (Exception ex) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.warn("回滚初始化事务时发生异常: " + rollbackEx.getMessage());
            }
            logger.error("MySQL 表结构初始化失败", ex);
            throw new SQLException("无法初始化 MySQL 表结构", ex);
        }
    }

    @Override
    public boolean isConnectionValid() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (Exception ex) {
            logger.warn("检查 MySQL 连接状态时发生异常: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public int executeUpdate(String sql, Object... params) throws SQLException {
        long startTime = System.currentTimeMillis();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            setParameters(stmt, params);
            int result = stmt.executeUpdate();

            // 记录性能统计
            recordExecution(startTime);
            logger.debug("MySQL 更新操作完成，受影响行数: " + result);
            return result;

        } catch (Exception ex) {
            recordExecution(startTime); // 即使失败也记录统计
            logger.error("MySQL 执行更新失败: " + sql, ex);
            throw new SQLException("MySQL 更新操作失败", ex);
        }
    }

    @Override
    public <T> T queryOne(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        long startTime = System.currentTimeMillis();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                T result = null;
                if (rs.next()) {
                    result = handler.handle(rs);
                }

                // 记录性能统计
                recordExecution(startTime);
                logger.debug("MySQL 单行查询完成，结果: " + (result != null ? "找到" : "未找到"));
                return result;
            }
        } catch (Exception ex) {
            recordExecution(startTime);
            logger.error("MySQL 单行查询失败: " + sql, ex);
            throw new SQLException("MySQL 查询操作失败", ex);
        }
    }

    @Override
    public <T> List<T> queryList(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        long startTime = System.currentTimeMillis();
        List<T> results = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(handler.handle(rs));
                }
            }

            // 记录性能统计
            recordExecution(startTime);
            logger.debug("MySQL 多行查询完成，结果数量: " + results.size());
            return results;

        } catch (Exception ex) {
            recordExecution(startTime);
            logger.error("MySQL 多行查询失败: " + sql, ex);
            throw new SQLException("MySQL 查询操作失败", ex);
        }
    }

    @Override
    public CompletableFuture<Integer> executeUpdateAsync(String sql, Object... params) {
        return asyncManager.supplyAsync(() -> {
            try {
                return executeUpdate(sql, params);
            } catch (SQLException ex) {
                logger.error("MySQL 异步更新失败: " + sql, ex);
                throw new RuntimeException("MySQL 异步更新操作失败", ex);
            }
        }).whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("MySQL 异步更新完成时发生异常", ex);
            } else {
                logger.debug("MySQL 异步更新完成，受影响行数: " + result);
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> queryOneAsync(String sql, ResultSetHandler<T> handler, Object... params) {
        return asyncManager.supplyAsync(() -> {
            try {
                return queryOne(sql, handler, params);
            } catch (SQLException ex) {
                logger.error("MySQL 异步单行查询失败: " + sql, ex);
                throw new RuntimeException("MySQL 异步查询操作失败", ex);
            }
        }).whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("MySQL 异步单行查询完成时发生异常", ex);
            } else {
                logger.debug("MySQL 异步单行查询完成，结果: " + (result != null ? "找到" : "未找到"));
            }
        });
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(String sql, ResultSetHandler<T> handler, Object... params) {
        return asyncManager.supplyAsync(() -> {
            try {
                return queryList(sql, handler, params);
            } catch (SQLException ex) {
                logger.error("MySQL 异步多行查询失败: " + sql, ex);
                throw new RuntimeException("MySQL 异步查询操作失败", ex);
            }
        }).whenComplete((results, ex) -> {
            if (ex != null) {
                logger.error("MySQL 异步多行查询完成时发生异常", ex);
            } else {
                logger.debug("MySQL 异步多行查询完成，结果数量: " + results.size());
            }
        });
    }

    @Override
    public String getDatabaseType() {
        return "MySQL";
    }

    /**
     * 记录语句执行性能统计
     */
    private void recordExecution(long startTime) {
        executedStatements.incrementAndGet();
        totalExecutionTime.addAndGet(System.currentTimeMillis() - startTime);
    }

    /**
     * 设置 PreparedStatement 参数
     */
    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
        }
    }

    /**
     * 执行初始化脚本
     */
    private void executeSchemaScript(String scriptName) throws Exception {
        logger.debug("开始执行 MySQL 脚本: " + scriptName);
        
        // 从插件资源中读取SQL脚本
        try (var inputStream = plugin.getResource(scriptName)) {
            if (inputStream == null) {
                throw new Exception("找不到SQL脚本文件: " + scriptName);
            }

            // 读取脚本内容并拆解为语句列表，确保注释不会导致语句被跳过
            String scriptContent = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            List<String> statements = parseSqlStatements(scriptContent);

            for (String statement : statements) {
                logger.debug("执行SQL语句: " + statement);
                try (var stmt = connection.prepareStatement(statement)) {
                    stmt.executeUpdate();
                }
            }

            logger.info("MySQL 脚本执行完成: " + scriptName);

        } catch (Exception ex) {
            logger.error("执行 MySQL 脚本失败: " + scriptName, ex);
            throw new Exception("无法执行SQL脚本: " + scriptName, ex);
        }
    }

    /**
     * 检查指定表是否存在于当前数据库中
     */
    private boolean checkTableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?";
        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, database);
            stmt.setString(2, tableName);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception ex) {
            logger.warn("检查表是否存在时发生异常: " + ex.getMessage());
        }
        return false;
    }

    /**
     * 获取性能统计信息，类似 DrcomoCoreLib 的 getMetrics
     *
     * @return 性能统计描述
     */
    public String getMetrics() {
        long statements = executedStatements.get();
        long avgTime = statements > 0 ? totalExecutionTime.get() / statements : 0;

        return String.format("已执行语句: %d, 平均耗时: %dms", statements, avgTime);
    }

    /**
     * 将脚本内容转换为可执行的 SQL 语句列表，自动忽略空行和注释。
     *
     * @param scriptContent SQL 脚本全文
     * @return SQL 语句列表
     */
    private List<String> parseSqlStatements(String scriptContent) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        for (String rawLine : scriptContent.split("\\r?\\n")) {
            String trimmedLine = rawLine.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("--")) {
                continue;
            }

            currentStatement.append(rawLine).append('\n');

            if (trimmedLine.endsWith(";")) {
                int semicolonIndex = currentStatement.lastIndexOf(";");
                if (semicolonIndex >= 0) {
                    currentStatement.deleteCharAt(semicolonIndex);
                }

                String statement = currentStatement.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }

                currentStatement.setLength(0);
            }
        }

        String remaining = currentStatement.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }

        return statements;
    }
}
