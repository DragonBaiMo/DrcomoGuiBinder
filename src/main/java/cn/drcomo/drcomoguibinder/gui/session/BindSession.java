package cn.drcomo.drcomoguibinder.gui.session;

import java.time.Instant;

/**
 * 记录玩家当前正在操作的绑定上下文。
 */
public final class BindSession {

  private final String mainId;
  private final int slot;
  private final String subId;
  private final long createdAt;

  public BindSession(String mainId, int slot, String subId) {
    this.mainId = mainId;
    this.slot = slot;
    this.subId = subId;
    this.createdAt = Instant.now().toEpochMilli();
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

  public long getCreatedAt() {
    return createdAt;
  }
}
