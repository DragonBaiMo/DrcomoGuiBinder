package cn.drcomo.drcomoguibinder.papi;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.util.DebugUtil;
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
 * 支持的 function: value, key, display, sub, id
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
  }

  /**
   * 根据 function 类型分发占位符解析请求。
   *
   * @param player 玩家
   * @param function 功能类型 (value, key, display, sub, id)
   * @param rawArgs 原始参数，格式为 "<mainId>_<slot>" 或 "<mainId>_<slot>|默认值"
   * @return 解析结果
   */
  private String resolveByFunction(Player player, String function, String rawArgs) {
    FunctionRequest request = parseFunctionArgs(rawArgs);
    if (request == null) {
      logger.warn("无效的占位符参数格式: " + rawArgs);
      return "";
    }

    return switch (function) {
      case "value" -> resolveValue(player, request.mainId, request.slotToken, request.defaultValue);
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

    String mainId = args[0];
    String slotToken = args[1];
    return new FunctionRequest(mainId, slotToken, defaultValue);
  }

  private String resolveValue(Player player, String mainId, String slotToken, String defaultValue) {
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
    if (parseValueOnRender) {
      return placeholderUtil.parse(player, binding.getEntryValue());
    }
    return binding.getEntryValue();
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

  private Integer resolveSlotIndex(String mainId, String slotToken) {
    if (slotToken == null || slotToken.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(slotToken);
    } catch (NumberFormatException ignored) {
      MainGuiDef main = configService.getMain(mainId);
      if (main == null) {
        return null;
      }
      MainSlotDef slotDef = main.findSlotById(slotToken);
      return slotDef == null ? null : slotDef.getSlot();
    }
  }

  /**
   * 功能请求记录类，用于封装解析后的占位符参数。
   */
  private record FunctionRequest(String mainId, String slotToken, String defaultValue) {
  }
}
