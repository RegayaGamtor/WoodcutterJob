package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.WoodcutterJob;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Chooses the storage backend and runs all storage work on ONE dedicated thread, so the main thread
 * never waits on I/O during gameplay and reads/writes are strictly ordered (FIFO).
 */
public final class DatabaseManager {

    /** A unit of storage work that may throw. */
    @FunctionalInterface
    public interface DbTask {
        void run() throws Exception;
    }

    private final WoodcutterJob plugin;
    private ExecutorService executor;
    private SQLiteManager sqlite;
    private PlayerRepository players;
    private RegionRepository regions;
    private String activeType = "NONE";

    public DatabaseManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public void init() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WoodcutterJob-DB");
            t.setDaemon(false);
            return t;
        });

        if (plugin.settings().storageType.equals("SQLITE")) {
            try {
                File file = new File(plugin.getDataFolder(), plugin.settings().sqliteFile);
                SQLiteManager manager = new SQLiteManager(file, plugin.getLogger());
                manager.connection(); // opens the connection and creates the tables
                sqlite = manager;
                players = new SqlitePlayerRepository(manager);
                regions = new SqliteRegionRepository(manager, plugin.getLogger());
                activeType = "SQLITE";
            } catch (Exception ex) {
                plugin.getLogger().severe("SQLite could not be initialised, falling back to YAML storage: " + ex.getMessage());
                if (sqlite != null) sqlite.close();
                sqlite = null;
            }
        }
        if (players == null || regions == null) {
            players = new YamlPlayerRepository(plugin.getDataFolder());
            regions = new YamlRegionRepository(plugin.getDataFolder(), plugin.getLogger());
            activeType = "YAML";
        }
        plugin.getLogger().info("Storage backend: " + activeType);
    }

    public String activeType() { return activeType; }
    public PlayerRepository players() { return players; }
    public RegionRepository regions() { return regions; }

    /** Runs work on the DB thread; failures are logged, never thrown. */
    public void submit(String description, DbTask task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    plugin.getLogger().severe("Storage error (" + description + "): " + ex.getMessage());
                    if (plugin.settings().debug) ex.printStackTrace();
                }
            });
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().warning("Storage is shut down; skipped: " + description);
        }
    }

    /** Runs work on the DB thread and waits for it. Never use this per click on the main thread. */
    public <T> T callBlocking(Callable<T> task, long timeoutSeconds) throws Exception {
        Future<T> future = executor.submit(task);
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** Waits for queued work to finish, then closes the connection. */
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Storage thread did not finish in time; some data may not have been saved.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (sqlite != null) sqlite.close();
    }
}
