package cn.drcomo.drcomoguibinder.config.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表示用于渲染 GUI 物品的模板配置。模板字段均为可选，渲染时将自动与默认值合并。
 */
public final class ItemTemplate {

  private final String material;
  private final int amount;
  private final String name;
  private final List<String> lore;
  private final Integer customModelData;
  private final List<String> flags;
  private final boolean glow;
  private final Map<String, Object> nbt;
  private final String skullTexture;

  private ItemTemplate(Builder builder) {
    this.material = builder.material;
    this.amount = builder.amount;
    this.name = builder.name;
    this.lore = builder.lore == null ? Collections.emptyList() : List.copyOf(builder.lore);
    this.customModelData = builder.customModelData;
    this.flags = builder.flags == null ? Collections.emptyList() : List.copyOf(builder.flags);
    this.glow = builder.glow;
    this.nbt = builder.nbt == null ? Collections.emptyMap() : Map.copyOf(builder.nbt);
    this.skullTexture = builder.skullTexture;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .material(material)
        .amount(amount)
        .name(name)
        .lore(new ArrayList<>(lore))
        .customModelData(customModelData)
        .flags(new ArrayList<>(flags))
        .glow(glow)
        .nbt(new LinkedHashMap<>(nbt))
        .skullTexture(skullTexture);
  }

  public ItemTemplate merge(ItemTemplate override) {
    if (override == null) {
      return this;
    }
    Builder merged = this.toBuilder();
    if (override.material != null) {
      merged.material(override.material);
    }
    if (override.amount > 0) {
      merged.amount(override.amount);
    }
    if (override.name != null) {
      merged.name(override.name);
    }
    if (!override.lore.isEmpty()) {
      merged.lore(new ArrayList<>(override.lore));
    }
    if (override.customModelData != null) {
      merged.customModelData(override.customModelData);
    }
    if (!override.flags.isEmpty()) {
      merged.flags(new ArrayList<>(override.flags));
    }
    if (override.nbt != null && !override.nbt.isEmpty()) {
      merged.nbt(new LinkedHashMap<>(override.nbt));
    }
    if (override.skullTexture != null) {
      merged.skullTexture(override.skullTexture);
    }
    merged.glow(override.glow || merged.glow);
    return merged.build();
  }

  public String getMaterial() {
    return material;
  }

  public int getAmount() {
    return amount;
  }

  public String getName() {
    return name;
  }

  public List<String> getLore() {
    return lore;
  }

  public Integer getCustomModelData() {
    return customModelData;
  }

  public List<String> getFlags() {
    return flags;
  }

  public boolean isGlow() {
    return glow;
  }

  public Map<String, Object> getNbt() {
    return nbt;
  }

  public String getSkullTexture() {
    return skullTexture;
  }

  public boolean isEmpty() {
    return material == null && name == null && lore.isEmpty() && customModelData == null
        && flags.isEmpty() && !glow && (nbt == null || nbt.isEmpty()) && skullTexture == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ItemTemplate that)) {
      return false;
    }
    return amount == that.amount && glow == that.glow && Objects.equals(material, that.material)
        && Objects.equals(name, that.name) && Objects.equals(lore, that.lore)
        && Objects.equals(customModelData, that.customModelData) && Objects.equals(flags, that.flags)
        && Objects.equals(nbt, that.nbt) && Objects.equals(skullTexture, that.skullTexture);
  }

  @Override
  public int hashCode() {
    return Objects.hash(material, amount, name, lore, customModelData, flags, glow, nbt, skullTexture);
  }

  public static final class Builder {
    private String material;
    private int amount = 1;
    private String name;
    private List<String> lore;
    private Integer customModelData;
    private List<String> flags;
    private boolean glow;
    private Map<String, Object> nbt;
    private String skullTexture;

    public Builder material(String material) {
      this.material = material;
      return this;
    }

    public Builder amount(int amount) {
      if (amount > 0) {
        this.amount = amount;
      }
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder lore(List<String> lore) {
      this.lore = lore;
      return this;
    }

    public Builder addLoreLine(String line) {
      if (this.lore == null) {
        this.lore = new ArrayList<>();
      }
      this.lore.add(line);
      return this;
    }

    public Builder customModelData(Integer customModelData) {
      this.customModelData = customModelData;
      return this;
    }

    public Builder flags(List<String> flags) {
      this.flags = flags;
      return this;
    }

    public Builder addFlag(String flag) {
      if (this.flags == null) {
        this.flags = new ArrayList<>();
      }
      this.flags.add(flag);
      return this;
    }

    public Builder glow(boolean glow) {
      this.glow = glow;
      return this;
    }

    public Builder nbt(Map<String, Object> nbt) {
      this.nbt = nbt;
      return this;
    }

    public Builder putNbt(String key, Object value) {
      if (this.nbt == null) {
        this.nbt = new LinkedHashMap<>();
      }
      this.nbt.put(key, value);
      return this;
    }

    public Builder skullTexture(String skullTexture) {
      this.skullTexture = skullTexture;
      return this;
    }

    public ItemTemplate build() {
      return new ItemTemplate(this);
    }
  }
}
