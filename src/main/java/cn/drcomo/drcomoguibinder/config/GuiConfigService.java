package cn.drcomo.drcomoguibinder.config;

import cn.drcomo.corelib.async.AsyncTaskManager;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.config.model.EntryDef;
import cn.drcomo.drcomoguibinder.config.model.ItemTemplate;
import cn.drcomo.drcomoguibinder.config.model.MainGuiDef;
import cn.drcomo.drcomoguibinder.config.model.MainSlotDef;
import cn.drcomo.drcomoguibinder.config.model.SubGuiDef;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 负责加载 Main/Sub 配置文件并提供只读快照。
 */
public final class GuiConfigService {

  private final Plugin plugin;
  private final YamlUtil yamlUtil;
  private final DebugUtil logger;
  private final AsyncTaskManager asyncManager;

  private final AtomicReference<Map<String, MainGuiDef>> mainDefs = new AtomicReference<>(Map.of());
  private final AtomicReference<Map<String, SubGuiDef>> subDefs = new AtomicReference<>(Map.of());
  private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

  private DirectoryWatcher mainWatcher;
  private DirectoryWatcher subWatcher;

  public GuiConfigService(Plugin plugin, YamlUtil yamlUtil, DebugUtil logger,
      AsyncTaskManager asyncManager) {
    this.plugin = plugin;
    this.yamlUtil = yamlUtil;
    this.logger = logger;
    this.asyncManager = asyncManager;
  }

  public void init() {
    yamlUtil.ensureDirectory("Main");
    yamlUtil.ensureDirectory("Sub");
    reloadAll();
    startWatchers();
  }

  public void reloadAll() {
    Map<String, MainGuiDef> mains = new LinkedHashMap<>();
    Map<String, SubGuiDef> subs = new LinkedHashMap<>();

    loadSubDefinitions(subs);
    loadMainDefinitions(mains);

    subDefs.set(Collections.unmodifiableMap(subs));
    mainDefs.set(Collections.unmodifiableMap(mains));
    logger.info("配置加载完成: main=" + mains.size() + ", sub=" + subs.size());
    notifyReloadListeners();
  }

  public MainGuiDef getMain(String id) {
    return mainDefs.get().get(id);
  }

  public SubGuiDef getSub(String id) {
    return subDefs.get().get(id);
  }

  public Set<String> getMainIds() {
    return mainDefs.get().keySet();
  }

  public Set<String> getSubIds() {
    return subDefs.get().keySet();
  }

  public void addReloadListener(Runnable listener) {
    reloadListeners.add(listener);
  }

  public void shutdown() {
    if (mainWatcher != null) {
      mainWatcher.close();
      mainWatcher = null;
    }
    if (subWatcher != null) {
      subWatcher.close();
      subWatcher = null;
    }
  }

  private void notifyReloadListeners() {
    for (Runnable listener : reloadListeners) {
      try {
        listener.run();
      } catch (Exception ex) {
        logger.error("执行配置重载监听器时发生异常", ex);
      }
    }
  }

  private void loadMainDefinitions(Map<String, MainGuiDef> mains) {
    Map<String, YamlConfiguration> configs;
    try {
      configs = yamlUtil.loadAllConfigsInFolder("Main");
    } catch (Exception ex) {
      logger.error("读取 Main 配置文件夹失败", ex);
      return;
    }
    for (Map.Entry<String, YamlConfiguration> entry : configs.entrySet()) {
      String fileName = entry.getKey();
      try {
        YamlConfiguration yaml = entry.getValue();
        MainGuiDef def = parseMain(fileName, yaml);
        if (def != null) {
          mains.put(def.getId(), def);
        }
      } catch (Exception ex) {
        logger.error("解析 Main 配置失败: " + fileName, ex);
      }
    }
  }

  private void loadSubDefinitions(Map<String, SubGuiDef> subs) {
    Map<String, YamlConfiguration> configs;
    try {
      configs = yamlUtil.loadAllConfigsInFolder("Sub");
    } catch (Exception ex) {
      logger.error("读取 Sub 配置文件夹失败", ex);
      return;
    }
    for (Map.Entry<String, YamlConfiguration> entry : configs.entrySet()) {
      String fileName = entry.getKey();
      try {
        YamlConfiguration yaml = entry.getValue();
        SubGuiDef def = parseSub(fileName, yaml);
        if (def != null) {
          subs.put(def.getId(), def);
        }
      } catch (Exception ex) {
        logger.error("解析 Sub 配置失败: " + fileName, ex);
      }
    }
  }

  private MainGuiDef parseMain(String fileName, YamlConfiguration yaml) {
    String id = yaml.getString("id", fileName.replace(".yml", ""));
    String title = Objects.requireNonNullElse(yaml.getString("title"), id);
    int size = yaml.getInt("size", 54);
    if (size % 9 != 0) {
      logger.warn("Main " + id + " 的 size 配置不是 9 的倍数，已自动向上取整");
      size = ((size + 8) / 9) * 9;
    }
    List<Map<?, ?>> slotList = yaml.getMapList("slots");
    List<MainSlotDef> slots = new ArrayList<>();
    for (Map<?, ?> map : slotList) {
      int slotIndex = ((Number) map.getOrDefault("slot", slots.size())).intValue();
      String subId = Objects.toString(map.get("sub"), null);
      ItemTemplate displayEmpty = parseItemTemplate(map.get("displayEmpty"));
      if (displayEmpty == null || displayEmpty.isEmpty()) {
        displayEmpty = ItemTemplate.builder()
            .material("GRAY_STAINED_GLASS_PANE")
            .name("&7未绑定槽位")
            .build();
      }
      slots.add(new MainSlotDef(slotIndex, subId, displayEmpty));
    }
    return new MainGuiDef(id, title, size, slots);
  }

