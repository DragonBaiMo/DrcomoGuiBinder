package cn.drcomo.drcomoguibinder.config;

import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * 负责批量加载 GUI 配置文件并转换成 {@link GuiDefinition}。
 */
public final class GuiConfigLoader {

    private final YamlUtil yamlUtil;
    private final DebugUtil logger;
    private final Set<String> lastLoadedFiles = new HashSet<>();

    public GuiConfigLoader(YamlUtil yamlUtil, DebugUtil logger) {
        this.yamlUtil = Objects.requireNonNull(yamlUtil, "yamlUtil");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Map<String, GuiDefinition> loadAll(String folder) {
        Map<String, GuiDefinition> result = new LinkedHashMap<>();
        Map<String, YamlConfiguration> raw = yamlUtil.loadAllConfigsInFolder(folder);
        lastLoadedFiles.clear();
        raw.forEach((fileName, configuration) -> {
            try {
                GuiDefinition definition = GuiDefinition.fromYaml(fileName.replace(".yml", ""), configuration, logger);
                result.put(definition.getId(), definition);
                lastLoadedFiles.add(fileName.replace(".yml", ""));
            } catch (Exception ex) {
                logger.error("加载 GUI 配置 " + fileName + " 失败: " + ex.getMessage());
            }
        });
        return result;
    }

    public Set<String> getLastLoadedFiles() {
        return Collections.unmodifiableSet(lastLoadedFiles);
    }
}
