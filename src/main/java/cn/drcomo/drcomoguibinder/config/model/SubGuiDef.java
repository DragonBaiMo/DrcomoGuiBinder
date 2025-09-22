package cn.drcomo.drcomoguibinder.config.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 描述子界面配置。
 */
public final class SubGuiDef {

  private final String id;
  private final String title;
  private final int size;
  private final List<EntryDef> entries;

  public SubGuiDef(String id, String title, int size, List<EntryDef> entries) {
    this.id = id;
    this.title = title;
    this.size = size;
    this.entries = List.copyOf(entries);
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

  public List<EntryDef> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  public EntryDef findEntryBySlot(int slotIndex) {
    return entries.stream().filter(entry -> entry.getSlot() == slotIndex).findFirst().orElse(null);
  }

  public EntryDef findEntryByKey(String key) {
    return entries.stream().filter(entry -> entry.getKey().equalsIgnoreCase(key)).findFirst()
        .orElse(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubGuiDef subGuiDef)) {
      return false;
    }
    return size == subGuiDef.size && Objects.equals(id, subGuiDef.id)
        && Objects.equals(title, subGuiDef.title) && Objects.equals(entries, subGuiDef.entries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, size, entries);
  }
}
