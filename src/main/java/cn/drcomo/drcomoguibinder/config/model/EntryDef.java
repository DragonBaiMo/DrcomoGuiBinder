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
  private final GuiSlotType type;
  private final int priority;

  public EntryDef(int slot, String key, String value, ItemTemplate display,
      Map<String, ItemTemplate> overrides) {
    this(slot, key, value, display, overrides, ConditionConfig.empty(), GuiSlotType.DEFAULT, 0);
  }

  public EntryDef(int slot, String key, String value, ItemTemplate display,
      Map<String, ItemTemplate> overrides, ConditionConfig conditions) {
    this(slot, key, value, display, overrides, conditions, GuiSlotType.DEFAULT, 0);
  }

  public EntryDef(int slot, String key, String value, ItemTemplate display,
      Map<String, ItemTemplate> overrides, ConditionConfig conditions, GuiSlotType type) {
    this(slot, key, value, display, overrides, conditions, type, 0);
  }

  public EntryDef(int slot, String key, String value, ItemTemplate display,
      Map<String, ItemTemplate> overrides, ConditionConfig conditions, GuiSlotType type,
      int priority) {
    this.slot = slot;
    this.key = key;
    this.value = value;
    this.display = display;
    this.overrides = overrides == null ? Collections.emptyMap() : Map.copyOf(overrides);
    this.conditions = conditions == null ? ConditionConfig.empty() : conditions;
    this.type = type == null ? GuiSlotType.DEFAULT : type;
    this.priority = priority;
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

  public GuiSlotType getType() {
    return type;
  }

  /**
   * 获取条目的优先级。
   * 优先级越高，在排序时越靠前显示。
   *
   * @return 优先级值，默认为 0
   */
  public int getPriority() {
    return priority;
  }

  /**
   * 根据 mainId 解析最终的显示模板。
   * 若存在 override，则完全替换默认 display；若不存在，则使用默认 display。
   *
   * @param mainId Main GUI 的 ID
   * @return 最终的显示模板
   */
  public ItemTemplate resolveDisplay(String mainId) {
    ItemTemplate override = overrides.get(mainId);
    // 完全替换逻辑：override 中未定义的字段（如 lore）会保持为空，而不是保留原 display 的值
    return override == null ? display : override;
  }

  public EntryDef withOverride(String mainId, ItemTemplate template) {
    Map<String, ItemTemplate> map = new LinkedHashMap<>(overrides);
    map.put(mainId, template);
    return new EntryDef(slot, key, value, display, map, conditions, type, priority);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EntryDef entryDef)) {
      return false;
    }
    return slot == entryDef.slot && priority == entryDef.priority
        && Objects.equals(key, entryDef.key)
        && Objects.equals(value, entryDef.value) && Objects.equals(display, entryDef.display)
        && Objects.equals(overrides, entryDef.overrides)
        && Objects.equals(conditions, entryDef.conditions) && type == entryDef.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, key, value, display, overrides, conditions, type, priority);
  }
}
