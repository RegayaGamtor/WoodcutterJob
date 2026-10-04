package com.regayagamtor.woodcutterjob.cooldown;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.CooldownData;
import com.regayagamtor.woodcutterjob.model.TreeData;

/**
 * Cooldown is a timestamp stored on the tree (world + x + y + z), shared by all players and persisted.
 * There is no scheduler: the cooldown is simply compared with the clock when somebody interacts.
 */
public final class CooldownManager {

    private final WoodcutterJob plugin;

    public CooldownManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public CooldownData get(TreeData tree) {
        return new CooldownData(tree.cooldownUntil(), System.currentTimeMillis());
    }

    public boolean isActive(TreeData tree) {
        return get(tree).active();
    }

    /** Starts the normal (success) cooldown for the tree's type. */
    public void start(TreeData tree) {
        startSeconds(tree, plugin.settings().cooldownSeconds(tree.type()));
    }

    /** Starts the shorter cooldown used after a failed minigame. */
    public void startFail(TreeData tree) {
        startSeconds(tree, plugin.settings().cooldownFailSeconds);
    }

    private void startSeconds(TreeData tree, long seconds) {
        if (seconds <= 0) return;
        tree.setCooldownUntil(System.currentTimeMillis() + seconds * 1000L);
        plugin.trees().save(tree);
    }

    public void clear(TreeData tree) {
        tree.setCooldownUntil(0L);
        plugin.trees().save(tree);
    }
}
