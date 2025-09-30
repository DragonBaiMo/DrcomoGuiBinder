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
  private final GuiSlotType type;

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id) {
    this(slot, subId, displayEmpty, id, GuiSlotType.DEFAULT);
  }

  public MainSlotDef(int slot, String subId, ItemTemplate displayEmpty, String id, GuiSlotType type) {
    this.slot = slot;
    this.subId = subId;
    this.displayEmpty = displayEmpty;
    this.id = id;
    this.type = type == null ? GuiSlotType.DEFAULT : type;
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
        && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, subId, displayEmpty, id, type);
  }
}
