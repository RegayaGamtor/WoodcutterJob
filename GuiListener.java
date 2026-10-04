package com.regayagamtor.woodcutterjob.listener;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.gui.WoodcuttingGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Everything that happens in a woodcutting GUI is cancelled first (no taking, moving, shift-click, drag,
 * double-click, number keys, offhand swap, inserting items); only plain left/right clicks on target slots are processed.
 */
public final class GuiListener implements Listener {

    private final WoodcutterJob plugin;

    public GuiListener(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof WoodcuttingGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != top) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        plugin.woodcutting().handleClick(player, gui, event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WoodcuttingGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WoodcuttingGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof WoodcuttingGui gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.woodcutting().onGuiClosed(player, gui);
        plugin.gui().purgeGuiItems(player);
    }
}
