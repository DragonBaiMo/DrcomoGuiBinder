package cn.drcomo.drcomoguibinder.papi;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.color.ColorUtil;
import cn.drcomo.drcomoguibinder.bind.Binding;
import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.drcomoguibinder.config.GuiConfigService;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.config.model.MainGuiDef;
import cn.drcomo.drcomoguibinder.config.model.MainSlotDef;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
import cn.drcomo.drcomoguibinder.template.ItemTemplateRenderer;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 负责向 PlaceholderAPI 注册并解析插件提供的占位符。
 * 新格式: %dgb_&lt;function&gt;_&lt;mainId&gt;_&lt;slot&gt;%
 * has 功能支持两种格式：
 *   - %dgb_has_<mainId>% 检测整个主界面是否有绑定
 *   - %dgb_has_<mainId>_<slotToken>% 检测主界面的指定槽位是否有绑定
 * subhas 功能需传入 subId 与 entryKey，例如 %dgb_subhas_子界面标识_条目键%
 * 支持的 function: value, key, display, sub, id, has, subhas
 */
public final class BinderPlaceholderExpansion {

  private final PlaceholderAPIUtil placeholderUtil;
  private final GuiConfigService configService;
  private final BindingService bindingService;
  private final ItemTemplateRenderer renderer;
  private final boolean parseValueOnRender;
  private final DebugUtil logger;

  public BinderPlaceholderExpansion(PlaceholderAPIUtil placeholderUtil,
      GuiConfigService configService, BindingService bindingService,
      ItemTemplateRenderer renderer, boolean parseValueOnRender, DebugUtil logger) {
    this.placeholderUtil = placeholderUtil;
    this.configService = configService;
    this.bindingService = bindingService;
    this.renderer = renderer;
    this.parseValueOnRender = parseValueOnRender;
    this.logger = logger;
  }

  public void registerAll() {
    // 为 value 功能注册占位符：%dgb_value_<mainId>_<slot>%
    placeholderUtil.register("value",
        (player, rawArgs) -> resolveByFunction(player, "value", rawArgs));

    // 为 key 功能注册占位符：%dgb_key_<mainId>_<slot>%
    placeholderUtil.register("key",
        (player, rawArgs) -> resolveByFunction(player, "key", rawArgs));

    // 为 display 功能注册占位符：%dgb_display_<mainId>_<slot>%
    placeholderUtil.register("display",
        (player, rawArgs) -> resolveByFunction(player, "display", rawArgs));

    // 为 sub 功能注册占位符：%dgb_sub_<mainId>_<slotId>%
    placeholderUtil.register("sub",
        (player, rawArgs) -> resolveByFunction(player, "sub", rawArgs));

    // 为 id 功能注册占位符：%dgb_id_<mainId>_<slotId>%
    placeholderUtil.register("id",
        (player, rawArgs) -> resolveByFunction(player, "id", rawArgs));

    // 为 has 功能注册占位符：%dgb_has_<mainId>%
    placeholderUtil.register("has",
        (player, rawArgs) -> resolveByFunction(player, "has", rawArgs));

    // 为 subhas 功能注册占位符：%dgb_subhas_<subId>_<entryKey>%
    placeholderUtil.register("subhas",
        (player, rawArgs) -> resolveByFunction(player, "subhas", rawArgs));

  }

  /**
   * 根据 function 类型分发占位符解析请求。
   *
   * @param player 玩家
   * @param function 功能类型 (value, key, display, sub, id, has, subhas)
   * @param rawArgs 原始参数，value/key/display/sub/id 功能需传入 "<mainId>_<slot>" 或
   *     "<mainId>_<slot>|默认值"；has 功能需传入 "<mainId>" 或 "<mainId>|默认值" 或
   *     "<mainId>_<slotToken>" 或 "<mainId>_<slotToken>|默认值"；subhas 功能需
   *     传入 "<subId>_<entryKey>" 或 "<subId>_<entryKey>|默认值"
   * @return 解析结果
   */
  private String resolveByFunction(Player player, String function, String rawArgs) {
    if ("has".equals(function)) {
      return resolveHasFunction(player, rawArgs);
    }

    if ("subhas".equals(function)) {
      SubEntryFunctionRequest request = parseSubEntryFunctionArgs(rawArgs);
      if (request == null) {
        logger.warn("无效的占位符参数格式: " + rawArgs);
        return "0";
      }
      int def = normalize01ToInt(request.defaultValue);
      int r = resolveSubHasBoundInt(player, request.subId, request.entryKey, def);
      return String.valueOf(r);
    }

    FunctionRequest request = parseFunctionArgs(rawArgs);
    if (request == null) {
      logger.warn("无效的占位符参数格式: " + rawArgs);
      return "";
    }

    return switch (function) {
      case "value" -> {
        ValueArg opts = parseValueOptions(rawArgs);
        FunctionRequest fr = parseFunctionArgs(opts.argsPart());
        if (fr == null) {
          logger.warn("无效的占位符参数格式: " + rawArgs);
          yield "";
        }
        yield resolveValue(player, fr.mainId, fr.slotToken, fr.defaultValue, opts.stripColor());
      }
      case "key" -> resolveKey(player, request.mainId, request.slotToken, request.defaultValue);
      case "display" -> resolveDisplay(player, request.mainId, request.slotToken,
          request.defaultValue);
      case "sub" -> resolveSub(player, request.mainId, request.slotToken, request.defaultValue);
      case "id" -> resolveId(player, request.mainId, request.slotToken, request.defaultValue);
      default -> {
        logger.warn("不支持的占位符功能: " + function);
        yield "";
      }
    };
  }

