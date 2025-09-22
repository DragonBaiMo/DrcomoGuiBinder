package cn.drcomo.drcomoguibinder.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 当绑定被清除时触发，通知其他模块进行同步处理。
 */
public final class GuiClearEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  private final UUID targetPlayer;
  private final Player actor;
  private final String mainId;
  private final int slot;

  public GuiClearEvent(Player actor, UUID targetPlayer, String mainId, int slot) {
    this.actor = actor;
    this.targetPlayer = targetPlayer;
    this.mainId = mainId;
    this.slot = slot;
  }

  public UUID getTargetPlayer() {
    return targetPlayer;
  }

  public Player getActor() {
    return actor;
  }

  public String getMainId() {
    return mainId;
  }

  public int getSlot() {
    return slot;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
