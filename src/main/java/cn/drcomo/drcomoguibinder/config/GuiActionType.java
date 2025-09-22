package cn.drcomo.drcomoguibinder.config;

/**
 * GUI 动作类型枚举，定义 YAML 中允许声明的动作关键字。
 */
public enum GuiActionType {
    /** 向点击玩家发送语言文件中的消息。 */
    MESSAGE,
    /** 使用文本模板直接向玩家发送消息。 */
    MESSAGE_TEXT,
    /** 以玩家身份执行指令。 */
    PLAYER_COMMAND,
    /** 以控制台身份执行指令。 */
    CONSOLE_COMMAND,
    /** 打开另一个菜单。 */
    OPEN,
    /** 关闭当前菜单。 */
    CLOSE,
    /** 播放配置中预定义的音效。 */
    SOUND
}
