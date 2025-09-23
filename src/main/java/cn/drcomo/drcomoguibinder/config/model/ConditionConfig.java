package cn.drcomo.drcomoguibinder.config.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 描述条目的条件配置。
 * 包含显示条件（display）和选择条件（choose）。
 */
public final class ConditionConfig {

  private final List<String> display;
  private final List<String> choose;

  public ConditionConfig(List<String> display, List<String> choose) {
    this.display = display == null ? Collections.emptyList() : List.copyOf(display);
    this.choose = choose == null ? Collections.emptyList() : List.copyOf(choose);
  }

  /**
   * 获取显示条件列表。
   * 当所有条件都满足时，条目才会在界面中显示。
   *
   * @return 显示条件列表，空列表表示无条件限制
   */
  public List<String> getDisplay() {
    return display;
  }

  /**
   * 获取选择条件列表。
   * 当所有条件都满足时，玩家才能选择该条目。
   *
   * @return 选择条件列表，空列表表示无条件限制
   */
  public List<String> getChoose() {
    return choose;
  }

  /**
   * 检查是否有显示条件。
   *
   * @return 如果有显示条件返回 true
   */
  public boolean hasDisplayConditions() {
    return !display.isEmpty();
  }

  /**
   * 检查是否有选择条件。
   *
   * @return 如果有选择条件返回 true
   */
  public boolean hasChooseConditions() {
    return !choose.isEmpty();
  }

  /**
   * 检查是否有任何条件。
   *
   * @return 如果有任何条件返回 true
   */
  public boolean hasAnyConditions() {
    return hasDisplayConditions() || hasChooseConditions();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConditionConfig that)) {
      return false;
    }
    return Objects.equals(display, that.display) && Objects.equals(choose, that.choose);
  }

  @Override
  public int hashCode() {
    return Objects.hash(display, choose);
  }

  @Override
  public String toString() {
    return "ConditionConfig{display=" + display + ", choose=" + choose + "}";
  }

  /**
   * 创建一个空的条件配置（无任何条件限制）。
   *
   * @return 空的条件配置
   */
  public static ConditionConfig empty() {
    return new ConditionConfig(null, null);
  }
}
