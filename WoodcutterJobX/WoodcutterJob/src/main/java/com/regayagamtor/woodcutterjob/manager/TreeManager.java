package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.CooldownData;
import com.regayagamtor.woodcutterjob.model.TreeData;
import com.regayagamtor.woodcutterjob.model.TreeLocation;
import com.regayagamtor.woodcutterjob.model.TreeStatus;
import com.regayagamtor.woodcutterjob.model.TreeType;
import com.regayagamtor.woodcutterjob.util.TextUtil;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory registry of registered trees (loaded once, written through asynchronously). */
public final class TreeManager {

    private final WoodcutterJob plugin;
    private final Map<TreeLocation, TreeData> trees = new ConcurrentHashMap<>();

    public TreeManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            Map<TreeLocation, TreeData> loaded = plugin.database().callBlocking(() -> plugin.database().trees().loadAll(), 30);
            trees.clear();
            trees.putAll(loaded);
            plugin.getLogger().info("Loaded " + trees.size() + " registered tree(s).");
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load registered trees: " + ex.getMessage());
        }
    }

    public TreeData get(TreeLocation location) {
        return trees.get(location);
    }

    public TreeData get(Block block) {
        return trees.get(TreeLocation.of(block));
    }

    /** Cheap check used by protection listeners: material first, map lookup only for logs. */
    public boolean isRegistered(Block block) {
        return TreeType.fromMaterial(block.getType()) != null && trees.containsKey(TreeLocation.of(block));
    }

    public Collection<TreeData> all() {
        return trees.values();
    }

    public int count() {
        return trees.size();
    }

    /** @return the new tree, or null if that block is already registered. */
    public TreeData register(Block block, TreeType type) {
        TreeLocation loc = TreeLocation.of(block);
        TreeData data = new TreeData(loc, type);
        if (trees.putIfAbsent(loc, data) != null) return null;
        save(data);
        return data;
    }

    public boolean unregister(TreeLocation location) {
        TreeData removed = trees.remove(location);
        if (removed == null) return false;
        plugin.database().submit("delete tree", () -> plugin.database().trees().delete(location));
        return true;
    }

    public void save(TreeData tree) {
        plugin.database().submit("save tree", () -> plugin.database().trees().save(tree));
    }

    public void saveAll() {
        for (TreeData t : trees.values()) save(t);
    }

    /** Live status; only looks at the one block, never scans the world. */
    public TreeStatus statusOf(TreeData tree) {
        Block block = tree.location().getLoadedBlock();
        if (block == null) return TreeStatus.UNLOADED;
        TreeType current = TreeType.fromMaterial(block.getType());
        if (current == null) {
            tree.setInvalid(true);
            return TreeStatus.INVALID;
        }
        tree.setInvalid(false);
        return plugin.settings().enabledTreeTypes.contains(current) ? TreeStatus.VALID : TreeStatus.DISABLED;
    }

    public void sendInfo(CommandSender sender, TreeData tree) {
        CooldownData cd = plugin.cooldowns().get(tree);
        String cooldown = cd.active() ? TextUtil.formatDuration(cd.remainingMillis()) : "READY";
        plugin.messages().send(sender, "info",
                MessageManager.ph("world", tree.location().world()),
                MessageManager.ph("location", tree.location().coords()),
                MessageManager.ph("type", tree.type().name()),
                MessageManager.ph("cooldown", cooldown),
                MessageManager.ph("status", statusOf(tree).name()));
    }
}
