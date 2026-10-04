package com.regayagamtor.woodcutterjob.model;

/** A registered tree ("job point"). Cooldown is stored as an absolute timestamp. */
public final class TreeData {

    private final TreeLocation location;
    private volatile TreeType type;
    private volatile long cooldownUntil;
    private volatile boolean invalid;

    public TreeData(TreeLocation location, TreeType type) {
        this(location, type, 0L);
    }

    public TreeData(TreeLocation location, TreeType type, long cooldownUntil) {
        this.location = location;
        this.type = type;
        this.cooldownUntil = cooldownUntil;
    }

    public TreeLocation location() { return location; }
    public TreeType type() { return type; }
    public void setType(TreeType type) { this.type = type; }
    public long cooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(long cooldownUntil) { this.cooldownUntil = cooldownUntil; }
    public boolean isInvalid() { return invalid; }
    public void setInvalid(boolean invalid) { this.invalid = invalid; }
}
