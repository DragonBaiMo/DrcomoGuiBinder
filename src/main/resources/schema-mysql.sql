-- MySQL 数据库初始化脚本
CREATE TABLE IF NOT EXISTS gui_bindings (
  player_uuid VARCHAR(36) NOT NULL COMMENT '玩家UUID',
  main_id     VARCHAR(255) NOT NULL COMMENT '主GUI标识符',
  slot_index  INT NOT NULL COMMENT '槽位索引',
  sub_id      VARCHAR(255) NOT NULL COMMENT '子GUI标识符',
  entry_key   VARCHAR(255) NOT NULL COMMENT '条目键名',
  entry_value TEXT NOT NULL COMMENT '条目值',
  updated_at  BIGINT NOT NULL COMMENT '更新时间戳',
  PRIMARY KEY (player_uuid, main_id, slot_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='GUI绑定关系表';

-- 为玩家查询创建索引
CREATE INDEX idx_gui_bindings_player ON gui_bindings(player_uuid);

-- 为主GUI查询创建复合索引
CREATE INDEX idx_gui_bindings_player_main ON gui_bindings(player_uuid, main_id);
