package com.regayagamtor.woodcutterjob.config;

import com.regayagamtor.woodcutterjob.model.ClickMode;
import com.regayagamtor.woodcutterjob.model.CloseBehavior;
import com.regayagamtor.woodcutterjob.model.MissMode;
import com.regayagamtor.woodcutterjob.model.RewardData;
import com.regayagamtor.woodcutterjob.model.TreeType;
import com.regayagamtor.woodcutterjob.util.ParticleEffect;
import com.regayagamtor.woodcutterjob.util.SoundEffect;
import com.regayagamtor.woodcutterjob.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Typed, validated snapshot of config.yml. Invalid values fall back to defaults and log a warning.
 * A new instance is created on every reload; managers always read {@code plugin.settings()}.
 */
public final class Settings {

    private static final List<Material> AXES = List.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);

    // general
    public final boolean debug;
    public final String storageType;
    public final String sqliteFile;
    public final int autosaveSeconds;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;
    public final Set<TreeType> enabledTreeTypes;
    public final Set<Material> enabledAxes;

    // regions
    public final double regionRange;
    public final ClickMode regionClick;

    // auto chopping (after the minigame)
    public final int chopHits;
    public final int chopIntervalTicks;
    public final boolean chopBossbar;
    public final BossBar.Color chopBossbarColor;
    public final BossBar.Overlay chopBossbarOverlay;
    public final boolean chopActionbar;

    // minigame
    public final String guiTitle;
    public final int rows;
    public final int requiredSuccesses;
    public final int targetSlots;
    public final double maxDistance;
    public final long minClickIntervalMs;
    public final boolean differentPosition;
    public final boolean movingEnabled;
    public final int movingSpeedTicks;
    public final MissMode missMode;
    public final double penaltyPercent;
    public final boolean missRandomize;
    public final CloseBehavior closeBehavior;
    public final int resumeTimeoutSeconds;

    // progress bar
    public final int barLength;
    public final String barFilled;
    public final String barEmpty;
    public final TextColor barFilledColor;
    public final TextColor barEmptyColor;

    // gui look
    public final Material guiFiller;
    public final Material guiRed;
    public final Material guiGreen;
    public final boolean useHeldAxeIcon;

    // cooldown
    public final long cooldownDefaultSeconds;
    public final long cooldownFailSeconds;
    private final Map<TreeType, Long> cooldownPerType;
    public final boolean cooldownShowMessage;
    public final boolean cooldownShowTime;

    // job
    private final Map<TreeType, Integer> xp;
    private final NavigableMap<Integer, Long> levels;

    // rewards
    private final Map<TreeType, List<RewardData>> rewards;
    public final boolean moneyEnabled;
    private final Map<TreeType, Double> money;

    // effects
    public final SoundEffect soundSuccess;
    public final SoundEffect soundWoodHit;
    public final SoundEffect soundFail;
    public final SoundEffect soundComplete;
    public final SoundEffect soundLevelUp;
    public final SoundEffect soundDenied;
    public final ParticleEffect particleHitBlock;
    public final ParticleEffect particleHitCrit;
    public final ParticleEffect particleComplete;

    public Settings(ConfigurationSection c, Logger log) {
        debug = c.getBoolean("plugin.debug", false);

        String st = c.getString("storage.type", "SQLITE").trim().toUpperCase(Locale.ROOT);
        if (!st.equals("SQLITE") && !st.equals("YAML")) {
            log.warning("[config] storage.type '" + st + "' is invalid (SQLITE or YAML); using SQLITE.");
            st = "SQLITE";
        }
        storageType = st;
        String file = c.getString("storage.sqlite-file", "database.db");
        file = new java.io.File(file == null || file.isBlank() ? "database.db" : file).getName();
        sqliteFile = file;
        autosaveSeconds = clamp(c, "storage.autosave-interval-seconds", 300, 30, 86400, log);

        enabledWorlds = lowerSet(c.getStringList("worlds.enabled"));
        disabledWorlds = lowerSet(c.getStringList("worlds.disabled"));

        Set<TreeType> types = EnumSet.noneOf(TreeType.class);
        for (TreeType t : TreeType.values()) {
            if (c.getBoolean("tree-types." + t.name() + ".enabled", true)) types.add(t);
        }
        enabledTreeTypes = Collections.unmodifiableSet(types);

        Set<Material> axes = EnumSet.noneOf(Material.class);
        for (Material m : AXES) {
            if (c.getBoolean("axes." + m.name() + ".enabled", true)) axes.add(m);
        }
        enabledAxes = Collections.unmodifiableSet(axes);

        regionRange = Math.max(1.0, Math.min(6.0, c.getDouble("region.interact-range", 4.5)));
        regionClick = parseEnum(c, "region.click", ClickMode.class, ClickMode.RIGHT, log);

        chopHits = clamp(c, "chop.hits-required", 8, 1, 200, log);
        chopIntervalTicks = clamp(c, "chop.interval-ticks", 10, 2, 100, log);
        chopBossbar = c.getBoolean("chop.show-bossbar", true);
        chopBossbarColor = parseEnum(c, "chop.bossbar-color", BossBar.Color.class, BossBar.Color.GREEN, log);
        chopBossbarOverlay = parseEnum(c, "chop.bossbar-overlay", BossBar.Overlay.class, BossBar.Overlay.PROGRESS, log);
        chopActionbar = c.getBoolean("chop.show-actionbar", true);

        guiTitle = c.getString("minigame.title", "<dark_green>🌲 Woodcutting");
        rows = clamp(c, "minigame.rows", 3, 3, 6, log);
        requiredSuccesses = clamp(c, "minigame.required-successes", 5, 1, 100, log);
        targetSlots = clamp(c, "minigame.target-slots", 7, 3, 9, log);
        maxDistance = Math.max(0.0, c.getDouble("minigame.max-distance", 8.0));
        minClickIntervalMs = clamp(c, "minigame.min-click-interval-ms", 120, 0, 2000, log);
        differentPosition = c.getBoolean("minigame.different-position-each-round", true);
        movingEnabled = c.getBoolean("minigame.moving-target.enabled", false);
        movingSpeedTicks = clamp(c, "minigame.moving-target.speed-ticks", 3, 1, 40, log);
        missMode = parseEnum(c, "minigame.miss.mode", MissMode.class, MissMode.NO_PROGRESS, log);
        penaltyPercent = Math.max(0.0, Math.min(100.0, c.getDouble("minigame.miss.penalty-percent", 5.0)));
        missRandomize = c.getBoolean("minigame.miss.randomize-target", true);
        closeBehavior = parseEnum(c, "minigame.close-behavior", CloseBehavior.class, CloseBehavior.CANCEL, log);
        resumeTimeoutSeconds = clamp(c, "minigame.resume-timeout-seconds", 60, 5, 3600, log);

        barLength = clamp(c, "progress-bar.length", 20, 5, 50, log);
        String filled = c.getString("progress-bar.filled", "█");
        String empty = c.getString("progress-bar.empty", "░");
        barFilled = filled == null || filled.isEmpty() ? "█" : filled;
        barEmpty = empty == null || empty.isEmpty() ? "░" : empty;
        barFilledColor = TextUtil.parseColor(c.getString("progress-bar.filled-color", "GREEN"), NamedTextColor.GREEN);
        barEmptyColor = TextUtil.parseColor(c.getString("progress-bar.empty-color", "DARK_GRAY"), NamedTextColor.DARK_GRAY);

        guiFiller = material(c, "gui.filler-material", Material.GRAY_STAINED_GLASS_PANE, log);
        guiRed = material(c, "gui.red-material", Material.RED_STAINED_GLASS_PANE, log);
        guiGreen = material(c, "gui.green-material", Material.LIME_STAINED_GLASS_PANE, log);
        useHeldAxeIcon = c.getBoolean("gui.use-held-axe-icon", true);

        cooldownDefaultSeconds = clamp(c, "cooldown.default-seconds", 300, 0, 31_536_000, log);
        cooldownFailSeconds = clamp(c, "cooldown.fail-seconds", 60, 0, 31_536_000, log);
        Map<TreeType, Long> perType = new EnumMap<>(TreeType.class);
        for (TreeType t : TreeType.values()) {
            String path = "cooldown.per-type." + t.name();
            if (c.isSet(path)) perType.put(t, (long) clamp(c, path, (int) cooldownDefaultSeconds, 0, 31_536_000, log));
        }
        cooldownPerType = perType;
        cooldownShowMessage = c.getBoolean("cooldown.show-message", true);
        cooldownShowTime = c.getBoolean("cooldown.show-time", true);

        Map<TreeType, Integer> xpMap = new EnumMap<>(TreeType.class);
        for (TreeType t : TreeType.values()) {
            xpMap.put(t, clamp(c, "job.xp." + t.name(), 25, 0, 1_000_000, log));
        }
        xp = xpMap;
        levels = parseLevels(c, log);

        Map<TreeType, List<RewardData>> rewardMap = new EnumMap<>(TreeType.class);
        for (TreeType t : TreeType.values()) {
            List<RewardData> list = new ArrayList<>();
            for (Map<?, ?> entry : c.getMapList("rewards." + t.name() + ".items")) {
                RewardData data = RewardData.fromMap(entry, log, "rewards." + t.name());
                if (data != null) list.add(data);
            }
            if (list.isEmpty()) {
                log.warning("[config] rewards." + t.name() + " has no valid items; using 8x " + t.defaultLog().name() + ".");
                list.add(new RewardData(t.defaultLog(), 8, 8, 100.0));
            }
            rewardMap.put(t, Collections.unmodifiableList(list));
        }
        rewards = rewardMap;

        moneyEnabled = c.getBoolean("reward-money.enabled", false);
        Map<TreeType, Double> moneyMap = new EnumMap<>(TreeType.class);
        ConfigurationSection ms = c.getConfigurationSection("reward-money");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                TreeType t = TreeType.parse(key);
                if (t != null) moneyMap.put(t, Math.max(0.0, ms.getDouble(key, 0.0)));
            }
        }
        money = moneyMap;

        soundSuccess = SoundEffect.parse(c, "sounds.success", log, "ENTITY_PLAYER_ATTACK_STRONG", 1.0, 1.2);
        soundWoodHit = SoundEffect.parse(c, "sounds.wood-hit", log, "BLOCK_WOOD_HIT", 1.0, 0.9);
        soundFail = SoundEffect.parse(c, "sounds.fail", log, "BLOCK_NOTE_BLOCK_BASS", 1.0, 0.8);
        soundComplete = SoundEffect.parse(c, "sounds.complete", log, "ENTITY_PLAYER_LEVELUP", 1.0, 1.0);
        soundLevelUp = SoundEffect.parse(c, "sounds.level-up", log, "UI_TOAST_CHALLENGE_COMPLETE", 1.0, 1.0);
        soundDenied = SoundEffect.parse(c, "sounds.cooldown-denied", log, "ENTITY_VILLAGER_NO", 0.8, 1.0);
        particleHitBlock = ParticleEffect.parse(c, "particles.hit-block", log, "BLOCK", 18, 0.25, 0.05);
        particleHitCrit = ParticleEffect.parse(c, "particles.hit-crit", log, "CRIT", 8, 0.3, 0.1);
        particleComplete = ParticleEffect.parse(c, "particles.complete", log, "HAPPY_VILLAGER", 25, 0.6, 0.0);
    }

    // ------------------------------------------------------------------ accessors

    public boolean isWorldEnabled(String worldName) {
        String w = worldName.toLowerCase(Locale.ROOT);
        if (disabledWorlds.contains(w)) return false;
        return enabledWorlds.isEmpty() || enabledWorlds.contains(w);
    }

    public long cooldownSeconds(TreeType type) {
        return cooldownPerType.getOrDefault(type, cooldownDefaultSeconds);
    }

    public int xpFor(TreeType type) {
        return xp.getOrDefault(type, 0);
    }

    public NavigableMap<Integer, Long> levels() {
        return levels;
    }

    public List<RewardData> rewardsFor(TreeType type) {
        return rewards.getOrDefault(type, List.of());
    }

    public double moneyFor(TreeType type) {
        return money.getOrDefault(type, 0.0);
    }

    // ------------------------------------------------------------------ helpers

    private static NavigableMap<Integer, Long> parseLevels(ConfigurationSection c, Logger log) {
        TreeMap<Integer, Long> map = new TreeMap<>();
        ConfigurationSection sec = c.getConfigurationSection("levels");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key.trim());
                    long needed = Math.max(0L, sec.getLong(key, 0L));
                    if (level >= 1) map.put(level, needed);
                } catch (NumberFormatException ex) {
                    log.warning("[config] levels." + key + " is not a valid level number and was skipped.");
                }
            }
        }
        if (map.isEmpty()) {
            log.warning("[config] 'levels' is empty; using default levels.");
            map.put(1, 0L);
            map.put(2, 100L);
            map.put(3, 250L);
            map.put(4, 500L);
            map.put(5, 1000L);
        }
        map.put(1, 0L);
        return map;
    }

    private static Set<String> lowerSet(List<String> list) {
        Set<String> set = new HashSet<>();
        for (String s : list) {
            if (s != null && !s.isBlank()) set.add(s.trim().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(set);
    }

    private static int clamp(ConfigurationSection c, String path, int def, int min, int max, Logger log) {
        int v = c.getInt(path, def);
        if (v < min || v > max) {
            int fixed = Math.max(min, Math.min(max, v));
            log.warning("[config] " + path + " = " + v + " is out of range [" + min + ", " + max + "]; using " + fixed + ".");
            return fixed;
        }
        return v;
    }

    private static <E extends Enum<E>> E parseEnum(ConfigurationSection c, String path, Class<E> type, E def, Logger log) {
        String raw = c.getString(path);
        if (raw == null) return def;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("[config] " + path + " '" + raw + "' is invalid; using " + def.name() + ".");
            return def;
        }
    }

    private static Material material(ConfigurationSection c, String path, Material def, Logger log) {
        String raw = c.getString(path);
        if (raw == null) return def;
        Material m = Material.matchMaterial(raw.trim());
        if (m == null || !m.isItem() || m.isAir()) {
            log.warning("[config] " + path + " '" + raw + "' is not a valid item; using " + def.name() + ".");
            return def;
        }
        return m;
    }
}
