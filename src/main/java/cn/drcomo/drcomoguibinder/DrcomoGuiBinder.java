package cn.drcomo.drcomoguibinder;

import cn.drcomo.corelib.async.AsyncTaskManager;
import cn.drcomo.corelib.async.AsyncTaskManager.Builder;
import cn.drcomo.corelib.color.ColorUtil;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GuiManager;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.util.DebugUtil.LogLevel;
import cn.drcomo.drcomoguibinder.bind.BindingRepository;
import cn.drcomo.drcomoguibinder.bind.BindingService;
import cn.drcomo.drcomoguibinder.command.CommandHandler;
import cn.drcomo.drcomoguibinder.config.GuiConfigService;
import cn.drcomo.drcomoguibinder.gui.GuiListener;
import cn.drcomo.drcomoguibinder.gui.MainGuiController;
import cn.drcomo.drcomoguibinder.gui.SubGuiController;
import cn.drcomo.drcomoguibinder.gui.session.BindSession;
import cn.drcomo.drcomoguibinder.papi.BinderPlaceholderExpansion;
import cn.drcomo.drcomoguibinder.template.ItemTemplateRenderer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件主类，负责初始化与释放资源。
 */
public final class DrcomoGuiBinder extends JavaPlugin {

  private DebugUtil logger;
  private YamlUtil yamlUtil;
  private AsyncTaskManager asyncManager;
  private MessageService messageService;
  private GuiConfigService configService;
  private BindingRepository bindingRepository;
  private BindingService bindingService;
  private ItemTemplateRenderer itemRenderer;
  private PlaceholderAPIUtil valuePlaceholder;
  private PlaceholderAPIUtil keyPlaceholder;
  private PlaceholderAPIUtil displayPlaceholder;
  private BinderPlaceholderExpansion placeholderExpansion;
  private CommandHandler commandHandler;
  private GUISessionManager guiSessionManager;
  private GuiActionDispatcher guiDispatcher;
  private MainGuiController mainGuiController;
  private SubGuiController subGuiController;
  private GuiListener guiListener;
  private cn.drcomo.corelib.gui.session.PlayerSessionManager<BindSession> bindSessionManager;
  private boolean resolveAtBind;
  private long clickCooldownMs;

