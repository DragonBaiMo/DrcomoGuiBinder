package cn.drcomo.drcomoguibinder.config.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 描述主界面配置的整体定义。
 */
public final class MainGuiDef {

  private final String id;
  private final String title;
  private final int size;
  private final List<MainSlotDef> slots;

  public MainGuiDef(String id, String title, int size, List<MainSlotDef> slots) {
    this.id = id;
    this.title = title;
    this.size = size;
    this.slots = List.copyOf(slots);
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public int getSize() {
    return size;
  }

  public List<MainSlotDef> getSlots() {
    return Collections.unmodifiableList(slots);
  }

  public MainSlotDef findSlot(int slotIndex) {
    return slots.stream().filter(def -> def.getSlot() == slotIndex).findFirst().orElse(null);
  }

  public MainSlotDef findSlotById(String slotId) {
    if (slotId == null || slotId.isEmpty()) {
      return null;
    }
    return slots.stream()
        .filter(def -> def.getId() != null && slotId.equalsIgnoreCase(def.getId()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MainGuiDef that)) {
      return false;
    }
    return size == that.size && Objects.equals(id, that.id) && Objects.equals(title, that.title)
        && Objects.equals(slots, that.slots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, size, slots);
  }
}
