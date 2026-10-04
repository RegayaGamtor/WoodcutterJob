package com.regayagamtor.woodcutterjob.listener;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.config.Settings;
import com.regayagamtor.woodcutterjob.model.ClickMode;
import com.regayagamtor.woodcutterjob.model.RegionData;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Starts woodcutting when a player uses an axe while aiming into a region.
 * Only AIR / WATER interactions count: clicking a real block (a log, the ground, ...) is ignored.
 * The region only decides WHERE the job point is; no block is ever touched or registered.
 */
public final class RegionInteractListener implements Listener {

    private final WoodcutterJob plugin;

    public RegionInteractListener(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    // ignoreCancelled = false on purpose: *_CLICK_AIR events are often pre-cancelled by protection plugins.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action == Action.PHYSICAL) return;

        // A click on a solid block (log, ground, anything) never counts. Water is clickable-through, so it counts.
        Block clicked = event.getClickedBlock();
        if (clicked != null && !clicked.isLiquid()) return;

        Settings s = plugin.settings();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (s.regionClick == ClickMode.RIGHT && !right) return;
        if (s.regionClick == ClickMode.LEFT && right) return;

        Player player = event.getPlayer();
        if (!s.enabledAxes.contains(player.getInventory().getItemInMainHand().getType())) return;
        if (plugin.regions().count() == 0) return;

        RegionData region = plugin.regions().findTarget(player, s.regionRange);
        if (region == null) return;
        if (plugin.woodcutting().debounce(player)) return;
        plugin.woodcutting().start(player, region);
    }
}
