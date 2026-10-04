package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.config.Settings;
import com.regayagamtor.woodcutterjob.gui.WoodcuttingGui;
import com.regayagamtor.woodcutterjob.model.WoodcuttingSession;
import com.regayagamtor.woodcutterjob.util.ItemBuilder;
import com.regayagamtor.woodcutterjob.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds and updates the minigame inventory.
 * Layout: top row center = info item, middle row = target slots (RED/GREEN), everything else = filler panes.
 * The GUI shows NO progress (tree progress happens after the minigame, outside the inventory).
 */
public final class GuiManager {

    private final WoodcutterJob plugin;
    private final NamespacedKey itemKey;
    private ItemStack filler;
    private ItemStack red;
    private ItemStack green;

    public GuiManager(WoodcutterJob plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "gui_item");
        rebuildTemplates();
    }

    /** (Re)creates the cached static items; called on enable and reload. */
    public void rebuildTemplates() {
        Settings s = plugin.settings();
        MessageManager m = plugin.messages();
        filler = ItemBuilder.create(s.guiFiller, m.component("gui.filler-name"), m.lore("gui.filler-lore"), itemKey);
        red = ItemBuilder.create(s.guiRed, m.component("gui.red-name"), m.lore("gui.red-lore"), itemKey);
        green = ItemBuilder.create(s.guiGreen, m.component("gui.green-name"), m.lore("gui.green-lore"), itemKey);
    }

    /** Creates the GUI for a session, renders it and opens it. @return false if opening was refused. */
    public boolean open(Player player, WoodcuttingSession session) {
        Settings s = plugin.settings();
        int rows = s.rows;
        int n = s.targetSlots;
        int targetRow = rows / 2;
        int startCol = (9 - n) / 2;
        int[] slots = new int[n];
        for (int i = 0; i < n; i++) {
            slots[i] = targetRow * 9 + startCol + i;
        }
        WoodcuttingGui holder = new WoodcuttingGui(session.sessionId(), slots);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                plugin.messages().parse(s.guiTitle, MessageManager.ph("tree", session.treeType().displayName())));
        holder.setInventory(inv);
        session.setGui(holder);
        render(session);
        InventoryView view = player.openInventory(inv);
        return view != null;
    }

    /** Full redraw (after a hit/miss). */
    public void render(WoodcuttingSession session) {
        WoodcuttingGui gui = session.gui();
        if (gui == null) return;
        Settings s = plugin.settings();
        Inventory inv = gui.getInventory();
        MessageManager m = plugin.messages();

        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        int[] slots = gui.targetSlots();
        for (int i = 0; i < slots.length; i++) {
            inv.setItem(slots[i], i == session.greenIndex() ? green : red);
        }

        TagResolver[] r = resolvers(session, s);
        Material icon = s.useHeldAxeIcon ? session.axeMaterial() : Material.WOODEN_AXE;
        inv.setItem(4, ItemBuilder.create(icon, m.component("gui.info-name", r), m.lore("gui.info-lore", r), itemKey));
    }

    /** Cheap update for the moving target: only two slots change. */
    public void moveTarget(WoodcuttingSession session, int oldIndex, int newIndex) {
        WoodcuttingGui gui = session.gui();
        if (gui == null) return;
        int[] slots = gui.targetSlots();
        if (oldIndex < 0 || newIndex < 0 || oldIndex >= slots.length || newIndex >= slots.length) return;
        Inventory inv = gui.getInventory();
        inv.setItem(slots[oldIndex], red);
        inv.setItem(slots[newIndex], green);
        session.setGreenIndex(newIndex);
    }

    private TagResolver[] resolvers(WoodcuttingSession session, Settings s) {
        return new TagResolver[] {
                MessageManager.ph("tree", session.treeType().displayName()),
                MessageManager.ph("hits", String.valueOf(session.displayHits())),
                MessageManager.ph("required", String.valueOf(session.required())),
                MessageManager.ph("percent", TextUtil.percent(session.progress())),
                MessageManager.phc("progress_bar", TextUtil.progressBar(session.progress(), s))
        };
    }

    public boolean isGuiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /** Safety net: removes any GUI-marked item from a player's inventory/cursor (called on close and join). */
    public void purgeGuiItems(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (isGuiItem(contents[i])) {
                inv.setItem(i, null);
                changed = true;
            }
        }
        if (isGuiItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            changed = true;
        }
        if (changed) player.updateInventory();
    }
}
