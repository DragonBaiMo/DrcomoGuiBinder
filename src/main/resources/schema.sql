CREATE TABLE IF NOT EXISTS gui_bindings (
  player_uuid TEXT NOT NULL,
  main_id     TEXT NOT NULL,
  slot_index  INTEGER NOT NULL,
  sub_id      TEXT NOT NULL,
  entry_key   TEXT NOT NULL,
  entry_value TEXT NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, main_id, slot_index)
);

CREATE INDEX IF NOT EXISTS idx_gui_bindings_player ON gui_bindings(player_uuid);
