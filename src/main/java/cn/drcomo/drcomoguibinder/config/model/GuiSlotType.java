package cn.drcomo.drcomoguibinder.config.model;

/**
 * 标记 GUI 槽位或条目的交互类型。
 */
public enum GuiSlotType {

  /**
   * 默认交互，沿用原有绑定逻辑。
   */
  DEFAULT,

  /**
   * 装饰槽，仅用于展示，不响应点击。
   */
  DECORATION,

  /**
   * 返回槽，点击后返回上一级界面。
   */
  RETURN,

  /**
   * 清除绑定槽，点击后清除当前 Main 槽绑定并关闭子界面。
   */
  CLEAR;
}
