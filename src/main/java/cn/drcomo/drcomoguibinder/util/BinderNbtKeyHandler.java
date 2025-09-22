package cn.drcomo.drcomoguibinder.util;

import cn.drcomo.corelib.nbt.NbtKeyHandler;

/**
 * 为本插件提供命名空间前缀的 NBT key 处理器。
 */
public final class BinderNbtKeyHandler implements NbtKeyHandler {

  private final String prefix;

  public BinderNbtKeyHandler(String pluginId) {
    this.prefix = pluginId.toLowerCase() + ":";
  }

  @Override
  public boolean isValidKey(String key) {
    return key != null && !key.isBlank();
  }

  @Override
  public String addPrefix(String key) {
    return prefix + key;
  }

  @Override
  public String removePrefix(String key) {
    if (key != null && key.startsWith(prefix)) {
      return key.substring(prefix.length());
    }
    return key;
  }
}