  /**
   * 解析占位符参数，支持格式：<mainId>_<slot> 或 <mainId>_<slot>|默认值。
   *
   * @param rawArgs 原始参数字符串
   * @return 解析后的请求对象，解析失败返回 null
   */
  private FunctionRequest parseFunctionArgs(String rawArgs) {
    if (rawArgs == null || rawArgs.isEmpty()) {
      return null;
    }

    // 按 | 分割默认值部分
    String[] defaultParts = rawArgs.split("\\|", 2);
    String mainPart = defaultParts[0];
    String defaultValue = defaultParts.length > 1 ? defaultParts[1] : "";

    // 按 _ 分割 mainId 和 slot，slot 允许包含下划线
    String[] args = mainPart.split("_", 2);
    if (args.length < 2) {
      return null;
    }

    String mainId = args[0] == null ? "" : args[0].trim();
    String slotToken = args[1] == null ? "" : args[1].trim();
    return new FunctionRequest(mainId, slotToken, defaultValue);
  }

  private String resolveValue(Player player, String mainId, String slotToken, String defaultValue, boolean stripColor) {
    if (player == null) {
      return defaultValue;
    }
    Integer slot = resolveSlotIndex(mainId, slotToken);
    if (slot == null) {
      return defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, slot);
    if (binding == null) {
      return defaultValue;
    }
    String base = binding.getEntryValue();
    logger.debug("[占位符:value] 开始解析: main=" + mainId + ", slotToken=" + slotToken
        + ", stripColor=" + stripColor + ", base='" + base + "'");
    String result = parseValueOnRender ? placeholderUtil.parse(player, base) : base;
    logger.debug("[占位符:value] 解析后结果: '" + result + "' (parseValueOnRender=" + parseValueOnRender + ")");
    if (result == null || result.isEmpty()) {
      // 回退：解析结果为空则回退到 base；若 strip 模式且 base 为空，使用去色后的原始值
      result = base;
      if (result == null || result.isEmpty()) {
        return defaultValue;
      }
    }
    if (stripColor) {
      String stripped = ColorUtil.stripColorCodes(result);
      logger.debug("[占位符:value] stripcolor 最终去色输出: '" + stripped + "'");
      // strip 模式下，直接返回去色后的纯文本（不再润色）
      return stripped == null || stripped.isEmpty() ? defaultValue : stripped;
    }
    String colored = ColorUtil.translateColors(result);
    logger.debug("[占位符:value] 默认模式最终润色输出: '" + colored + "'");
    return colored;
  }

  private String resolveKey(Player player, String mainId, String slotToken, String defaultValue) {
    if (player == null) {
      return defaultValue;
    }
    Integer slot = resolveSlotIndex(mainId, slotToken);
    if (slot == null) {
      return defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, slot);
    return binding == null ? defaultValue : binding.getEntryKey();
  }

