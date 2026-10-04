package com.regayagamtor.woodcutterjob.model;

import java.util.UUID;

/** Persistent woodcutter statistics of one player. Mutators are synchronized (saved from another thread). */
public final class PlayerJobData {

    private final UUID uuid;
    private int level = 1;
    private long xp;
    private int treesCut;
    private int successfulHits;
    private long totalRewards;
    private double totalEarnings;
    private volatile boolean saveAllowed = true;
    private final long loadedAt = System.currentTimeMillis();

    public PlayerJobData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() { return uuid; }
    public synchronized int level() { return level; }
    public synchronized void setLevel(int level) { this.level = level; }
    public synchronized long xp() { return xp; }
    public synchronized void addXp(long amount) { this.xp += amount; }
    public synchronized int treesCut() { return treesCut; }
    public synchronized void incrementTreesCut() { this.treesCut++; }
    public synchronized int successfulHits() { return successfulHits; }
    public synchronized void addSuccessfulHits(int amount) { this.successfulHits += amount; }
    public synchronized long totalRewards() { return totalRewards; }
    public synchronized void addTotalRewards(long amount) { this.totalRewards += amount; }
    public synchronized double totalEarnings() { return totalEarnings; }
    public synchronized void addTotalEarnings(double amount) { this.totalEarnings += amount; }

    /** False if the load from storage failed; such data must never overwrite the stored row. */
    public boolean isSaveAllowed() { return saveAllowed; }
    public void setSaveAllowed(boolean saveAllowed) { this.saveAllowed = saveAllowed; }
    public long loadedAt() { return loadedAt; }

    public synchronized void restore(int level, long xp, int treesCut, int successfulHits, long totalRewards, double totalEarnings) {
        this.level = Math.max(1, level);
        this.xp = Math.max(0L, xp);
        this.treesCut = Math.max(0, treesCut);
        this.successfulHits = Math.max(0, successfulHits);
        this.totalRewards = Math.max(0L, totalRewards);
        this.totalEarnings = Math.max(0.0, totalEarnings);
    }

    /** @return an independent copy used for asynchronous saving. */
    public synchronized PlayerJobData copy() {
        PlayerJobData c = new PlayerJobData(uuid);
        c.restore(level, xp, treesCut, successfulHits, totalRewards, totalEarnings);
        c.saveAllowed = this.saveAllowed;
        return c;
    }
}
