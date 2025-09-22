package cn.drcomo.drcomoguibinder.config;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * GUI 中单个物品格的配置定义。
 */
public final class GuiItemDefinition {

    private final String id;
    private final Material material;
    private final int amount;
    private final boolean glow;
    private final Integer customModelData;
    private final String displayName;
    private final List<String> lore;
    private final List<Integer> slots;
    private final List<GuiActionDefinition> actions;

    private GuiItemDefinition(String id,
                              Material material,
                              int amount,
                              boolean glow,
                              Integer customModelData,
                              String displayName,
                              List<String> lore,
                              List<Integer> slots,
                              List<GuiActionDefinition> actions) {
        this.id = id;
        this.material = material;
        this.amount = amount;
        this.glow = glow;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.lore = lore;
        this.slots = slots;
        this.actions = actions;
    }

    public static GuiItemDefinition fromSection(String menuId,
                                                String itemId,
                                                ConfigurationSection section,
                                                DebugUtil logger) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(logger, "logger");

        String materialName = section.getString("material", "BARRIER");
        Material material = Material.matchMaterial(materialName, true);
        if (material == null) {
            logger.warn("菜单 " + menuId + " 的物品 " + itemId + " 材质 " + materialName + " 无法识别，已回退为 BARRIER。");
            material = Material.BARRIER;
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        if (amount > material.getMaxStackSize()) {
            logger.warn("菜单 " + menuId + " 的物品 " + itemId + " 数量超过堆叠上限，已截断为最大值。");
            amount = material.getMaxStackSize();
        }

        boolean glow = section.getBoolean("glow", false);
        Integer customModelData = section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null;
        String displayName = section.getString("name");
        List<String> lore = section.getStringList("lore");

        List<Integer> slots = parseSlots(menuId, itemId, section, logger);
        List<Map<?, ?>> rawActions = section.getMapList("actions");
        List<GuiActionDefinition> actions = new ArrayList<>();
        for (Map<?, ?> raw : rawActions) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) raw;
                actions.add(GuiActionDefinition.fromMap(typed, menuId, itemId));
            } catch (Exception ex) {
                logger.error("解析菜单 " + menuId + " 物品 " + itemId + " 的动作失败: " + ex.getMessage());
            }
        }

        return new GuiItemDefinition(
                itemId,
                material,
                amount,
                glow,
                customModelData,
                displayName,
                lore,
                slots,
                actions
        );
    }

    private static List<Integer> parseSlots(String menuId,
                                            String itemId,
                                            ConfigurationSection section,
                                            DebugUtil logger) {
        Set<Integer> slotSet = new LinkedHashSet<>();
        if (section.isInt("slot")) {
            slotSet.add(section.getInt("slot"));
        } else if (section.contains("slot")) {
            try {
                slotSet.add(Integer.parseInt(section.getString("slot")));
            } catch (NumberFormatException ex) {
                logger.warn("菜单 " + menuId + " 的物品 " + itemId + " slot 配置无效: " + section.getString("slot"));
            }
        }

        if (section.isList("slots")) {
            for (Object value : section.getList("slots")) {
                if (value instanceof Number number) {
                    slotSet.add(number.intValue());
                } else if (value instanceof String str) {
                    addSlotRange(slotSet, str, menuId, itemId, logger);
                }
            }
        } else if (section.isString("slots")) {
            addSlotRange(slotSet, section.getString("slots"), menuId, itemId, logger);
        }

        if (slotSet.isEmpty()) {
            logger.warn("菜单 " + menuId + " 的物品 " + itemId + " 未配置 slot，默认放置在 0 号槽位。");
            slotSet.add(0);
        }
        return new ArrayList<>(slotSet);
    }

    private static void addSlotRange(Set<Integer> slots,
                                     String input,
                                     String menuId,
                                     String itemId,
                                     DebugUtil logger) {
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        String cleaned = input.replace(" ", "");
        String[] parts = cleaned.split(",");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length != 2) {
                    logger.warn("菜单 " + menuId + " 的物品 " + itemId + " slots 片段无效: " + part);
                    continue;
                }
                try {
                    int start = Integer.parseInt(range[0]);
                    int end = Integer.parseInt(range[1]);
                    if (start > end) {
                        int swap = start;
                        start = end;
                        end = swap;
                    }
                    for (int i = start; i <= end; i++) {
                        slots.add(i);
                    }
                } catch (NumberFormatException ex) {
                    logger.warn("菜单 " + menuId + " 的物品 " + itemId + " slots 范围无效: " + part);
                }
            } else {
                try {
                    slots.add(Integer.parseInt(part));
                } catch (NumberFormatException ex) {
                    logger.warn("菜单 " + menuId + " 的物品 " + itemId + " slots 值无效: " + part);
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isGlow() {
        return glow;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public List<Integer> getSlots() {
        return slots;
    }

    public List<GuiActionDefinition> getActions() {
        return actions;
    }
}
