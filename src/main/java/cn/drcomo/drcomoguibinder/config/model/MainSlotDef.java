package cn.drcomo.drcomoguibinder.config.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 描述主界面中的单个槽位定义。
 */
public final class MainSlotDef {

  private final int slot;
  private final String subId;
  private final ItemTemplate displayEmpty;
  private final String id;
  private final GuiSlotType type;
  private final ConditionConfig conditions;
  private final List<String> actions;
  private final List<MainSlotEntryDef> entries;

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id) {
    this(slot, subId, displayEmpty, id, GuiSlotType.DEFAULT, ConditionConfig.empty(),
        Collections.emptyList(), Collections.emptyList());
  }

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id, GuiSlotType type) {
    this(slot, subId, displayEmpty, id, type, ConditionConfig.empty(),
        Collections.emptyList(), Collections.emptyList());
  }

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id, GuiSlotType type,
      ConditionConfig conditions) {
    this(slot, subId, displayEmpty, id, type, conditions,
        Collections.emptyList(), Collections.emptyList());
  }

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id, GuiSlotType type,
      ConditionConfig conditions, List<String> actions) {
    this(slot, subId, displayEmpty, id, type, conditions, actions, Collections.emptyList());
  }

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id, GuiSlotType type,
      ConditionConfig conditions, List<String> actions, List<MainSlotEntryDef> entries) {
    this.slot = slot;
    this.subId = subId;
    this.displayEmpty = displayEmpty;
    this.id = id;
    this.type = type == null ? GuiSlotType.DEFAULT : type;
    this.conditions = conditions == null ? ConditionConfig.empty() : conditions;
    this.actions = actions == null ? Collections.emptyList() : List.copyOf(actions);
    this.entries = entries == null ? Collections.emptyList() : List.copyOf(entries);
  }

  public int getSlot() {
    return slot;
  }

  public String getSubId() {
    return subId;
  }

  public ItemTemplate getDisplayEmpty() {
    return displayEmpty;
  }

  public String getId() {
    return id;
  }

  public GuiSlotType getType() {
    return type;
  }

  /**
   * 获取槽位的条件配置。
   *
   * @return 条件配置
   */
  public ConditionConfig getConditions() {
    return conditions;
  }

  /**
   * 获取 ACTION 类型槽位的命令列表。
   * 每条命令可带 [op]、[console]、[player] 前缀指定执行方式。
   *
   * @return 命令列表（不可变）
   */
  public List<String> getActions() {
    return actions;
  }

  /**
   * 获取槽位的候选条目列表。
   * 用于多条目动态选择场景，支持按条件和优先级选择显示。
   *
   * @return 条目列表（不可变），按优先级降序排列
   */
  public List<MainSlotEntryDef> getEntries() {
    return entries;
  }

  /**
   * 检查槽位是否配置了候选条目。
   *
   * @return 如果配置了 entries 返回 true
   */
  public boolean hasEntries() {
    return !entries.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MainSlotDef)) {
      return false;
    }
    MainSlotDef that = (MainSlotDef) o;
    return slot == that.slot
        && Objects.equals(subId, that.subId)
        && Objects.equals(displayEmpty, that.displayEmpty)
        && Objects.equals(id, that.id)
        && type == that.type
        && Objects.equals(conditions, that.conditions)
        && Objects.equals(actions, that.actions)
        && Objects.equals(entries, that.entries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, subId, displayEmpty, id, type, conditions, actions, entries);
  }
}
