package cn.drcomo.drcomoguibinder.config.model;

import java.util.Objects;

/**
 * 描述主界面中的单个槽位定义。
 */
public final class MainSlotDef {

  private final int slot;
  private final String subId;
  private final ItemTemplate displayEmpty;

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty) {
    this.slot = slot;
    this.subId = subId;
    this.displayEmpty = displayEmpty;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MainSlotDef that)) {
      return false;
    }
    return slot == that.slot && Objects.equals(subId, that.subId)
        && Objects.equals(displayEmpty, that.displayEmpty);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, subId, displayEmpty);
  }
}