  @Override
  public void onEnable() {
    ColorUtil.initMajorVersion(getServer());
    this.logger = new DebugUtil(this, LogLevel.INFO);
    this.yamlUtil = new YamlUtil(this, logger);

    // 拷贝默认文件结构
    yamlUtil.copyYamlFile("config.yml", "");
    yamlUtil.copyYamlFile("lang.yml", "");
    yamlUtil.ensureFolderAndCopyDefaults("Main", "Main");
    yamlUtil.ensureFolderAndCopyDefaults("Sub", "Sub");
    if (!getDataFolder().toPath().resolve("schema.sql").toFile().exists()) {
      saveResource("schema.sql", false);
    }

    yamlUtil.loadConfig("config");
    YamlConfiguration config = yamlUtil.getConfig("config");

    this.resolveAtBind = config.getBoolean("resolveAtBind", false);
    this.clickCooldownMs = config.getLong("clickCooldownMs", 300L);
    long sessionTimeout = config.getLong("sessionTimeoutMs", 90000L);
    String dbFile = resolveDatabasePath(config);
    int writerThreads = Math.max(1, config.getInt("async.writerThreads", 2));
    String placeholderPrefix = config.getString("placeholders.prefix", "dgb");
    String logLevel = config.getString("logLevel", "INFO");
    try {
      logger.setLevel(LogLevel.valueOf(logLevel.toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException ex) {
      logger.warn("未知的日志级别: " + logLevel + "，已使用 INFO");
      logger.setLevel(LogLevel.INFO);
    }

    Builder builder = AsyncTaskManager.newBuilder(this, logger).poolSize(writerThreads);
    this.asyncManager = builder.build();

    this.valuePlaceholder = new PlaceholderAPIUtil(this, placeholderPrefix);
    this.keyPlaceholder = new PlaceholderAPIUtil(this, placeholderPrefix + "k");
    this.displayPlaceholder = new PlaceholderAPIUtil(this, placeholderPrefix + "d");

    this.messageService = new MessageService(this, logger, yamlUtil, valuePlaceholder,
        "lang", "messages.");

    this.guiSessionManager = new GUISessionManager(this, logger, messageService, sessionTimeout);
    GuiManager guiManager = new GuiManager(logger);
    this.guiDispatcher = new GuiActionDispatcher(logger, guiSessionManager, guiManager);

    this.bindSessionManager = new cn.drcomo.corelib.gui.session.PlayerSessionManager<>(this, logger,
        sessionTimeout);

    this.configService = new GuiConfigService(this, yamlUtil, logger, asyncManager);
    configService.init();

    this.bindingRepository = new BindingRepository(this, logger, dbFile, List.of("schema.sql"));
    bindingRepository.connect();
    this.bindingService = new BindingService(bindingRepository, asyncManager, logger);
    try {
      bindingService.loadAll().join();
    } catch (CompletionException ex) {
      logger.error("加载绑定数据失败", ex.getCause() == null ? ex : ex.getCause());
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.itemRenderer = new ItemTemplateRenderer(logger, valuePlaceholder, getName());

    this.placeholderExpansion = new BinderPlaceholderExpansion(valuePlaceholder, keyPlaceholder,
        displayPlaceholder, configService, bindingService, itemRenderer, !resolveAtBind, logger);

    this.mainGuiController = new MainGuiController(guiSessionManager, guiDispatcher, configService,
        bindingService, itemRenderer, bindSessionManager, logger, messageService,
        !resolveAtBind, clickCooldownMs);
    this.subGuiController = new SubGuiController(guiSessionManager, guiDispatcher, configService,
        bindingService, itemRenderer, bindSessionManager, logger, messageService,
        valuePlaceholder, resolveAtBind, mainGuiController, this);
    this.mainGuiController.setSubGuiController(subGuiController);
    this.subGuiController.setMainGuiController(mainGuiController);

    this.guiListener = new GuiListener(guiDispatcher, guiSessionManager, logger,
        mainGuiController, subGuiController);
    PluginManager pm = Bukkit.getPluginManager();
    pm.registerEvents(guiListener, this);

    placeholderExpansion.registerAll();
    configService.addReloadListener(() -> {
      placeholderExpansion.registerAll();
      mainGuiController.handleConfigReload();
      subGuiController.handleConfigReload();
    });

    this.commandHandler = new CommandHandler(this, configService, bindingService,
        mainGuiController, messageService, placeholderExpansion, logger, valuePlaceholder,
        resolveAtBind);
    Objects.requireNonNull(getCommand("drcomoguibinder"), "drcomoguibinder 命令未在 plugin.yml 中声明")
        .setExecutor(commandHandler);
    getCommand("drcomoguibinder").setTabCompleter(commandHandler);

    logger.info("DrcomoGuiBinder 已成功启用");
  }

  @Override
  public void onDisable() {
    try {
      if (bindingService != null) {
        bindingService.flush().join();
      }
    } catch (Exception ex) {
      logger.error("等待写入完成时发生异常", ex);
    }
    if (guiSessionManager != null) {
      guiSessionManager.closeAllSessions();
      guiSessionManager.flushOnDisable();
    }
    if (configService != null) {
      configService.shutdown();
    }
    if (yamlUtil != null) {
      yamlUtil.close();
    }
    if (asyncManager != null) {
      asyncManager.close();
    }
    if (bindingRepository != null) {
      bindingRepository.close();
    }
    logger.info("DrcomoGuiBinder 已卸载");
  }

  private String resolveDatabasePath(YamlConfiguration config) {
    if (config.isConfigurationSection("database")) {
      ConfigurationSection section = config.getConfigurationSection("database");
      if (section != null) {
        return section.getString("file", "data.db");
      }
    }
    return config.getString("database", "data.db");
  }
}
