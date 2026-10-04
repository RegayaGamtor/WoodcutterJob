package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pending admin actions (set/remove/info): the next block click by that admin performs the action. */
public final class AdminModeManager {

    public enum Mode { SET, REMOVE, INFO }

    private record Pending(Mode mode, long expiresAt) {}

    private final WoodcutterJob plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public AdminModeManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public void set(UUID id, Mode mode) {
        pending.put(id, new Pending(mode, expiry()));
    }

    /** @return the active mode, or null if none/expired. */
    public Mode peek(UUID id) {
        Pending p = pending.get(id);
        if (p == null) return null;
        if (p.expiresAt() < System.currentTimeMillis()) {
            pending.remove(id, p);
            return null;
        }
        return p.mode();
    }

    public void refresh(UUID id) {
        pending.computeIfPresent(id, (k, v) -> new Pending(v.mode(), expiry()));
    }

    public boolean clear(UUID id) {
        return pending.remove(id) != null;
    }

    private long expiry() {
        return System.currentTimeMillis() + plugin.settings().pendingTimeoutSeconds * 1000L;
    }
}
