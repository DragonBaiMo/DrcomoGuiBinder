package cn.drcomo.drcomoguibinder.config.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 描述 Sub 条目的定义。
 */
public final class EntryDef {

  private final int slot;
  private final String key;
  private final String value;
  private final ItemTemplate display;
  private final Map<String, ItemTemplate> overrides;
  private final ConditionConfig conditions;

  public EntryDef(int slot, String key, String value, ItemTemplate display,
      Map<String, ItemTemplate> overrides) {
    this(slot, key, value, display, overrides, ConditionConfig.empty());
  }

  public EntryDef(int slot, String key, String value, ItemTemplate display,
      Map<String, ItemTemplate> overrides, ConditionConfig conditions) {
    this.slot = slot;
    this.key = key;
    this.value = value;
    this.display = display;
    this.overrides = overrides == null ? Collections.emptyMap() : Map.copyOf(overrides);
    this.conditions = conditions == null ? ConditionConfig.empty() : conditions;
  }

  public int getSlot() {
    return slot;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public ItemTemplate getDisplay() {
    return display;
  }

  public Map<String, ItemTemplate> getOverrides() {
    return overrides;
  }

  public ConditionConfig getConditions() {
    return conditions;
  }

  public ItemTemplate resolveDisplay(String mainId) {
    ItemTemplate override = overrides.get(mainId);
    return override == null ? display : display.merge(override);
  }

  public EntryDef withOverride(String mainId, ItemTemplate template) {
    Map<String, ItemTemplate> map = new LinkedHashMap<>(overrides);
    map.put(mainId, template);
    return new EntryDef(slot, key, value, display, map, conditions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EntryDef entryDef)) {
      return false;
    }
    return slot == entryDef.slot && Objects.equals(key, entryDef.key)
        && Objects.equals(value, entryDef.value) && Objects.equals(display, entryDef.display)
        && Objects.equals(overrides, entryDef.overrides) && Objects.equals(conditions, entryDef.conditions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, key, value, display, overrides, conditions);
  }
}
