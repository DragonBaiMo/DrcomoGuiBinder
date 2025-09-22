package cn.drcomo.drcomoguibinder.config;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 单个 GUI 菜单的完整定义。
 */
public final class GuiDefinition {

    private final String id;
    private final String title;
    private final int size;
    private final String openSound;
    private final String closeSound;
    private final Map<Integer, GuiItemDefinition> slotItemMap;
    private final List<GuiItemDefinition> items;

    private GuiDefinition(String id,
                          String title,
                          int size,
                          String openSound,
                          String closeSound,
                          Map<Integer, GuiItemDefinition> slotItemMap,
                          List<GuiItemDefinition> items) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.openSound = openSound;
        this.closeSound = closeSound;
        this.slotItemMap = slotItemMap;
        this.items = items;
    }

    public static GuiDefinition fromYaml(String fileName,
                                         YamlConfiguration config,
                                         DebugUtil logger) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");

        String id = config.getString("id", fileName);
        if (id == null || id.isBlank()) {
            id = fileName;
        }
        id = id.toLowerCase(Locale.ROOT);

        String title = config.getString("title", "&7未命名菜单");
        int size = Math.max(9, config.getInt("size", config.getInt("rows", 3) * 9));
        if (size % 9 != 0) {
            int corrected = ((size / 9) + 1) * 9;
            logger.warn("菜单 " + id + " 的 size 非 9 的倍数，已自动调整为 " + corrected + "。");
            size = corrected;
        }
        if (size > 54) {
            logger.warn("菜单 " + id + " 的 size 超过 54，已截断为 54。");
            size = 54;
        }

        String openSound = config.getString("open-sound", "");
        String closeSound = config.getString("close-sound", "");

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        Map<Integer, GuiItemDefinition> slotItemMap = new LinkedHashMap<>();
        List<GuiItemDefinition> items = new ArrayList<>();
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection section = itemsSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                GuiItemDefinition definition = GuiItemDefinition.fromSection(id, key, section, logger);
                items.add(definition);
                for (Integer slot : definition.getSlots()) {
                    if (slot < 0 || slot >= size) {
                        logger.warn("菜单 " + id + " 的物品 " + key + " 槽位超出范围: " + slot);
                        continue;
                    }
                    slotItemMap.put(slot, definition);
                }
            }
        } else {
            logger.warn("菜单 " + id + " 未配置 items 节点。");
        }

        return new GuiDefinition(id, title, size, openSound, closeSound, slotItemMap, items);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public String getOpenSound() {
        return openSound;
    }

    public String getCloseSound() {
        return closeSound;
    }

    public Map<Integer, GuiItemDefinition> getSlotItemMap() {
        return Collections.unmodifiableMap(slotItemMap);
    }

    public List<GuiItemDefinition> getItems() {
        return Collections.unmodifiableList(items);
    }
}
