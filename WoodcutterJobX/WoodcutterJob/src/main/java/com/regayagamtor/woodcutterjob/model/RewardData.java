package com.regayagamtor.woodcutterjob.model;

import org.bukkit.Material;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** One reward entry: material, amount range and drop chance (0-100). */
public record RewardData(Material material, int min, int max, double chance) {

    public int rollAmount() {
        return min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public boolean rollChance() {
        return chance >= 100.0 || ThreadLocalRandom.current().nextDouble() * 100.0 < chance;
    }

    /** Parses a config map. Returns null (after logging a warning) if the entry is invalid. */
    public static RewardData fromMap(Map<?, ?> map, Logger log, String context) {
        Object matObj = map.get("material");
        if (matObj == null) {
            log.warning("[config] " + context + ": reward entry without 'material' was skipped.");
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(matObj));
        if (material == null || !material.isItem() || material.isAir()) {
            log.warning("[config] " + context + ": invalid reward material '" + matObj + "' was skipped.");
            return null;
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
        return new RewardData(material, min, max, chance);
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
