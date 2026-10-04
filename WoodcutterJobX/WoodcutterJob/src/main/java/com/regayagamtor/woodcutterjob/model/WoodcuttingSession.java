package com.regayagamtor.woodcutterjob.model;

import com.regayagamtor.woodcutterjob.gui.WoodcuttingGui;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** State of one player's minigame. At most one session exists per player (Map&lt;UUID, WoodcuttingSession&gt;). */
public final class WoodcuttingSession {

    public enum State { ACTIVE, PAUSED, COMPLETED, CANCELLED, FAILED }

    private final UUID sessionId = UUID.randomUUID();
    private final UUID playerId;
    private final TreeLocation treeLocation;
    private final TreeType treeType;
    private final int required;
    private final Material axeMaterial;
    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

    private double progress;
    private int totalHits;
    private int greenIndex;
    private int direction = 1;
    private long lastClickMillis;
    private long pausedAt;
    private WoodcuttingGui gui;
    private BukkitTask moveTask;

    public WoodcuttingSession(UUID playerId, TreeLocation treeLocation, TreeType treeType, int required, Material axeMaterial) {
        this.playerId = playerId;
        this.treeLocation = treeLocation;
        this.treeType = treeType;
        this.required = Math.max(1, required);
        this.axeMaterial = axeMaterial;
    }

    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public TreeLocation treeLocation() { return treeLocation; }
    public TreeType treeType() { return treeType; }
    public int required() { return required; }
    public Material axeMaterial() { return axeMaterial; }

    public State state() { return state.get(); }
    /** Atomic state change; returns false if the state was not {@code expected}. Used so rewards are given exactly once. */
    public boolean transition(State expected, State next) { return state.compareAndSet(expected, next); }
    public void forceState(State next) { state.set(next); }

    public double progress() { return progress; }
    public int totalHits() { return totalHits; }

    /** Registers one successful (green) click and returns the new progress. */
    public double addHit() {
        totalHits++;
        progress = Math.min(100.0, progress + 100.0 / required);
        return progress;
    }

    public void losePercent(double percent) { progress = Math.max(0.0, progress - percent); }
    public boolean isComplete() { return progress >= 99.999; }

    /** Number of "hits" shown in the GUI, derived from progress. */
    public int displayHits() { return (int) Math.min(required, Math.round(progress * required / 100.0)); }

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
}
