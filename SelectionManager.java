package com.regayagamtor.woodcutterjob.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-admin pos1/pos2 selection used by /woodcutter region create (like a mini WorldEdit selection). */
public final class SelectionManager {

    public record Point(String world, int x, int y, int z) {
        public String coords() {
            return x + ", " + y + ", " + z;
        }
    }

    private final Map<UUID, Point[]> selections = new ConcurrentHashMap<>();

    /** @param index 0 for pos1, 1 for pos2 */
    public void set(UUID id, int index, Point point) {
        selections.computeIfAbsent(id, k -> new Point[2])[index] = point;
    }

    /** @return array of two (possibly null) points, or null if nothing was ever selected. */
    public Point[] get(UUID id) {
        return selections.get(id);
    }

    public void clear(UUID id) {
        selections.remove(id);
    }
}