  private String resolveDisplay(Player player, String mainId, String slotToken, String defaultValue) {
    if (player == null) {
      return defaultValue;
    }
    Integer slot = resolveSlotIndex(mainId, slotToken);
    if (slot == null) {
      return defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, slot);
    if (binding == null) {
      return defaultValue;
    }
    SubGuiDef sub = configService.getSub(binding.getSubId());
    if (sub == null) {
      return defaultValue;
    }
    EntryDef entry = sub.findEntryByKey(binding.getEntryKey());
    if (entry == null) {
      return defaultValue;
    }
    ItemTemplate template = entry.resolveDisplay(mainId);
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("key", binding.getEntryKey());
    placeholders.put("value", binding.getEntryValue());
    placeholders.put("main", mainId);
    placeholders.put("sub", sub.getId());
    ItemStack stack = renderer.render(template, player, placeholders, parseValueOnRender);
    if (stack.hasItemMeta() && stack.getItemMeta().displayName() != null) {
      return stack.getItemMeta().displayName().toString();
    }
    return binding.getEntryValue();
  }

  private String resolveSub(Player player, String mainId, String slotToken, String defaultValue) {
    if (player == null) {
      return defaultValue;
    }
    Integer slot = resolveSlotIndex(mainId, slotToken);
    if (slot == null) {
      return defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, slot);
    return binding == null ? defaultValue : binding.getSubId();
  }

  private String resolveId(Player player, String mainId, String slotToken, String defaultValue) {
    // 与 key 功能保持一致，便于按槽位别名直接获取绑定的 entry key。
    return resolveKey(player, mainId, slotToken, defaultValue);
  }

  private int resolveHasBoundInt(Player player, String mainId, int defaultValue) {
    if (mainId == null || mainId.isEmpty()) {
      return 0;
    }
    if (player == null) {
      return defaultValue;
    }
    boolean hasBound = !bindingService.listPlayerMain(player.getUniqueId(), mainId).isEmpty();
    return hasBound ? 1 : defaultValue;
  }

  private int resolveSubHasBoundInt(Player player, String subId, String entryKey,
      int defaultValue) {
    if (subId == null || subId.isEmpty() || entryKey == null || entryKey.isEmpty()) {
      return 0;
    }
    if (player == null) {
      return defaultValue;
    }
    for (Binding binding : bindingService.listPlayer(player.getUniqueId())) {
      if (subId.equalsIgnoreCase(binding.getSubId())
          && entryKey.equalsIgnoreCase(binding.getEntryKey())) {
        return 1;
      }
    }
    return defaultValue;
  }

  private MainFunctionRequest parseMainFunctionArgs(String rawArgs) {
    if (rawArgs == null || rawArgs.isEmpty()) {
      return null;
    }
    String[] defaultParts = rawArgs.split("\\|", 2);
    String mainId = defaultParts[0] == null ? "" : defaultParts[0].trim();
    if (mainId.isEmpty()) {
      return null;
    }
    String defaultValue = defaultParts.length > 1 ? defaultParts[1] : "0";
    if (defaultValue == null || defaultValue.isEmpty()) {
      defaultValue = "0";
    }
    return new MainFunctionRequest(mainId, defaultValue);
  }

  private SubEntryFunctionRequest parseSubEntryFunctionArgs(String rawArgs) {
    if (rawArgs == null || rawArgs.isEmpty()) {
      return null;
    }
    String[] defaultParts = rawArgs.split("\\|", 2);
    String mainPart = defaultParts[0];
    if (mainPart == null || mainPart.isEmpty()) {
      return null;
    }
    String[] args = mainPart.split("_", 2);
    if (args.length < 2) {
      return null;
    }
    String subId = args[0].trim();
    String entryKey = args[1].trim();
    if (subId.isEmpty() || entryKey.isEmpty()) {
      return null;
    }
    String defaultValue = defaultParts.length > 1 ? defaultParts[1] : "0";
    if (defaultValue == null || defaultValue.isEmpty()) {
      defaultValue = "0";
    }
    return new SubEntryFunctionRequest(subId, entryKey, defaultValue);
  }

  private Integer resolveSlotIndex(String mainId, String slotToken) {
    if (slotToken == null || slotToken.isEmpty()) {
      return null;
    }
    try {
      int s = Integer.parseInt(slotToken);
      logger.debug("[占位符] 槽位标识为数字: token='" + slotToken + "' -> slotIndex=" + s);
      return s;
    } catch (NumberFormatException ignored) {
      MainGuiDef main = configService.getMain(mainId);
      if (main == null) {
        logger.debug("[占位符] 主界面不存在: mainId='" + mainId + "'");
        return null;
      }
      MainSlotDef slotDef = main.findSlotById(slotToken);
      Integer idx = slotDef == null ? null : slotDef.getSlot();
      logger.debug("[占位符] 槽位别名解析: token='" + slotToken + "' -> slotIndex=" + idx);
      return idx;
    }
  }

