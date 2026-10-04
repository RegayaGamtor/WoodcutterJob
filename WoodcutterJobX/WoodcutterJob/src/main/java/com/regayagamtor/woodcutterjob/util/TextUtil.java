package com.regayagamtor.woodcutterjob.util;

import com.regayagamtor.woodcutterjob.config.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Locale;

public final class TextUtil {

    private TextUtil() {}

    /** "OAK_LOG" -> "Oak Log". */
    public static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String pretty(Material material) {
        return pretty(material.name());
    }

    /** 272000 ms -> "4m 32s". */
    public static String formatDuration(long millis) {
        long total = (Math.max(0L, millis) + 999L) / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static String percent(double progress) {
        return String.valueOf((int) Math.round(progress));
    }

    /** Builds the progress bar component using the configured characters and colors. */
    public static Component progressBar(double percent, Settings s) {
        int len = s.barLength;
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        int filled = (int) Math.round(clamped / 100.0 * len);
        Component f = Component.text(s.barFilled.repeat(filled), s.barFilledColor);
        Component e = Component.text(s.barEmpty.repeat(Math.max(0, len - filled)), s.barEmptyColor);
        return f.append(e);
    }

    /** Parses a named color (GREEN, DARK_GRAY) or hex (#55FF55); returns {@code def} when invalid. */
    public static TextColor parseColor(String raw, TextColor def) {
        if (raw == null || raw.isBlank()) return def;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("#")) {
            TextColor hex = TextColor.fromHexString(v);
            return hex != null ? hex : def;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(v);
        return named != null ? named : def;
    }
}
