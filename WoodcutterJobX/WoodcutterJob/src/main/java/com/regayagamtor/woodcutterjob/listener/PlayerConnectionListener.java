package com.regayagamtor.woodcutterjob.listener;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads data before join (async), cleans up sessions and saves on quit. */
public final class PlayerConnectionListener implements Listener {

    private final WoodcutterJob plugin;

    public PlayerConnectionListener(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        plugin.playerData().load(event.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.playerData().has(player.getUniqueId())) {
            plugin.playerData().load(player.getUniqueId());
        }
        plugin.gui().purgeGuiItems(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.woodcutting().onQuit(player);
        plugin.playerData().unload(player.getUniqueId());
    }
}