  private SubGuiDef parseSub(String fileName, YamlConfiguration yaml) {
    String id = yaml.getString("id", fileName.replace(".yml", ""));
    String title = Objects.requireNonNullElse(yaml.getString("title"), id);
    int size = yaml.getInt("size", 54);
    if (size % 9 != 0) {
      size = ((size + 8) / 9) * 9;
    }
    List<EntryDef> entries = new ArrayList<>();
    List<?> entryList = yaml.getList("entries");
    if (entryList != null) {
      int index = 0;
      for (Object obj : entryList) {
        if (obj instanceof Map<?, ?> map) {
          entries.add(parseEntry(id, index++, map));
        }
      }
    }
    return new SubGuiDef(id, title, size, entries);
  }

  private EntryDef parseEntry(String subId, int index, Map<?, ?> map) {
    int slot = ((Number) map.getOrDefault("slot", index)).intValue();
    String key = Objects.toString(map.get("key"), "entry_" + index);
    String value = Objects.toString(map.get("value"), "");
    ItemTemplate display = parseItemTemplate(map.get("display"));
    if (display == null || display.isEmpty()) {
      display = ItemTemplate.builder()
          .material("BOOK")
          .name("&f" + key)
          .build();
    }
    Map<String, ItemTemplate> overrides = new HashMap<>();
    Object overrideObj = map.get("overrides");
    if (overrideObj instanceof Map<?, ?> overrideMap) {
      for (Map.Entry<?, ?> entry : overrideMap.entrySet()) {
        String mainId = Objects.toString(entry.getKey());
        overrides.put(mainId, parseItemTemplate(entry.getValue()));
      }
    }
    return new EntryDef(slot, key, value, display, overrides);
  }

  private ItemTemplate parseItemTemplate(Object sectionObj) {
    if (sectionObj == null) {
      return null;
    }
    if (sectionObj instanceof ConfigurationSection section) {
      return parseItemTemplate(section);
    }
    if (sectionObj instanceof Map<?, ?> map) {
      YamlConfiguration temp = new YamlConfiguration();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        temp.set(String.valueOf(entry.getKey()), entry.getValue());
      }
      return parseItemTemplate(temp);
    }
    return ItemTemplate.builder().material("STONE").name("&7未定义").build();
  }

  private ItemTemplate parseItemTemplate(ConfigurationSection section) {
    if (section == null) {
      return null;
    }
    ItemTemplate.Builder builder = ItemTemplate.builder();
    builder.material(section.getString("material"));
    builder.amount(section.getInt("amount", 1));
    builder.name(section.getString("name"));
    List<String> lore = section.getStringList("lore");
    if (!lore.isEmpty()) {
      builder.lore(lore);
    }
    if (section.contains("customModelData")) {
      builder.customModelData(section.getInt("customModelData"));
    }
    List<String> flags = section.getStringList("flags");
    if (!flags.isEmpty()) {
      builder.flags(flags);
    }
    builder.glow(section.getBoolean("glow", false));
    if (section.contains("skullTexture")) {
      builder.skullTexture(section.getString("skullTexture"));
    }
    ConfigurationSection nbtSection = section.getConfigurationSection("nbt");
    if (nbtSection != null) {
      Map<String, Object> nbt = new LinkedHashMap<>();
      for (String key : nbtSection.getKeys(false)) {
        nbt.put(key, nbtSection.get(key));
      }
      builder.nbt(nbt);
    }
    return builder.build();
  }

  private void startWatchers() {
    Path mainPath = plugin.getDataFolder().toPath().resolve("Main");
    Path subPath = plugin.getDataFolder().toPath().resolve("Sub");
    try {
      mainWatcher = new DirectoryWatcher(mainPath, this::reloadAll);
      subWatcher = new DirectoryWatcher(subPath, this::reloadAll);
      asyncManager.getExecutor().submit(mainWatcher);
      asyncManager.getExecutor().submit(subWatcher);
    } catch (IOException ex) {
      logger.error("初始化配置热更新监听失败", ex);
    }
  }

  private final class DirectoryWatcher implements Runnable {

    private final Path directory;
    private final WatchService watchService;
    private volatile boolean running = true;
    private final Runnable onReload;

    private DirectoryWatcher(Path directory, Runnable onReload) throws IOException {
      this.directory = directory;
      this.onReload = onReload;
      this.watchService = FileSystems.getDefault().newWatchService();
      Files.createDirectories(directory);
      directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
    }

    @Override
    public void run() {
      while (running) {
        WatchKey key;
        try {
          key = watchService.take();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return;
        } catch (ClosedWatchServiceException ex) {
          return;
        } catch (Exception ex) {
          logger.error("配置文件监听异常中断", ex);
          return;
        }
        boolean trigger = false;
        for (WatchEvent<?> event : key.pollEvents()) {
          Object context = event.context();
          if (context instanceof Path path) {
            Path changed = directory.resolve(path);
            if (changed.toString().endsWith(".yml")) {
              trigger = true;
            }
          }
        }
        if (trigger) {
          Bukkit.getScheduler().runTask(plugin, onReload);
        }
        boolean valid = key.reset();
        if (!valid) {
          break;
        }
      }
    }

    private void close() {
      running = false;
      try {
        watchService.close();
      } catch (IOException ex) {
        logger.warn("关闭配置监听时出现异常", ex);
      }
    }
  }
}
