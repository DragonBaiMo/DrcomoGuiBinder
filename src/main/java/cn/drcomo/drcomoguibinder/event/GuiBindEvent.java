package cn.drcomo.drcomoguibinder.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 在玩家选择 Sub 条目并即将写入绑定时触发，可取消或修改结果。
 */
public final class GuiBindEvent extends Event implements Cancellable {

  private static final HandlerList HANDLERS = new HandlerList();

  private final Player player;
  private String mainId;
  private int slot;
  private String subId;
  private String entryKey;
  private String entryValue;
  private boolean cancelled;

  public GuiBindEvent(Player player, String mainId, int slot, String subId, String entryKey,
      String entryValue) {
    this.player = player;
    this.mainId = mainId;
    this.slot = slot;
    this.subId = subId;
    this.entryKey = entryKey;
    this.entryValue = entryValue;
  }

  public Player getPlayer() {
    return player;
  }

  public String getMainId() {
    return mainId;
  }

  public void setMainId(String mainId) {
    this.mainId = mainId;
  }

  public int getSlot() {
    return slot;
  }

  public void setSlot(int slot) {
    this.slot = slot;
  }

  public String getSubId() {
    return subId;
  }

  public void setSubId(String subId) {
    this.subId = subId;
  }

  public String getEntryKey() {
    return entryKey;
  }

  public void setEntryKey(String entryKey) {
    this.entryKey = entryKey;
  }

  public String getEntryValue() {
    return entryValue;
  }

  public void setEntryValue(String entryValue) {
    this.entryValue = entryValue;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    this.cancelled = cancel;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
