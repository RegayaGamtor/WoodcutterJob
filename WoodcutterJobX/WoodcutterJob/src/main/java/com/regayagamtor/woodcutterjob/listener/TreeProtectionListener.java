package com.regayagamtor.woodcutterjob.listener;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Registered trees must never disappear: block breaking, explosions, fire and pistons are blocked. */
public final class TreeProtectionListener implements Listener {

    private final WoodcutterJob plugin;

    public TreeProtectionListener(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    private boolean protectedTree(Block block) {
        return plugin.settings().protectTrees && plugin.trees().isRegistered(block);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (protectedTree(event.getBlock())) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "tree-protected");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.settings().protectTrees) event.blockList().removeIf(this::protectedTree);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.settings().protectTrees) event.blockList().removeIf(this::protectedTree);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent event) {
        if (protectedTree(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (protectedTree(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (protectedTree(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
