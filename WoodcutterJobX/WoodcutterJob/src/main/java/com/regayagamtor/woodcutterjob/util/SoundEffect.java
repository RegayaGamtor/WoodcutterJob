package com.regayagamtor.woodcutterjob.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.logging.Logger;

/** A configurable sound. Resolved through the Sound registry (Sound is registry-backed in 1.21.x). */
public final class SoundEffect {

    private final boolean enabled;
    private final Sound sound;
    private final float volume;
    private final float pitch;

    private SoundEffect(boolean enabled, Sound sound, float volume, float pitch) {
        this.enabled = enabled && sound != null;
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static SoundEffect parse(ConfigurationSection root, String path, Logger log,
                                    String defSound, double defVolume, double defPitch) {
        boolean enabled = root.getBoolean(path + ".enabled", true);
        String name = root.getString(path + ".sound", defSound);
        float volume = (float) Math.max(0.0, root.getDouble(path + ".volume", defVolume));
        float pitch = (float) Math.max(0.0, Math.min(2.0, root.getDouble(path + ".pitch", defPitch)));
        Sound sound = resolve(name);
        if (sound == null && enabled) {
            log.warning("[config] " + path + ".sound '" + name + "' is not a valid sound; trying default '" + defSound + "'.");
            sound = resolve(defSound);
        }
        return new SoundEffect(enabled, sound, volume, pitch);
    }

    /** Accepts ENTITY_PLAYER_LEVELUP, entity.player.levelup or minecraft:entity.player.levelup. */
    public static Sound resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.contains(":") || v.contains(".")) {
            NamespacedKey key = NamespacedKey.fromString(v);
            return key == null ? null : Registry.SOUNDS.get(key);
        }
        String[] tokens = v.split("_");
        if (tokens.length > 9) return null;
        return search(tokens, 1, new StringBuilder(tokens[0]));
    }

    // Underscores in enum-style names may be '.' or '_' in the real key (block.note_block.bass): try all combinations.
    private static Sound search(String[] tokens, int index, StringBuilder current) {
        if (index == tokens.length) {
            NamespacedKey key = NamespacedKey.fromString("minecraft:" + current);
            return key == null ? null : Registry.SOUNDS.get(key);
        }
        int len = current.length();
        for (char sep : new char[] {'.', '_'}) {
            current.setLength(len);
            current.append(sep).append(tokens[index]);
            Sound found = search(tokens, index + 1, current);
            if (found != null) return found;
        }
        current.setLength(len);
        return null;
    }

    public void play(Player player) {
        if (!enabled || player == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public void playAt(Location location) {
        if (!enabled || location == null || location.getWorld() == null) return;
        location.getWorld().playSound(location, sound, volume, pitch);
    }
}
