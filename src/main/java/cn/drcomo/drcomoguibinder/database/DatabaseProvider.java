package cn.drcomo.drcomoguibinder.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 数据库操作的统一抽象接口，支持 SQLite 和 MySQL。
 * 
 * 提供同步和异步的基础 CRUD 操作，屏蔽底层数据库实现细节。
 */
public interface DatabaseProvider {

    /**
     * 连接到数据库
     * @throws SQLException 连接失败时抛出异常
     */
    void connect() throws SQLException;

    /**
     * 断开数据库连接
     */
    void disconnect();

    /**
     * 初始化数据库表结构
     * @throws SQLException 初始化失败时抛出异常
     */
    void initializeSchema() throws SQLException;

    /**
     * 检查数据库连接是否有效
     * @return 连接有效返回 true，否则返回 false
     */
    boolean isConnectionValid();

    /**
     * 执行更新操作（INSERT、UPDATE、DELETE）
     * 
     * @param sql SQL 语句，支持 ? 占位符
     * @param params 占位符参数
     * @return 受影响的行数
     * @throws SQLException 执行失败时抛出异常
     */
    int executeUpdate(String sql, Object... params) throws SQLException;

    /**
     * 查询单行数据
     * 
     * @param <T> 返回结果类型
     * @param sql 查询语句，支持 ? 占位符
     * @param handler 结果集处理器
     * @param params 占位符参数
     * @return 查询结果，没有记录时返回 null
     * @throws SQLException 执行失败时抛出异常
     */
    <T> T queryOne(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException;

    /**
     * 查询多行数据
     * 
     * @param <T> 返回结果类型
     * @param sql 查询语句，支持 ? 占位符
     * @param handler 结果集处理器
     * @param params 占位符参数
     * @return 查询结果列表，永不返回 null
     * @throws SQLException 执行失败时抛出异常
     */
    <T> List<T> queryList(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException;

    /**
     * 异步执行更新操作
     * 
     * @param sql SQL 语句，支持 ? 占位符
     * @param params 占位符参数
     * @return CompletableFuture，完成时包含受影响的行数
     */
    CompletableFuture<Integer> executeUpdateAsync(String sql, Object... params);

    /**
     * 异步查询单行数据
     * 
     * @param <T> 返回结果类型
     * @param sql 查询语句，支持 ? 占位符
     * @param handler 结果集处理器
     * @param params 占位符参数
     * @return CompletableFuture，完成时包含查询结果
     */
    <T> CompletableFuture<T> queryOneAsync(String sql, ResultSetHandler<T> handler, Object... params);

    /**
     * 异步查询多行数据
     * 
     * @param <T> 返回结果类型
     * @param sql 查询语句，支持 ? 占位符
     * @param handler 结果集处理器
     * @param params 占位符参数
     * @return CompletableFuture，完成时包含查询结果列表
     */
    <T> CompletableFuture<List<T>> queryListAsync(String sql, ResultSetHandler<T> handler, Object... params);

    /**
     * ResultSet 处理器接口
     * 
     * @param <T> 处理结果类型
     */
    @FunctionalInterface
    interface ResultSetHandler<T> {
        /**
         * 处理 ResultSet 中的一行数据
         * 
         * @param rs 结果集
         * @return 处理后的对象
         * @throws SQLException 处理失败时抛出异常
         */
        T handle(ResultSet rs) throws SQLException;
    }

    /**
     * 获取数据库类型名称
     * @return 数据库类型（如 "SQLite", "MySQL"）
     */
    String getDatabaseType();
}
