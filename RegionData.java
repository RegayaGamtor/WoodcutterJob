package com.regayagamtor.woodcutterjob.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A woodcutting region: an axis-aligned box (inclusive block coordinates) in one world.
 * The cooldown is stored on the region as an absolute timestamp.
 */
public final class RegionData {

    private final String name;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private volatile TreeType type;
    private volatile long cooldownUntil;

    public RegionData(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2,
                      TreeType type, long cooldownUntil) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.type = type;
        this.cooldownUntil = cooldownUntil;
    }

    public String name() { return name; }
    public String world() { return world; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public TreeType type() { return type; }
    public void setType(TreeType type) { this.type = type; }
    public long cooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(long cooldownUntil) { this.cooldownUntil = cooldownUntil; }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** "x1, y1, z1 -> x2, y2, z2" */
    public String coords() {
        return minX + ", " + minY + ", " + minZ + " -> " + maxX + ", " + maxY + ", " + maxZ;
    }

    /** @return the world or null if it is not loaded. */
    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    /** Squared distance from a location to the box (0 when the location is inside). */
    public double distanceSquared(Location loc) {
        double dx = axisDistance(loc.getX(), minX, maxX + 1.0);
        double dy = axisDistance(loc.getY(), minY, maxY + 1.0);
        double dz = axisDistance(loc.getZ(), minZ, maxZ + 1.0);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double v, double lo, double hi) {
        if (v < lo) return lo - v;
        if (v > hi) return v - hi;
        return 0.0;
    }

    /**
     * Ray vs. box (slab method).
     *
     * @return distance along the ray where it enters (or starts inside) the box, or -1 if it does not
     *         touch the box within {@code range}.
     */
    public double rayEntry(double ox, double oy, double oz, double dx, double dy, double dz, double range) {
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        double[] lo = {minX, minY, minZ};
        double[] hi = {maxX + 1.0, maxY + 1.0, maxZ + 1.0};
        double tMin = 0.0;
        double tMax = range;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                if (o[i] < lo[i] || o[i] > hi[i]) return -1.0;
            } else {
                double t1 = (lo[i] - o[i]) / d[i];
                double t2 = (hi[i] - o[i]) / d[i];
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return -1.0;
            }
        }
        return tMin;
    }
}
