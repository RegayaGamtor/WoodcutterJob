package com.regayagamtor.woodcutterjob.model;

import com.regayagamtor.woodcutterjob.gui.WoodcuttingGui;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * State of one player's woodcutting. At most one session exists per player.
 * Phase MINIGAME: the GUI (click the green slot {@code required} times).
 * Phase CHOPPING: the player swings the axe automatically; every swing adds progress until 100%.
 */
public final class WoodcuttingSession {

    public enum State { ACTIVE, PAUSED, COMPLETED, CANCELLED, FAILED }

    public enum Phase { MINIGAME, CHOPPING }

    private final UUID sessionId = UUID.randomUUID();
    private final UUID playerId;
    private final String regionName;
    private final TreeType treeType;
    private final int required;
    private final Material axeMaterial;
    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

    private volatile Phase phase = Phase.MINIGAME;
    private double progress;          // minigame progress (green clicks), never shown in the GUI
    private double chopProgress;      // auto chopping progress
    private int totalHits;
    private int greenIndex;
    private int direction = 1;
    private long lastClickMillis;
    private long pausedAt;
    private WoodcuttingGui gui;
    private BukkitTask moveTask;
    private BukkitTask chopTask;
    private BossBar bossBar;

    public WoodcuttingSession(UUID playerId, String regionName, TreeType treeType, int required, Material axeMaterial) {
        this.playerId = playerId;
        this.regionName = regionName;
        this.treeType = treeType;
        this.required = Math.max(1, required);
        this.axeMaterial = axeMaterial;
    }

    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public String regionName() { return regionName; }
    public TreeType treeType() { return treeType; }
    public int required() { return required; }
    public Material axeMaterial() { return axeMaterial; }

    public State state() { return state.get(); }
    /** Atomic state change; returns false if the state was not {@code expected}. Used so rewards are given exactly once. */
    public boolean transition(State expected, State next) { return state.compareAndSet(expected, next); }
    public void forceState(State next) { state.set(next); }

    public Phase phase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    // ---- minigame

    public double progress() { return progress; }
    public int totalHits() { return totalHits; }

    /** Registers one successful (green) click and returns the new minigame progress. */
    public double addHit() {
        totalHits++;
        progress = Math.min(100.0, progress + 100.0 / required);
        return progress;
    }

    public void losePercent(double percent) { progress = Math.max(0.0, progress - percent); }
    /** True when the minigame itself is finished. */
    public boolean isComplete() { return progress >= 99.999; }

    /** Number of green hits so far, derived from progress. */
    public int displayHits() { return (int) Math.min(required, Math.round(progress * required / 100.0)); }

    // ---- auto chopping

    public double chopProgress() { return chopProgress; }

    /** One automatic swing; {@code step} is the percentage added. */
    public double addChopSwing(double step) {
        totalHits++;
        chopProgress = Math.min(100.0, chopProgress + step);
        return chopProgress;
    }

    public boolean isChopComplete() { return chopProgress >= 99.999; }

    // ---- misc

    public int greenIndex() { return greenIndex; }
    public void setGreenIndex(int greenIndex) { this.greenIndex = greenIndex; }
    public int direction() { return direction; }
    public void setDirection(int direction) { this.direction = direction; }
    public long lastClickMillis() { return lastClickMillis; }
    public void setLastClickMillis(long lastClickMillis) { this.lastClickMillis = lastClickMillis; }
    public long pausedAt() { return pausedAt; }
    public void setPausedAt(long pausedAt) { this.pausedAt = pausedAt; }
    public WoodcuttingGui gui() { return gui; }
    public void setGui(WoodcuttingGui gui) { this.gui = gui; }
    public BukkitTask moveTask() { return moveTask; }
    public void setMoveTask(BukkitTask moveTask) { this.moveTask = moveTask; }
    public BukkitTask chopTask() { return chopTask; }
    public void setChopTask(BukkitTask chopTask) { this.chopTask = chopTask; }
    public BossBar bossBar() { return bossBar; }
    public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }
}
