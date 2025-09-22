package cn.drcomo.drcomoguibinder;

import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.gui.GuiManager;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.sound.SoundManager;
import cn.drcomo.corelib.util.ColorUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.command.GuiBinderCommand;
import cn.drcomo.drcomoguibinder.config.GuiConfigLoader;
import cn.drcomo.drcomoguibinder.config.GuiDefinition;
import cn.drcomo.drcomoguibinder.config.PluginConfig;
import cn.drcomo.drcomoguibinder.gui.GuiEngine;
import cn.drcomo.drcomoguibinder.gui.GuiListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 插件主类，负责初始化依赖、注册指令与监听器以及配置热重载。
 */
public final class DrcomoGuiBinder extends JavaPlugin {

    private DebugUtil logger;
    private YamlUtil yamlUtil;
    private PluginConfig pluginConfig;
    private PlaceholderAPIUtil placeholderUtil;
    private MessageService messageService;
    private SoundManager soundManager;
    private GuiManager guiManager;
    private GUISessionManager sessionManager;
    private GuiActionDispatcher dispatcher;
    private GuiEngine guiEngine;
    private GuiConfigLoader guiConfigLoader;
    private final List<AutoCloseable> watchHandles = new ArrayList<>();
    private File debugLogFile;

    @Override
    public void onEnable() {
        ColorUtil.initMajorVersion(getServer());
        this.logger = new DebugUtil(this, DebugUtil.LogLevel.INFO);
        this.yamlUtil = new YamlUtil(this, logger);
        copyBaseResources();
        yamlUtil.loadConfig("config");
        this.pluginConfig = PluginConfig.fromYaml(yamlUtil.getConfig("config"));
        applyLoggerSettings();

        this.placeholderUtil = new PlaceholderAPIUtil(this, pluginConfig.getPlaceholderIdentifier());
        this.messageService = new MessageService(this, logger, yamlUtil, placeholderUtil, pluginConfig.getLanguageFile(), "messages.");
        this.messageService.reloadLanguages();

        this.soundManager = new SoundManager(this, yamlUtil, logger, pluginConfig.getSoundConfig(), pluginConfig.getSoundVolume(), pluginConfig.isWarnMissingSound());
        this.soundManager.setVolumeMultiplier(pluginConfig.getSoundVolume());
        this.soundManager.loadSounds();

        this.guiManager = new GuiManager(logger);
        this.sessionManager = new GUISessionManager(this, logger, messageService);
        if (pluginConfig.getSessionTimeoutMillis() > 0) {
            sessionManager.setSessionTimeout(pluginConfig.getSessionTimeoutMillis());
        }
        this.dispatcher = new GuiActionDispatcher(logger, sessionManager, guiManager);
        this.guiEngine = new GuiEngine(logger, messageService, placeholderUtil, soundManager, sessionManager, dispatcher, guiManager, pluginConfig);
        this.guiConfigLoader = new GuiConfigLoader(yamlUtil, logger);

        ensureGuiFolder();
        reloadGuiDefinitions();

        GuiBinderCommand executor = new GuiBinderCommand(this, messageService, guiEngine, pluginConfig);
        Objects.requireNonNull(getCommand("guibinder"), "guibinder 指令未在 plugin.yml 中声明").setExecutor(executor);
        Objects.requireNonNull(getCommand("guibinder")).setTabCompleter(executor);

        Bukkit.getPluginManager().registerEvents(new GuiListener(sessionManager, dispatcher, guiManager, guiEngine, pluginConfig), this);
        registerPlaceholders();
        registerWatchers();
        logger.info("DrcomoGuiBinder 已成功加载，当前已解析 " + guiEngine.getLoadedMenuCount() + " 个菜单。");
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(player -> guiEngine.closeForPlayer(player, false));
        clearWatchers();
        sessionManager.flushOnDisable();
        sessionManager.closeAllSessions();
        yamlUtil.saveAllDirtyConfigs();
        yamlUtil.close();
        logger.info("DrcomoGuiBinder 已安全卸载。");
    }

    public void reloadEverything() {
        clearWatchers();
        yamlUtil.reloadConfig("config");
        this.pluginConfig = PluginConfig.fromYaml(yamlUtil.getConfig("config"));
        applyLoggerSettings();
        guiEngine.updatePluginConfig(pluginConfig);
        messageService.switchLanguage(pluginConfig.getLanguageFile());
        messageService.reloadLanguages();
        ensureGuiFolder();
        soundManager.setVolumeMultiplier(pluginConfig.getSoundVolume());
        soundManager.loadSounds();
        if (pluginConfig.getSessionTimeoutMillis() > 0) {
            sessionManager.setSessionTimeout(pluginConfig.getSessionTimeoutMillis());
        }
        Bukkit.getOnlinePlayers().forEach(player -> guiEngine.closeForPlayer(player, false));
        reloadGuiDefinitions();
        registerWatchers();
    }

    private void reloadGuiDefinitions() {
        Map<String, GuiDefinition> loaded = guiConfigLoader.loadAll(pluginConfig.getGuiFolder());
        guiEngine.updateDefinitions(loaded);
        logger.info("已重新加载 " + loaded.size() + " 个菜单配置。");
    }

    private void copyBaseResources() {
        yamlUtil.copyYamlFile("config.yml", "");
        yamlUtil.copyYamlFile("lang.yml", "");
        yamlUtil.copyYamlFile("sounds.yml", "");
    }

    private void ensureGuiFolder() {
        yamlUtil.ensureFolderAndCopyDefaults("guis", pluginConfig.getGuiFolder());
    }

    private void registerPlaceholders() {
        placeholderUtil.register("menu_count", (player, raw) -> String.valueOf(guiEngine.getLoadedMenuCount()));
    }

    private void registerWatchers() {
        watchHandles.add(yamlUtil.watchConfig("config", cfg -> Bukkit.getScheduler().runTask(this, this::reloadEverything)));
        if (pluginConfig.isLanguageAutoReload()) {
            watchHandles.add(yamlUtil.watchConfig(pluginConfig.getLanguageFile(), cfg -> Bukkit.getScheduler().runTask(this, () -> messageService.reloadLanguages())));
        }
        if (pluginConfig.isGuiHotReload()) {
            for (String file : guiConfigLoader.getLastLoadedFiles()) {
                watchHandles.add(yamlUtil.watchConfig(pluginConfig.getGuiFolder() + "/" + file, cfg -> Bukkit.getScheduler().runTask(this, this::reloadGuiDefinitions)));
            }
        }
    }

    private void applyLoggerSettings() {
        logger.setLevel(pluginConfig.getLogLevel());
        logger.setPrefix(pluginConfig.getLogPrefix());
        String fileName = pluginConfig.getLogFile();
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(getDataFolder(), fileName);
            if (!file.equals(debugLogFile)) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    logger.addFileHandler(file);
                    debugLogFile = file;
                } catch (IOException ex) {
                    getLogger().warning("无法创建调试日志文件: " + ex.getMessage());
                }
            }
        }
    }

    private void clearWatchers() {
        for (AutoCloseable handle : watchHandles) {
            try {
                handle.close();
            } catch (Exception ignored) {
            }
        }
        watchHandles.clear();
    }
}