  /**
   * 功能请求记录类，用于封装解析后的占位符参数。
   */
  private record FunctionRequest(String mainId, String slotToken, String defaultValue) {
  }

  /**
   * value 占位符的选项与参数。允许在原始参数末尾追加 ":stripcolor" 来去除颜色。
   * 例如：<mainId>_<slot>[|默认值]:stripcolor
   */
  private record ValueArg(String argsPart, boolean stripColor) {}

  /**
   * 解析 value 的原始参数，分离出参数主体与可选的 ":stripcolor" 标志。
   */
  private ValueArg parseValueOptions(String rawArgs) {
    if (rawArgs == null || rawArgs.isEmpty()) {
      logger.debug("[占位符:value] parseValueOptions 空参数，strip=false");
      return new ValueArg("", false);
    }
    String args = rawArgs.trim();
    boolean strip = false;
    int idx = rawArgs.lastIndexOf(':');
    if (idx >= 0) {
      String suffix = rawArgs.substring(idx + 1).trim().toLowerCase();
      if ("stripcolor".equals(suffix)) {
        strip = true;
        args = rawArgs.substring(0, idx);
      }
    }
    logger.debug("[占位符:value] parseValueOptions 解析结果: argsPart='" + args + "', strip=" + strip);
    return new ValueArg(args, strip);
  }

  /**
   * 主界面占位符请求记录类，用于封装仅依赖主 GUI 的占位符参数。
   */
  private record MainFunctionRequest(String mainId, String defaultValue) {
  }

  /**
   * 子界面占位符请求记录类，用于封装依赖子界面条目的占位符参数。
   */
  private record SubEntryFunctionRequest(String subId, String entryKey, String defaultValue) {
  }

  /**
   * 处理 has 功能，支持两种格式：
   * 1. <mainId> 或 <mainId>|默认值 - 检测整个主界面是否有绑定
   * 2. <mainId>_<slotToken> 或 <mainId>_<slotToken>|默认值 - 检测主界面的指定槽位是否有绑定
   *
   * @param player 玩家
   * @param rawArgs 原始参数
   * @return 解析结果
   */
  private String resolveHasFunction(Player player, String rawArgs) {
    if (rawArgs == null || rawArgs.isEmpty()) {
      logger.warn("无效的占位符参数格式: " + rawArgs);
      return "0";
    }

    // 按 | 分割默认值部分
    String[] defaultParts = rawArgs.split("\\|", 2);
    String mainPart = defaultParts[0];
    String defaultValue = defaultParts.length > 1 ? defaultParts[1] : "0";
    if (defaultValue == null || defaultValue.isEmpty()) {
      defaultValue = "0";
    }
    int def = normalize01ToInt(defaultValue);

    // 检查是否包含下划线，判断是否为槽位格式
    if (mainPart.contains("_")) {
      // 格式: <mainId>_<slotToken> - 检测特定槽位
      String[] args = mainPart.split("_", 2);
      if (args.length < 2) {
        logger.warn("无效的占位符参数格式: " + rawArgs);
        return "0";
      }
      String mainId = args[0];
      String slotToken = args[1];
      int r = resolveHasSlotBoundInt(player, mainId, slotToken, def);
      return String.valueOf(r);
    } else {
      // 格式: <mainId> - 检测整个主界面
      String mainId = mainPart.trim();
      if (mainId.isEmpty()) {
        logger.warn("无效的占位符参数格式: " + rawArgs);
        return "0";
      }
      int r = resolveHasBoundInt(player, mainId, def);
      return String.valueOf(r);
    }
  }

  /**
   * 检测指定主界面的指定槽位是否有绑定。
   *
   * @param player 玩家
   * @param mainId 主界面 ID
   * @param slotToken 槽位标识符（数字或别名）
   * @param defaultValue 默认值（0 或 1）
   * @return 如果有绑定返回 1，否则返回 defaultValue
   */
  private int resolveHasSlotBoundInt(Player player, String mainId, String slotToken, int defaultValue) {
    if (mainId == null || mainId.isEmpty() || slotToken == null || slotToken.isEmpty()) {
      return 0;
    }
    if (player == null) {
      return defaultValue;
    }
    Integer slot = resolveSlotIndex(mainId, slotToken);
    if (slot == null) {
      return defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, slot);
    return binding != null ? 1 : defaultValue;
  }

  /**
   * 将默认值归一化为整型 0/1。仅当传入字符串严格等于 "1" 时返回 1，其余情况均返回 0。
   */
  private int normalize01ToInt(String value) {
    return "1".equals(value) ? 1 : 0;
  }
}
