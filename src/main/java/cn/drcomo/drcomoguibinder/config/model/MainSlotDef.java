package cn.drcomo.drcomoguibinder.config.model;

import java.util.Objects;

/**
 * 描述主界面中的单个槽位定义。
 */
public final class MainSlotDef {

  private final int slot;
  private final String subId;
  private final ItemTemplate displayEmpty;
  private final String id;

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id) {
    this.slot = slot;
    this.subId = subId;
    this.displayEmpty = displayEmpty;
    this.id = id;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MainSlotDef that)) {
      return false;
    }
    return slot == that.slot
        && Objects.equals(subId, that.subId)
        && Objects.equals(displayEmpty, that.displayEmpty)
        && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, subId, displayEmpty, id);
  }
}
