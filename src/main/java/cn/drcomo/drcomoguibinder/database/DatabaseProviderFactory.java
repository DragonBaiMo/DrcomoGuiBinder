package cn.drcomo.drcomoguibinder.database;

import cn.drcomo.corelib.async.AsyncTaskManager;
import cn.drcomo.corelib.util.DebugUtil;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * 数据库提供者工厂，支持 SQLite（基于 DrcomoCoreLib）和 MySQL（自行实现）。
 * SQLite 使用 DrcomoCoreLib 的强大功能，MySQL 使用自定义实现。
 */
public final class DatabaseProviderFactory {

    // 默认的初始化脚本文件名
    private static final String DEFAULT_SQLITE_SCHEMA = "schema.sql";
    private static final String DEFAULT_MYSQL_SCHEMA = "schema-mysql.sql";

    private DatabaseProviderFactory() {
        // 工具类，禁止实例化
    }

    /**
     * 根据配置创建数据库提供者
     *
     * @param plugin 插件实例
     * @param logger 日志工具
     * @param asyncManager 异步任务管理器（MySQL 需要）
     * @param databaseConfig 数据库配置节
     * @param schemaScripts 初始化脚本列表（可为 null，将使用默认值）
     * @return 数据库提供者实例
     * @throws IllegalArgumentException 配置无效时抛出异常
     * @throws NullPointerException 必需参数为 null 时抛出异常
     */
    public static DatabaseProvider createProvider(Plugin plugin, DebugUtil logger,
                                                AsyncTaskManager asyncManager,
                                                ConfigurationSection databaseConfig,
                                                List<String> schemaScripts) {
        // 参数验证
        Objects.requireNonNull(plugin, "插件实例不能为 null");
        Objects.requireNonNull(logger, "日志工具不能为 null");
        Objects.requireNonNull(databaseConfig, "数据库配置不能为 null");

        String type = databaseConfig.getString("type", "sqlite").toLowerCase().trim();
        logger.info("创建数据库提供者，类型: " + type);

        try {
            switch (type) {
                case "sqlite":
                    return createSQLiteProvider(plugin, logger, databaseConfig, schemaScripts);

                case "mysql":
                    if (asyncManager == null) {
                        throw new IllegalArgumentException("MySQL 数据库需要提供 AsyncTaskManager");
                    }
                    return createMySQLProvider(plugin, logger, asyncManager, databaseConfig, schemaScripts);

                default:
                    throw new IllegalArgumentException("不支持的数据库类型: '" + type + "'，仅支持 'sqlite' 和 'mysql'");
            }
        } catch (Exception ex) {
            logger.error("创建数据库提供者失败，类型: " + type, ex);
            throw new IllegalStateException("无法创建数据库提供者", ex);
        }
    }

    /**
     * 创建 SQLite 提供者
     */
    private static DatabaseProvider createSQLiteProvider(Plugin plugin, DebugUtil logger,
                                                       ConfigurationSection config,
                                                       List<String> schemaScripts) {
        String file = config.getString("file", "data.db");
        if (file == null || file.trim().isEmpty()) {
            file = "data.db";
            logger.warn("SQLite 数据库文件名为空，使用默认值: " + file);
        }

        // 使用提供的脚本或默认脚本
        List<String> scripts = (schemaScripts != null && !schemaScripts.isEmpty()) ?
            schemaScripts : List.of(DEFAULT_SQLITE_SCHEMA);

        logger.debug("创建 SQLite 提供者，文件: " + file + ", 初始化脚本: " + scripts);

        return new SQLiteProvider(plugin, logger, file, scripts);
    }


    /**
     * 创建默认的 SQLite 提供者，使用默认配置
     *
     * @param plugin 插件实例
     * @param logger 日志工具
     * @return SQLite 数据库提供者
     */
    public static DatabaseProvider createDefaultSQLiteProvider(Plugin plugin, DebugUtil logger) {
        logger.info("创建默认 SQLite 数据库提供者");
        return new SQLiteProvider(plugin, logger, "data.db", List.of(DEFAULT_SQLITE_SCHEMA));
    }

    /**
     * 创建 MySQL 提供者
     */
    private static DatabaseProvider createMySQLProvider(Plugin plugin, DebugUtil logger,
                                                      AsyncTaskManager asyncManager,
                                                      ConfigurationSection config,
                                                      List<String> schemaScripts) {
        // 获取配置参数
        String host = config.getString("host", "localhost").trim();
        int port = config.getInt("port", 3306);
        String database = config.getString("database");
        String username = config.getString("username");
        String password = config.getString("password");

        // 验证必需配置
        validateMySQLConfig(host, port, database, username, password);

        // 使用提供的脚本或默认脚本
        List<String> scripts = (schemaScripts != null && !schemaScripts.isEmpty()) ?
            schemaScripts : List.of(DEFAULT_MYSQL_SCHEMA);

        logger.debug("创建 MySQL 提供者，连接: " + host + ":" + port + "/" + database +
                    ", 用户: " + username + ", 初始化脚本: " + scripts);

        return new MySQLProvider(plugin, logger, asyncManager, host, port, database,
                               username, password, scripts);
    }

    /**
     * 验证 MySQL 配置参数
     */
    private static void validateMySQLConfig(String host, int port, String database,
                                          String username, String password) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("MySQL 配置错误: host 不能为空");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("MySQL 配置错误: port 必须在 1-65535 范围内，当前值: " + port);
        }
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL 配置缺少必需项: database");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL 配置缺少必需项: username");
        }
        if (password == null) {
            throw new IllegalArgumentException("MySQL 配置缺少必需项: password（可以为空字符串）");
        }
    }

    /**
     * 获取支持的数据库类型列表
     *
     * @return 支持的数据库类型
     */
    public static List<String> getSupportedDatabaseTypes() {
        return List.of("sqlite", "mysql");
    }

    /**
     * 检查数据库类型是否受支持
     *
     * @param type 数据库类型
     * @return 是否支持
     */
    public static boolean isSupportedDatabaseType(String type) {
        return type != null && getSupportedDatabaseTypes().contains(type.toLowerCase().trim());
    }
}
