package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.RegionData;
import com.regayagamtor.woodcutterjob.model.TreeType;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** In-memory registry of woodcutting regions (loaded once, written through asynchronously). */
public final class RegionManager {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    private final WoodcutterJob plugin;
    private final Map<String, RegionData> regions = new ConcurrentHashMap<>();

    public RegionManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            Map<String, RegionData> loaded = plugin.database().callBlocking(() -> plugin.database().regions().loadAll(), 30);
            regions.clear();
            regions.putAll(loaded);
            plugin.getLogger().info("Loaded " + regions.size() + " woodcutting region(s).");
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load regions: " + ex.getMessage());
        }
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public RegionData get(String name) {
        return name == null ? null : regions.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<RegionData> all() {
        return regions.values();
    }

    public int count() {
        return regions.size();
    }

    /** @return the new region, or null if a region with that name already exists. */
    public RegionData create(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2, TreeType type) {
        RegionData region = new RegionData(name, world, x1, y1, z1, x2, y2, z2, type, 0L);
        if (regions.putIfAbsent(name.toLowerCase(Locale.ROOT), region) != null) return null;
        save(region);
        return region;
    }

    public boolean remove(String name) {
        RegionData removed = regions.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) return false;
        String stored = removed.name();
        plugin.database().submit("delete region", () -> plugin.database().regions().delete(stored));
        return true;
    }

    public void save(RegionData region) {
        plugin.database().submit("save region", () -> plugin.database().regions().save(region));
    }

    public void saveAll() {
        for (RegionData r : regions.values()) save(r);
    }

    /**
     * The region the player is pointing into (the one the view ray enters first), or null.
     * Only air/water counts: if a solid block is hit before the ray reaches the region, there is no match.
     * (A click on a block, e.g. a log, never gets here - the listener ignores those.)
     */
    public RegionData findTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        String world = player.getWorld().getName();

        RegionData best = null;
        double bestT = Double.MAX_VALUE;
        for (RegionData r : regions.values()) {
            if (!r.world().equals(world)) continue;
            double t = r.rayEntry(eye.getX(), eye.getY(), eye.getZ(), dir.getX(), dir.getY(), dir.getZ(), range);
            if (t >= 0.0 && t < bestT) {
                best = r;
                bestT = t;
            }
        }
        if (best == null) return null;

        // Something solid in front of the region? (normally impossible for a *_CLICK_AIR event, but the
        // configured range may be larger than the vanilla reach)
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition().distance(eye.toVector()) < bestT - 1.0E-4) return null;
        return best;
    }
}
