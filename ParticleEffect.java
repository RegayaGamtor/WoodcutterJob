package com.regayagamtor.woodcutterjob.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** A configurable particle. Only particles without data or with BlockData are supported. */
public final class ParticleEffect {

    // Old (pre-1.20.5) names are mapped to the current ones so old configs keep working.
    private static final Map<String, String> ALIASES = Map.of(
            "VILLAGER_HAPPY", "HAPPY_VILLAGER",
            "VILLAGER_ANGRY", "ANGRY_VILLAGER",
            "BLOCK_CRACK", "BLOCK",
            "BLOCK_DUST", "BLOCK",
            "SMOKE_NORMAL", "SMOKE",
            "CRIT_MAGIC", "ENCHANTED_HIT");

    private final boolean enabled;
    private final Particle particle;
    private final int count;
    private final double spread;
    private final double speed;
    private final boolean needsBlockData;

    private ParticleEffect(boolean enabled, Particle particle, int count, double spread, double speed) {
        this.enabled = enabled && particle != null;
        this.particle = particle;
        this.count = count;
        this.spread = spread;
        this.speed = speed;
        this.needsBlockData = particle != null && particle.getDataType() == BlockData.class;
    }

    public static ParticleEffect parse(ConfigurationSection root, String path, Logger log,
                                       String defParticle, int defCount, double defSpread, double defSpeed) {
        boolean enabled = root.getBoolean(path + ".enabled", true);
        String name = root.getString(path + ".particle", defParticle);
        int count = Math.max(1, Math.min(200, root.getInt(path + ".count", defCount)));
        double spread = Math.max(0.0, root.getDouble(path + ".spread", defSpread));
        double speed = Math.max(0.0, root.getDouble(path + ".speed", defSpeed));
        Particle particle = resolve(name);
        if (particle == null && enabled) {
            log.warning("[config] " + path + ".particle '" + name + "' is not a valid particle; using '" + defParticle + "'.");
            particle = resolve(defParticle);
        }
        if (particle != null) {
            Class<?> dataType = particle.getDataType();
            if (dataType != Void.class && dataType != BlockData.class) {
                log.warning("[config] " + path + ".particle '" + name + "' needs extra data and is not supported; effect disabled.");
                particle = null;
            }
        }
        return new ParticleEffect(enabled, particle, count, spread, speed);
    }

    public static Particle resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        v = ALIASES.getOrDefault(v, v);
        try {
            return Particle.valueOf(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Spawns the particle for all nearby players. {@code blockData} is only used by BLOCK particles. */
    public void spawn(World world, Location location, BlockData blockData) {
        if (!enabled || world == null || location == null) return;
        if (needsBlockData) {
            BlockData data = blockData != null ? blockData : Bukkit.createBlockData(Material.OAK_LOG);
            world.spawnParticle(particle, location, count, spread, spread, spread, speed, data);
        } else {
            world.spawnParticle(particle, location, count, spread, spread, spread, speed);
        }
    }
}
