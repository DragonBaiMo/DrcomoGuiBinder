package cn.drcomo.drcomoguibinder.bind;

import java.util.Objects;
import java.util.UUID;

/**
 * (player, mainId, slot) 复合键。
 */
final class BindingKey {

  private final UUID playerUuid;
  private final String mainId;
  private final int slot;

  BindingKey(UUID playerUuid, String mainId, int slot) {
    this.playerUuid = playerUuid;
    this.mainId = mainId;
    this.slot = slot;
  }

  UUID getPlayerUuid() {
    return playerUuid;
  }

  String getMainId() {
    return mainId;
  }

  int getSlot() {
    return slot;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BindingKey bindingKey)) {
      return false;
    }
    return slot == bindingKey.slot && Objects.equals(playerUuid, bindingKey.playerUuid)
        && Objects.equals(mainId, bindingKey.mainId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerUuid, mainId, slot);
  }
}
