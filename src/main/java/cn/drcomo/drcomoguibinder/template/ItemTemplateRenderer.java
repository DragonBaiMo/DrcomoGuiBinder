package cn.drcomo.drcomoguibinder.template;

import cn.drcomo.corelib.color.ColorUtil;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.nbt.NBTUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.util.SkullUtil;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.util.BinderNbtKeyHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 根据 ItemTemplate 渲染实际的 ItemStack。
 */
public final class ItemTemplateRenderer {

  private final PlaceholderAPIUtil placeholderUtil;
  private final DebugUtil logger;
  private final NBTUtil nbtUtil;
  private final SkullUtil skullUtil;

  public ItemTemplateRenderer(DebugUtil logger, PlaceholderAPIUtil placeholderUtil,
      String pluginId) {
    this.placeholderUtil = placeholderUtil;
    this.logger = logger;
    this.nbtUtil = new NBTUtil(new BinderNbtKeyHandler(pluginId), logger);
    this.skullUtil = new SkullUtil(logger);
  }

  public ItemStack render(ItemTemplate template, Player player, Map<String, String> replacements,
      boolean parsePlaceholders) {
    if (template == null) {
      return new ItemStack(Material.BARRIER);
    }
    Map<String, String> placeholderValues = replacements == null ? new HashMap<>()
        : new HashMap<>(replacements);
    ItemStack stack = createBaseStack(template);
    if (stack == null) {
      stack = new ItemStack(Material.STONE);
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      if (template.getName() != null) {
        String name = resolvePlaceholders(template.getName(), player, placeholderValues,
            parsePlaceholders);
        meta.setDisplayName(ColorUtil.translateColors(name));
      }
      if (!template.getLore().isEmpty()) {
        List<String> lore = new ArrayList<>();
        for (String line : template.getLore()) {
          String parsed = resolvePlaceholders(line, player, placeholderValues, parsePlaceholders);
          lore.add(ColorUtil.translateColors(parsed));
        }
        meta.setLore(lore);
      }
      if (template.getCustomModelData() != null) {
        meta.setCustomModelData(template.getCustomModelData());
      }
      if (!template.getFlags().isEmpty()) {
        for (String flagName : template.getFlags()) {
          try {
            ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT));
            meta.addItemFlags(flag);
          } catch (IllegalArgumentException ex) {
            logger.warn("未知的 ItemFlag: " + flagName);
          }
        }
      }
      if (template.isGlow()) {
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      }
      stack.setItemMeta(meta);
    }
    if (!template.getNbt().isEmpty()) {
      for (Map.Entry<String, Object> entry : template.getNbt().entrySet()) {
        try {
          stack = nbtUtil.setRaw(stack, entry.getKey(), entry.getValue());
        } catch (Exception ex) {
          logger.error("写入 NBT 失败: " + entry.getKey(), ex);
        }
      }
    }
    return stack;
  }

  private ItemStack createBaseStack(ItemTemplate template) {
    Material material = Material.matchMaterial(Objects.requireNonNullElse(template.getMaterial(),
        "STONE"));
    if (material == null) {
      logger.warn("无法识别的材质: " + template.getMaterial());
      material = Material.STONE;
    }
    ItemStack stack;
    if (material == Material.PLAYER_HEAD && template.getSkullTexture() != null) {
      stack = template.getSkullTexture().startsWith("http")
          ? skullUtil.fromUrl(template.getSkullTexture())
          : skullUtil.fromBase64(template.getSkullTexture());
      stack.setAmount(Math.max(1, template.getAmount()));
      return stack;
    }
    stack = new ItemStack(material, Math.max(1, template.getAmount()));
    return stack;
  }

  private String resolvePlaceholders(String text, Player player, Map<String, String> custom,
      boolean parsePlaceholders) {
    if (text == null) {
      return "";
    }
    String resolved = placeholderUtil.parse(player, text, custom);
    return parsePlaceholders ? placeholderUtil.parse(player, resolved) : resolved;
  }
}
