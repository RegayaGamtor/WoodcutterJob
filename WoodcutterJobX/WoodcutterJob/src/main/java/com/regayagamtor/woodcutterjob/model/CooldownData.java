package com.regayagamtor.woodcutterjob.model;

/** Snapshot of a tree cooldown at a point in time. */
public record CooldownData(long until, long now) {

    public boolean active() {
        return until > now;
    }

    public long remainingMillis() {
        return Math.max(0L, until - now);
    }
}
