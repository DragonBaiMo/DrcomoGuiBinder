package cn.drcomo.drcomoguibinder.config.model;

import cn.drcomo.corelib.gui.ClickContext;
import org.bukkit.event.inventory.ClickType;

/**
 * 主界面清除绑定的点击类型枚举。
 * 直接使用 Bukkit 的 ClickType 方法判断，确保准确性。
 */
public enum ClearClickType {
  /**
   * 禁用主界面清除功能。
   */
  DISABLED {
    @Override
    public boolean matches(ClickContext ctx) {
      return false;
    }
  },

  /**
   * 右键点击清除（不含 Shift）。
   */
  RIGHT_CLICK {
    @Override
    public boolean matches(ClickContext ctx) {
      ClickType clickType = ctx.clickType();
      return clickType != null && clickType.isRightClick() && !clickType.isShiftClick();
    }
  },

  /**
   * Shift + 右键点击清除。
   */
  SHIFT_RIGHT_CLICK {
    @Override
    public boolean matches(ClickContext ctx) {
      ClickType clickType = ctx.clickType();
      return clickType != null && clickType.isRightClick() && clickType.isShiftClick();
    }
  },

  /**
   * Shift + 左键点击清除。
   */
  SHIFT_LEFT_CLICK {
    @Override
    public boolean matches(ClickContext ctx) {
      ClickType clickType = ctx.clickType();
      return clickType != null && clickType.isLeftClick() && clickType.isShiftClick();
    }
  },

  /**
   * 中键点击清除。
   */
  MIDDLE_CLICK {
    @Override
    public boolean matches(ClickContext ctx) {
      ClickType clickType = ctx.clickType();
      return clickType == ClickType.MIDDLE;
    }
  },

  /**
   * 丢弃键（Q键）清除。
   */
  DROP {
    @Override
    public boolean matches(ClickContext ctx) {
      ClickType clickType = ctx.clickType();
      return clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP;
    }
  };

  /**
   * 检查给定的点击上下文是否匹配此清除类型。
   *
   * @param ctx 点击上下文
   * @return 如果匹配返回 true，否则返回 false
   */
  public abstract boolean matches(ClickContext ctx);

  /**
   * 从配置字符串解析清除点击类型。
   *
   * @param value 配置值
   * @return 对应的枚举值，无法解析时返回 DISABLED
   */
  public static ClearClickType fromString(String value) {
    if (value == null || value.isEmpty()) {
      return DISABLED;
    }
    try {
      return valueOf(value.toUpperCase().trim());
    } catch (IllegalArgumentException e) {
      return DISABLED;
    }
  }
}
