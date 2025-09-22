package cn.drcomo.drcomoguibinder.papi;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.bind.Binding;
import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.drcomoguibinder.config.GuiConfigService;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
import cn.drcomo.drcomoguibinder.template.ItemTemplateRenderer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 负责向 PlaceholderAPI 注册占位符。
 */
public final class BinderPlaceholderExpansion {

  private final PlaceholderAPIUtil valueExpansion;
  private final PlaceholderAPIUtil keyExpansion;
  private final PlaceholderAPIUtil displayExpansion;
  private final GuiConfigService configService;
  private final BindingService bindingService;
  private final ItemTemplateRenderer renderer;
  private final boolean parseValueOnRender;
  private final DebugUtil logger;

  public BinderPlaceholderExpansion(PlaceholderAPIUtil valueExpansion,
      PlaceholderAPIUtil keyExpansion, PlaceholderAPIUtil displayExpansion,
      GuiConfigService configService, BindingService bindingService,
      ItemTemplateRenderer renderer, boolean parseValueOnRender, DebugUtil logger) {
    this.valueExpansion = valueExpansion;
    this.keyExpansion = keyExpansion;
    this.displayExpansion = displayExpansion;
    this.configService = configService;
    this.bindingService = bindingService;
    this.renderer = renderer;
    this.parseValueOnRender = parseValueOnRender;
    this.logger = logger;
  }

  public void registerAll() {
    Set<String> mainIds = configService.getMainIds();
    for (String mainId : mainIds) {
      registerForMain(mainId);
    }
  }

  public void registerForMain(String mainId) {
    valueExpansion.register(mainId, (player, rawArgs) -> resolveValue(player, mainId, rawArgs));
    keyExpansion.register(mainId, (player, rawArgs) -> resolveKey(player, mainId, rawArgs));
    displayExpansion.register(mainId,
        (player, rawArgs) -> resolveDisplay(player, mainId, rawArgs));
  }

  private String resolveValue(Player player, String mainId, String rawArgs) {
    SlotRequest request = parseSlot(rawArgs);
    if (player == null) {
      return request.defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, request.slot);
    if (binding == null) {
      return request.defaultValue;
    }
    if (parseValueOnRender) {
      return valueExpansion.parse(player, binding.getEntryValue());
    }
    return binding.getEntryValue();
  }

  private String resolveKey(Player player, String mainId, String rawArgs) {
    SlotRequest request = parseSlot(rawArgs);
    if (player == null) {
      return request.defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, request.slot);
    return binding == null ? request.defaultValue : binding.getEntryKey();
  }

  private String resolveDisplay(Player player, String mainId, String rawArgs) {
    SlotRequest request = parseSlot(rawArgs);
    if (player == null) {
      return request.defaultValue;
    }
    Binding binding = bindingService.get(player.getUniqueId(), mainId, request.slot);
    if (binding == null) {
      return request.defaultValue;
    }
    SubGuiDef sub = configService.getSub(binding.getSubId());
    if (sub == null) {
      return request.defaultValue;
    }
    EntryDef entry = sub.findEntryByKey(binding.getEntryKey());
    if (entry == null) {
      return request.defaultValue;
    }
    ItemTemplate template = entry.resolveDisplay(mainId);
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("key", binding.getEntryKey());
    placeholders.put("value", binding.getEntryValue());
    placeholders.put("main", mainId);
    placeholders.put("sub", sub.getId());
    ItemStack stack = renderer.render(template, player, placeholders, parseValueOnRender);
    if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
      return stack.getItemMeta().getDisplayName();
    }
    return binding.getEntryValue();
  }

  private SlotRequest parseSlot(String rawArgs) {
    if (rawArgs == null || rawArgs.isEmpty()) {
      return new SlotRequest(0, "");
    }
    String[] parts = rawArgs.split("\\|", 2);
    try {
      int slot = Integer.parseInt(parts[0]);
      String def = parts.length > 1 ? parts[1] : "";
      return new SlotRequest(slot, def);
    } catch (NumberFormatException ex) {
      logger.warn("无效的槽位参数: " + rawArgs);
      String def = parts.length > 1 ? parts[1] : "";
      return new SlotRequest(0, def);
    }
  }

  private record SlotRequest(int slot, String defaultValue) {
  }
}
