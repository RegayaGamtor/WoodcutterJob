package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import org.bukkit.Bukkit;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player data cache. Loaded before join (async pre-login), saved asynchronously, removed on quit. */
public final class PlayerDataManager {

    private static final long STALE_AFTER_MS = 120_000L;

    private final WoodcutterJob plugin;
    private final Map<UUID, PlayerJobData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    /** Blocking load through the DB thread (FIFO after any pending save of the same player). Safe on async threads. */
    public void load(UUID uuid) {
        PlayerJobData data;
        try {
            data = plugin.database().callBlocking(() -> plugin.database().players().load(uuid), 10);
            if (data == null) data = new PlayerJobData(uuid);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load player data for " + uuid + " (progress will not be saved this session): " + ex.getMessage());
            data = new PlayerJobData(uuid);
            data.setSaveAllowed(false);
        }
        cache.put(uuid, data);
    }

    public boolean has(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /** @return the cached data, loading it first if it is missing (rare edge case). */
    public PlayerJobData get(UUID uuid) {
        PlayerJobData data = cache.get(uuid);
        if (data == null) {
            load(uuid);
            data = cache.get(uuid);
        }
        return data;
    }

    /** @return cached data or null; never loads. Used by PlaceholderAPI. */
    public PlayerJobData peek(UUID uuid) {
        return cache.get(uuid);
    }

    public void saveAsync(PlayerJobData data) {
        if (data == null || !data.isSaveAllowed()) return;
        PlayerJobData snapshot = data.copy();
        plugin.database().submit("save player " + data.uuid(), () -> plugin.database().players().save(snapshot));
    }

    public void unload(UUID uuid) {
        PlayerJobData data = cache.remove(uuid);
        saveAsync(data);
    }

    public void saveAll() {
        for (PlayerJobData data : cache.values()) saveAsync(data);
    }

    /** Drops cached data of players who never joined (e.g. login denied after pre-login). Main thread only. */
    public void cleanupOffline() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PlayerJobData>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerJobData> e = it.next();
            if (now - e.getValue().loadedAt() > STALE_AFTER_MS && Bukkit.getPlayer(e.getKey()) == null) {
                saveAsync(e.getValue());
                it.remove();
            }
        }
    }
}
