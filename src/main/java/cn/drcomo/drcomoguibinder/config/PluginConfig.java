package cn.drcomo.drcomoguibinder.config;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Locale;
import java.util.Objects;

/**
 * <p>插件主配置的抽象表示，负责从 {@link YamlConfiguration} 中解析所有业务开关。</p>
 * <p>配置项大量用于驱动运行时的行为，因而在对象内部做好不可变封装，保证线程安全。</p>
 */
public final class PluginConfig {

    private final DebugUtil.LogLevel logLevel;
    private final String logPrefix;
    private final String logFile;
    private final String languageFile;
    private final boolean languageAutoReload;
    private final String placeholderIdentifier;
    private final String soundConfig;
    private final float soundVolume;
    private final boolean warnMissingSound;
    private final String guiFolder;
    private final long sessionTimeoutMillis;
    private final String defaultOpenSound;
    private final String defaultCloseSound;
    private final boolean closeOnQuit;
    private final PermissionSettings permissions;
    private final CommandSettings commandSettings;
    private final boolean guiHotReload;

    private PluginConfig(DebugUtil.LogLevel logLevel,
                         String logPrefix,
                         String logFile,
                         String languageFile,
                         boolean languageAutoReload,
                         String placeholderIdentifier,
                         String soundConfig,
                         float soundVolume,
                         boolean warnMissingSound,
                         String guiFolder,
                         long sessionTimeoutMillis,
                         String defaultOpenSound,
                         String defaultCloseSound,
                         boolean closeOnQuit,
                         PermissionSettings permissions,
                         CommandSettings commandSettings,
                         boolean guiHotReload) {
        this.logLevel = logLevel;
        this.logPrefix = logPrefix;
        this.logFile = logFile;
        this.languageFile = languageFile;
        this.languageAutoReload = languageAutoReload;
        this.placeholderIdentifier = placeholderIdentifier;
        this.soundConfig = soundConfig;
        this.soundVolume = soundVolume;
        this.warnMissingSound = warnMissingSound;
        this.guiFolder = guiFolder;
        this.sessionTimeoutMillis = sessionTimeoutMillis;
        this.defaultOpenSound = defaultOpenSound;
        this.defaultCloseSound = defaultCloseSound;
        this.closeOnQuit = closeOnQuit;
        this.permissions = permissions;
        this.commandSettings = commandSettings;
        this.guiHotReload = guiHotReload;
    }

    /**
     * 从 YAML 配置生成不可变 {@link PluginConfig} 实例。
     *
     * @param config 插件主配置
     * @return 配置对象
     */
    public static PluginConfig fromYaml(YamlConfiguration config) {
        Objects.requireNonNull(config, "config");

        ConfigurationSection debug = config.getConfigurationSection("debug");
        DebugUtil.LogLevel level = DebugUtil.LogLevel.INFO;
        String prefix = "&f[&bGuiBinder&r]&f ";
        String file = "";
        if (debug != null) {
            String levelRaw = debug.getString("level", "INFO").toUpperCase(Locale.ROOT);
            try {
                level = DebugUtil.LogLevel.valueOf(levelRaw);
            } catch (IllegalArgumentException ignored) {
                level = DebugUtil.LogLevel.INFO;
            }
            prefix = debug.getString("prefix", prefix);
            file = debug.getString("file", "");
        }

        ConfigurationSection lang = config.getConfigurationSection("language");
        String languagePath = lang != null ? lang.getString("file", "lang") : "lang";
        boolean languageAutoReload = lang != null && lang.getBoolean("auto-reload", true);

        ConfigurationSection placeholder = config.getConfigurationSection("placeholder");
        String identifier = placeholder != null ? placeholder.getString("identifier", "drcomoguibinder") : "drcomoguibinder";

        ConfigurationSection sounds = config.getConfigurationSection("sounds");
        String soundConfig = sounds != null ? sounds.getString("config", "sounds") : "sounds";
        float soundVolume = sounds != null ? (float) sounds.getDouble("volume", 1.0D) : 1.0F;
        boolean warnMissingSound = sounds == null || sounds.getBoolean("warn-missing", true);

        ConfigurationSection gui = config.getConfigurationSection("gui");
        String guiFolder = gui != null ? gui.getString("folder", "guis") : "guis";
        long sessionTimeoutSeconds = gui != null ? gui.getLong("session-timeout", 300L) : 300L;
        long sessionTimeoutMillis = sessionTimeoutSeconds <= 0 ? sessionTimeoutSeconds : sessionTimeoutSeconds * 1000L;
        String defaultOpenSound = gui != null ? gui.getString("default-open-sound", "") : "";
        String defaultCloseSound = gui != null ? gui.getString("default-close-sound", "") : "";
        boolean closeOnQuit = gui == null || gui.getBoolean("close-on-quit", true);

        ConfigurationSection permissionsSection = config.getConfigurationSection("permissions");
        PermissionSettings permissions = new PermissionSettings(
                permissionsSection != null ? permissionsSection.getString("reload", "drcomoguibinder.reload") : "drcomoguibinder.reload",
                permissionsSection != null ? permissionsSection.getString("open", "drcomoguibinder.open") : "drcomoguibinder.open",
                permissionsSection != null ? permissionsSection.getString("open-others", "drcomoguibinder.open.others") : "drcomoguibinder.open.others"
        );

        ConfigurationSection command = config.getConfigurationSection("command");
        boolean allowConsoleOpen = command == null || command.getBoolean("allow-console-open", true);
        boolean notifyReloadSuccess = command == null || command.getBoolean("notify-reload-success", true);
        CommandSettings commandSettings = new CommandSettings(allowConsoleOpen, notifyReloadSuccess);

        ConfigurationSection hotReload = config.getConfigurationSection("hot-reload");
        boolean guiHotReload = hotReload == null || hotReload.getBoolean("enable", true);

        return new PluginConfig(
                level,
                prefix,
                file,
                languagePath,
                languageAutoReload,
                identifier,
                soundConfig,
                soundVolume,
                warnMissingSound,
                guiFolder,
                sessionTimeoutMillis,
                normalizeEmpty(defaultOpenSound),
                normalizeEmpty(defaultCloseSound),
                closeOnQuit,
                permissions,
                commandSettings,
                guiHotReload
        );
    }

