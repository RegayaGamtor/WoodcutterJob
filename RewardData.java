package com.regayagamtor.woodcutterjob.model;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * One reward entry: a material (or a full custom item copied from a hand), amount range and drop chance (0-100).
 * {@code template} is null for plain materials; otherwise it keeps name, lore, enchantments, model data, etc.
 */
public record RewardData(Material material, int min, int max, double chance, ItemStack template) {

    public RewardData(Material material, int min, int max, double chance) {
        this(material, min, max, chance, null);
    }

    public boolean isCustom() {
        return template != null;
    }

    public int rollAmount() {
        return min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public boolean rollChance() {
        return chance >= 100.0 || ThreadLocalRandom.current().nextDouble() * 100.0 < chance;
    }

    /** A fresh stack of this reward with the given amount. */
    public ItemStack createStack(int amount) {
        ItemStack stack = template != null ? template.clone() : new ItemStack(material);
        stack.setAmount(Math.max(1, amount));
        return stack;
    }

    /** Human readable name: the custom display name, or "Oak Log". */
    public String label() {
        if (template != null && template.hasItemMeta()) {
            ItemMeta meta = template.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
                return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public String amountText() {
        return min == max ? String.valueOf(min) : min + "-" + max;
    }

    /** Config representation (written back to config.yml by the reward commands). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", material.name());
        if (min == max) {
            m.put("amount", min);
        } else {
            m.put("min", min);
            m.put("max", max);
        }
        m.put("chance", chance);
        if (template != null) {
            m.put("item", Base64.getEncoder().encodeToString(template.serializeAsBytes()));
        }
        return m;
    }

    /** Parses a config map. Returns null (after logging a warning) if the entry is invalid. */
    public static RewardData fromMap(Map<?, ?> map, Logger log, String context) {
        ItemStack template = null;
        Object itemObj = map.get("item");
        if (itemObj != null) {
            try {
                template = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(itemObj)));
            } catch (RuntimeException ex) {
                log.warning("[config] " + context + ": custom item data is corrupt and the entry was skipped.");
                return null;
            }
        }

        Material material;
        if (template != null) {
            material = template.getType();
        } else {
            Object matObj = map.get("material");
            if (matObj == null) {
                log.warning("[config] " + context + ": reward entry without 'material' was skipped.");
                return null;
            }
            material = Material.matchMaterial(String.valueOf(matObj));
            if (material == null || !material.isItem() || material.isAir()) {
                log.warning("[config] " + context + ": invalid reward material '" + matObj + "' was skipped.");
                return null;
            }
        }
        int amount = toInt(map.get("amount"), -1);
        int min;
        int max;
        if (amount > 0) {
            min = amount;
            max = amount;
        } else {
            min = toInt(map.get("min"), 1);
            max = toInt(map.get("max"), min);
        }
        min = Math.max(1, min);
        max = Math.max(min, max);
        double chance = toDouble(map.get("chance"), 100.0);
        chance = Math.max(0.0, Math.min(100.0, chance));
        if (template != null) template.setAmount(1);
        return new RewardData(material, min, max, chance, template);
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }
}
