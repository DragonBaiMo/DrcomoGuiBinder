package cn.drcomo.drcomoguibinder.config.model;

import java.util.Objects;

/**
 * 针对特定 Main 的子条目展示覆盖。
 */
public final class EntryOverride {

  private final String mainId;
  private final ItemTemplate display;

  public EntryOverride(String mainId, ItemTemplate display) {
    this.mainId = mainId;
    this.display = display;
  }

  public String getMainId() {
    return mainId;
  }

  public ItemTemplate getDisplay() {
    return display;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EntryOverride that)) {
      return false;
    }
    return Objects.equals(mainId, that.mainId) && Objects.equals(display, that.display);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mainId, display);
  }
}
