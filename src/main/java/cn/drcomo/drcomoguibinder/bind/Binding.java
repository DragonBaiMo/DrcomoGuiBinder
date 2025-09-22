package cn.drcomo.drcomoguibinder.bind;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 描述玩家在某个 Main 槽位上的绑定记录。
 */
public final class Binding {

  private final UUID playerUuid;
  private final String mainId;
  private final int slot;
  private final String subId;
  private final String entryKey;
  private final String entryValue;
  private final long updatedAt;

  public Binding(UUID playerUuid, String mainId, int slot, String subId, String entryKey,
      String entryValue, long updatedAt) {
    this.playerUuid = playerUuid;
    this.mainId = mainId;
    this.slot = slot;
    this.subId = subId;
    this.entryKey = entryKey;
    this.entryValue = entryValue;
    this.updatedAt = updatedAt;
  }

  public static Binding now(UUID playerUuid, String mainId, int slot, String subId, String entryKey,
      String entryValue) {
    return new Binding(playerUuid, mainId, slot, subId, entryKey, entryValue,
        Instant.now().toEpochMilli());
  }

  public UUID getPlayerUuid() {
    return playerUuid;
  }

  public String getMainId() {
    return mainId;
  }

  public int getSlot() {
    return slot;
  }

  public String getSubId() {
    return subId;
  }

  public String getEntryKey() {
    return entryKey;
  }

  public String getEntryValue() {
    return entryValue;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public Binding withEntryValue(String newValue) {
    return new Binding(playerUuid, mainId, slot, subId, entryKey, newValue, updatedAt);
  }

  public Binding withUpdatedAt(long timestamp) {
    return new Binding(playerUuid, mainId, slot, subId, entryKey, entryValue, timestamp);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Binding binding)) {
      return false;
    }
    return slot == binding.slot && updatedAt == binding.updatedAt
        && Objects.equals(playerUuid, binding.playerUuid) && Objects.equals(mainId, binding.mainId)
        && Objects.equals(subId, binding.subId) && Objects.equals(entryKey, binding.entryKey)
        && Objects.equals(entryValue, binding.entryValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerUuid, mainId, slot, subId, entryKey, entryValue, updatedAt);
  }
}
