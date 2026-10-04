package com.regayagamtor.woodcutterjob.listener;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.config.Settings;
import com.regayagamtor.woodcutterjob.manager.AdminModeManager;
import com.regayagamtor.woodcutterjob.manager.AdminModeManager.Mode;
import com.regayagamtor.woodcutterjob.manager.MessageManager;
import com.regayagamtor.woodcutterjob.model.TreeData;
import com.regayagamtor.woodcutterjob.model.TreeLocation;
import com.regayagamtor.woodcutterjob.model.TreeType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/** Handles clicks on blocks: admin actions (set/remove/info) and starting the minigame on registered trees. */
public final class TreeInteractListener implements Listener {

    private final WoodcutterJob plugin;

    public TreeInteractListener(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    // ignoreCancelled = false on purpose: registered trees must work even inside region-protected areas.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        boolean mainHand = event.getHand() == EquipmentSlot.HAND;

        if (mainHand) {
            Mode mode = plugin.adminModes().peek(player.getUniqueId());
            if (mode != null) {
                event.setCancelled(true);
                handleAdmin(player, block, mode);
                return;
            }
        }

        TreeData tree = plugin.trees().get(TreeLocation.of(block));
        if (tree == null) return;

        TreeType current = TreeType.fromMaterial(block.getType());
        if (current == null) {
            // Registered point whose block changed: mark invalid, do not interfere with whatever block it is now.
            tree.setInvalid(true);
            if (mainHand && action == Action.RIGHT_CLICK_BLOCK && player.hasPermission("woodcutter.admin")) {
                plugin.messages().send(player, "tree-invalid");
            }
            return;
        }
        tree.setInvalid(false);

        // Cancel for both hands: prevents stripping the log with an axe and any other vanilla interaction.
        event.setCancelled(true);
        if (!mainHand || action != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.woodcutting().debounce(player)) return;
        plugin.woodcutting().start(player, tree, block);
    }

    private void handleAdmin(Player player, Block block, Mode mode) {
        Settings s = plugin.settings();
        MessageManager m = plugin.messages();
        UUID id = player.getUniqueId();
        boolean success = false;

        switch (mode) {
            case SET -> {
                if (!player.hasPermission("woodcutter.set")) {
                    m.send(player, "no-permission");
                    plugin.adminModes().clear(id);
                    return;
                }
                TreeType type = TreeType.fromMaterial(block.getType());
                if (type == null) {
                    m.send(player, "tree-invalid-block");
                } else if (!s.enabledTreeTypes.contains(type)) {
                    m.send(player, "tree-type-disabled", MessageManager.ph("type", type.name()));
                } else if (plugin.trees().register(block, type) == null) {
                    m.send(player, "tree-already-registered");
                } else {
                    success = true;
                    m.send(player, "tree-set",
                            MessageManager.ph("type", type.name()),
                            MessageManager.ph("location", TreeLocation.of(block).coords()));
                }
            }
            case REMOVE -> {
                if (!player.hasPermission("woodcutter.remove")) {
                    m.send(player, "no-permission");
                    plugin.adminModes().clear(id);
                    return;
                }
                if (plugin.trees().unregister(TreeLocation.of(block))) {
                    success = true;
                    m.send(player, "tree-removed");
                } else {
                    m.send(player, "tree-not-registered");
                }
            }
            case INFO -> {
                if (!player.hasPermission("woodcutter.info")) {
                    m.send(player, "no-permission");
                    plugin.adminModes().clear(id);
                    return;
                }
                TreeData tree = plugin.trees().get(TreeLocation.of(block));
                if (tree == null) {
                    m.send(player, "tree-not-registered");
                } else {
                    success = true;
                    plugin.trees().sendInfo(player, tree);
                }
            }
        }

        if (success) {
            if (s.keepAdminMode) plugin.adminModes().refresh(id);
            else plugin.adminModes().clear(id);
        }
    }
}
