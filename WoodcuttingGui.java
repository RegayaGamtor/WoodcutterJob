package com.regayagamtor.woodcutterjob.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Inventory holder that identifies a woodcutting minigame GUI and links it to a session id. */
public final class WoodcuttingGui implements InventoryHolder {

    private final UUID sessionId;
    private final int[] targetSlots;
    private Inventory inventory;

    public WoodcuttingGui(UUID sessionId, int[] targetSlots) {
        this.sessionId = sessionId;
        this.targetSlots = targetSlots;
    }

    public UUID sessionId() { return sessionId; }
    public int[] targetSlots() { return targetSlots; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    /** @return index (0..n-1) in the target row for an inventory slot, or -1 if the slot is not a target slot. */
    public int indexOfSlot(int slot) {
        for (int i = 0; i < targetSlots.length; i++) {
            if (targetSlots[i] == slot) return i;
        }
        return -1;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
