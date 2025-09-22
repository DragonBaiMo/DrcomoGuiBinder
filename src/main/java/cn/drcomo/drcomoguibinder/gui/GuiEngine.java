package cn.drcomo.drcomoguibinder.gui;

import cn.drcomo.corelib.gui.GuiActionDispatcher;
import cn.drcomo.corelib.gui.GUISessionManager;
import cn.drcomo.corelib.gui.GuiManager;
import cn.drcomo.corelib.gui.interfaces.GUICreator;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.sound.SoundManager;
import cn.drcomo.corelib.util.ColorUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.drcomoguibinder.config.GuiDefinition;
import cn.drcomo.drcomoguibinder.config.GuiItemDefinition;
import cn.drcomo.drcomoguibinder.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一负责 GUI 渲染、会话管理与点击回调注册。
 */
public final class GuiEngine {

    private final DebugUtil logger;
    private final MessageService messageService;
    private final PlaceholderAPIUtil placeholderUtil;
    private final SoundManager soundManager;
    private final GUISessionManager sessionManager;
    private final GuiActionDispatcher dispatcher;
    private final GuiManager guiManager;
    private final GuiActionExecutor actionExecutor;
    private PluginConfig pluginConfig;

    private final Map<String, GuiDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveSession> sessionsByPlayer = new ConcurrentHashMap<>();
    private final Map<String, ActiveSession> sessionsById = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger();
    private final Set<String> missingSoundWarnings = ConcurrentHashMap.newKeySet();