    private static String normalizeEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    public DebugUtil.LogLevel getLogLevel() {
        return logLevel;
    }

    public String getLogPrefix() {
        return logPrefix;
    }

    public String getLogFile() {
        return logFile;
    }

    public String getLanguageFile() {
        return languageFile;
    }

    public boolean isLanguageAutoReload() {
        return languageAutoReload;
    }

    public String getPlaceholderIdentifier() {
        return placeholderIdentifier;
    }

    public String getSoundConfig() {
        return soundConfig;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public boolean isWarnMissingSound() {
        return warnMissingSound;
    }

    public String getGuiFolder() {
        return guiFolder;
    }

    public long getSessionTimeoutMillis() {
        return sessionTimeoutMillis;
    }

    public String getDefaultOpenSound() {
        return defaultOpenSound;
    }

    public String getDefaultCloseSound() {
        return defaultCloseSound;
    }

    public boolean isCloseOnQuit() {
        return closeOnQuit;
    }

    public PermissionSettings getPermissions() {
        return permissions;
    }

    public CommandSettings getCommandSettings() {
        return commandSettings;
    }

    public boolean isGuiHotReload() {
        return guiHotReload;
    }

    /**
     * 权限相关配置的只读封装。
     */
    public static final class PermissionSettings {
        private final String reloadPermission;
        private final String openPermission;
        private final String openOthersPermission;

        public PermissionSettings(String reloadPermission, String openPermission, String openOthersPermission) {
            this.reloadPermission = Objects.requireNonNullElse(reloadPermission, "drcomoguibinder.reload");
            this.openPermission = Objects.requireNonNullElse(openPermission, "drcomoguibinder.open");
            this.openOthersPermission = Objects.requireNonNullElse(openOthersPermission, "drcomoguibinder.open.others");
        }

        public String reloadPermission() {
            return reloadPermission;
        }

        public String openPermission() {
            return openPermission;
        }

        public String openOthersPermission() {
            return openOthersPermission;
        }
    }

    /**
     * 指令层行为的只读封装。
     */
    public static final class CommandSettings {
        private final boolean allowConsoleOpen;
        private final boolean notifyReloadSuccess;

        public CommandSettings(boolean allowConsoleOpen, boolean notifyReloadSuccess) {
            this.allowConsoleOpen = allowConsoleOpen;
            this.notifyReloadSuccess = notifyReloadSuccess;
        }

        public boolean allowConsoleOpen() {
            return allowConsoleOpen;
        }

        public boolean notifyReloadSuccess() {
            return notifyReloadSuccess;
        }
    }
}
