package com.regayagamtor.woodcutterjob.job;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import com.regayagamtor.woodcutterjob.model.TreeType;

import java.util.Map;

/** Woodcutter XP and levels. */
public final class JobManager {

    /** Result of adding XP. */
    public record XpResult(int xpGained, int newLevel, boolean leveledUp) {}

    private final WoodcutterJob plugin;

    public JobManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public XpResult addXp(PlayerJobData data, TreeType type) {
        int gained = plugin.settings().xpFor(type);
        data.addXp(gained);
        int oldLevel = data.level();
        int newLevel = Math.max(oldLevel, levelFor(data.xp()));
        data.setLevel(newLevel);
        return new XpResult(gained, newLevel, newLevel > oldLevel);
    }

    /** Highest level whose XP threshold has been reached. */
    public int levelFor(long xp) {
        int level = 1;
        for (Map.Entry<Integer, Long> e : plugin.settings().levels().entrySet()) {
            if (xp >= e.getValue()) level = Math.max(level, e.getKey());
        }
        return level;
    }

    /** @return XP still needed for the next level, or -1 at max level. */
    public long xpToNext(PlayerJobData data) {
        Integer next = plugin.settings().levels().higherKey(data.level());
        if (next == null) return -1L;
        return Math.max(0L, plugin.settings().levels().get(next) - data.xp());
    }
}