    public GuiEngine(DebugUtil logger,
                     MessageService messageService,
                     PlaceholderAPIUtil placeholderUtil,
                     SoundManager soundManager,
                     GUISessionManager sessionManager,
                     GuiActionDispatcher dispatcher,
                     GuiManager guiManager,
                     PluginConfig pluginConfig) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.placeholderUtil = Objects.requireNonNull(placeholderUtil, "placeholderUtil");
        this.soundManager = Objects.requireNonNull(soundManager, "soundManager");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.guiManager = Objects.requireNonNull(guiManager, "guiManager");
        this.pluginConfig = Objects.requireNonNull(pluginConfig, "pluginConfig");
        this.actionExecutor = new GuiActionExecutor(logger, messageService, placeholderUtil, soundManager, this);
    }

    public void updatePluginConfig(PluginConfig newConfig) {
        this.pluginConfig = Objects.requireNonNull(newConfig, "pluginConfig");
    }

    public void updateDefinitions(Map<String, GuiDefinition> newDefinitions) {
        definitions.clear();
        definitions.putAll(newDefinitions);
    }

    public Map<String, GuiDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public boolean openMenu(Player player, String menuId) {
        return openMenu(player, menuId, Collections.emptyMap());
    }

    public boolean openMenu(Player player, String menuId, Map<String, String> externalPlaceholders) {
        if (player == null) {
            return false;
        }
        GuiDefinition definition = definitions.get(menuId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            return false;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("uuid", player.getUniqueId().toString());
        placeholders.put("menu", definition.getId());
        if (externalPlaceholders != null) {
            placeholders.putAll(externalPlaceholders);
        }

        closeForPlayer(player, false);

        String sessionId = "gui-" + definition.getId() + "-" + player.getUniqueId() + "-" + sessionCounter.incrementAndGet();
        GUICreator creator = viewer -> buildInventory(viewer, definition, placeholders);
        boolean opened = sessionManager.openSession(player, sessionId, creator);
        if (!opened) {
            logger.warn("无法为玩家 " + player.getName() + " 打开菜单 " + definition.getId() + "。");
            return false;
        }

        ActiveSession session = new ActiveSession(sessionId, player.getUniqueId(), definition, placeholders);
        sessionsByPlayer.put(player.getUniqueId(), session);
        sessionsById.put(sessionId, session);

        registerClickActions(player, session, placeholders);
        playSound(player, resolveOpenSound(definition));
        return true;
    }

    public void closeForPlayer(Player player, boolean playSound) {
        if (player == null) {
            return;
        }
        ActiveSession session = sessionsByPlayer.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        sessionsById.remove(session.getSessionId());
        dispatcher.unregister(session.getSessionId());
        sessionManager.closeSession(player);
        if (playSound) {
            playSound(player, resolveCloseSound(session.getDefinition()));
        }
    }

    public void handleInventoryClose(Player player) {
        closeForPlayer(player, true);
    }

    public Optional<ActiveSession> getSession(Player player) {
        return Optional.ofNullable(player).map(Player::getUniqueId).map(sessionsByPlayer::get);
    }

    public int getLoadedMenuCount() {
        return definitions.size();
    }

    private Inventory buildInventory(Player player, GuiDefinition definition, Map<String, String> placeholders) {
        String title = placeholderUtil.parse(player, definition.getTitle(), placeholders);
        Inventory inventory = Bukkit.createInventory(player, definition.getSize(), ColorUtil.translateColors(title));
        for (GuiItemDefinition item : definition.getItems()) {
            ItemStack stack = createItemStack(player, item, placeholders);
            for (Integer slot : item.getSlots()) {
                if (slot < 0 || slot >= definition.getSize()) {
                    continue;
                }
                inventory.setItem(slot, stack.clone());
            }
        }
        return inventory;
    }

    private ItemStack createItemStack(Player player, GuiItemDefinition item, Map<String, String> placeholders) {
        Material material = item.getMaterial();
        if (material == null) {
            material = Material.BARRIER;
        }
        ItemStack stack = new ItemStack(material, item.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (item.getDisplayName() != null) {
                String name = placeholderUtil.parse(player, item.getDisplayName(), placeholders);
                meta.setDisplayName(ColorUtil.translateColors(name));
            }
            if (!item.getLore().isEmpty()) {
                List<String> lore = new java.util.ArrayList<>();
                for (String line : item.getLore()) {
                    String parsed = placeholderUtil.parse(player, line, placeholders);
                    lore.add(ColorUtil.translateColors(parsed));
                }
                meta.setLore(lore);
            }
            if (item.getCustomModelData() != null) {
                meta.setCustomModelData(item.getCustomModelData());
            }
            if (item.isGlow()) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                stack.setItemMeta(meta);
                stack.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE, 1);
                stack = hideGlow(stack);
            } else {
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private ItemStack hideGlow(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void registerClickActions(Player player,
                                      ActiveSession session,
                                      Map<String, String> placeholders) {
        GuiDefinition definition = session.getDefinition();
        for (GuiItemDefinition item : definition.getItems()) {
            for (Integer slot : item.getSlots()) {
                final int slotIndex = slot;
                dispatcher.registerForSlot(session.getSessionId(), slotIndex, ctx -> {
                    Map<String, String> runtime = new HashMap<>(placeholders);
                    runtime.put("slot", String.valueOf(slotIndex));
                    actionExecutor.execute(player, definition, item, item.getActions(), runtime);
                });
            }
        }
    }

    private void playSound(Player player, String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) {
            return;
        }
        if (!soundManager.hasSound(soundKey)) {
            if (missingSoundWarnings.add(soundKey)) {
                logger.warn("音效键 " + soundKey + " 未在 sounds.yml 中定义。");
            }
            messageService.send(player, "gui.open-sound-missing", Map.of("id", getSession(player).map(s -> s.getDefinition().getId()).orElse("unknown"), "sound", soundKey));
            return;
        }
        soundManager.playSound(player, soundKey);
    }

    private String resolveOpenSound(GuiDefinition definition) {
        String key = definition.getOpenSound();
        if (key == null || key.isEmpty()) {
            key = pluginConfig.getDefaultOpenSound();
        }
        return key;
    }

    private String resolveCloseSound(GuiDefinition definition) {
        String key = definition.getCloseSound();
        if (key == null || key.isEmpty()) {
            key = pluginConfig.getDefaultCloseSound();
        }
        return key;
    }
}
