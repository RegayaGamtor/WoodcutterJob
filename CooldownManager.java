package com.regayagamtor.woodcutterjob.cooldown;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.CooldownData;
import com.regayagamtor.woodcutterjob.model.RegionData;

/**
 * Cooldown is a timestamp stored on the region, shared by all players and persisted.
 * There is no scheduler: the cooldown is simply compared with the clock when somebody interacts.
 */
public final class CooldownManager {

    private final WoodcutterJob plugin;

    public CooldownManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public CooldownData get(RegionData region) {
        return new CooldownData(region.cooldownUntil(), System.currentTimeMillis());
    }

    public boolean isActive(RegionData region) {
        return get(region).active();
    }

    /** Starts the normal (success) cooldown for the region's tree type. */
    public void start(RegionData region) {
        startSeconds(region, plugin.settings().cooldownSeconds(region.type()));
    }

    /** Starts the shorter cooldown used after a failed minigame. */
    public void startFail(RegionData region) {
        startSeconds(region, plugin.settings().cooldownFailSeconds);
    }

    private void startSeconds(RegionData region, long seconds) {
        if (seconds <= 0) return;
        region.setCooldownUntil(System.currentTimeMillis() + seconds * 1000L);
        plugin.regions().save(region);
    }

    public void clear(RegionData region) {
        region.setCooldownUntil(0L);
        plugin.regions().save(region);
    }
}
