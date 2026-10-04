package com.regayagamtor.woodcutterjob.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Immutable identity of a registered tree: world + x + y + z. */
public record TreeLocation(String world, int x, int y, int z) {

    public static TreeLocation of(Block block) {
        return new TreeLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** @return the block (may load its chunk) or null if the world is not loaded. */
    public Block getBlock() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    /** @return the block only if its chunk is already loaded, otherwise null. */
    public Block getLoadedBlock() {
        World w = Bukkit.getWorld(world);
        if (w == null || !w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return w.getBlockAt(x, y, z);
    }

    /** @return the center of the block or null if the world is not loaded. */
    public Location center() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public String coords() {
        return x + ", " + y + ", " + z;
    }
}
