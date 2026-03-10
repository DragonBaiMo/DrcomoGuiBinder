package cn.drcomo.drcomoguibinder.config.model;

import java.util.Objects;

/**
 * 描述 Main GUI 槽位的单个候选条目定义。
 * 用于多条目动态选择场景，支持按条件和优先级选择显示哪个条目。
 */
public final class MainSlotEntryDef {

  private final int priority;
  private final ConditionConfig conditions;
  private final ItemTemplate displayEmpty;
  private final ItemTemplate displayBound;

  public MainSlotEntryDef(int priority, ConditionConfig conditions,
      ItemTemplate displayEmpty, ItemTemplate displayBound) {
    this.priority = priority;
    this.conditions = conditions == null ? ConditionConfig.empty() : conditions;
    this.displayEmpty = displayEmpty;
    this.displayBound = displayBound;
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
   * 获取条目的条件配置。
   *
   * @return 条件配置
   */
  public ConditionConfig getConditions() {
    return conditions;
  }

  /**
   * 获取未绑定时的显示模板。
   *
   * @return 未绑定时的显示模板，可能为 null
   */
  public ItemTemplate getDisplayEmpty() {
    return displayEmpty;
  }

  /**
   * 获取已绑定时的显示模板。
   *
   * @return 已绑定时的显示模板，可能为 null
   */
  public ItemTemplate getDisplayBound() {
    return displayBound;
  }

  /**
   * 检查是否配置了已绑定时的显示模板。
   *
   * @return 如果配置了 displayBound 返回 true
   */
  public boolean hasDisplayBound() {
    return displayBound != null;
  }

  /**
   * 检查是否配置了未绑定时的显示模板。
   *
   * @return 如果配置了 displayEmpty 返回 true
   */
  public boolean hasDisplayEmpty() {
    return displayEmpty != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MainSlotEntryDef that)) {
      return false;
    }
    return priority == that.priority
        && Objects.equals(conditions, that.conditions)
        && Objects.equals(displayEmpty, that.displayEmpty)
        && Objects.equals(displayBound, that.displayBound);
  }

  @Override
  public int hashCode() {
    return Objects.hash(priority, conditions, displayEmpty, displayBound);
  }
}
