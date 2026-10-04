package com.regayagamtor.woodcutterjob.hook;

import com.regayagamtor.woodcutterjob.WoodcutterJob;

/**
 * Isolates every reference to PlaceholderAPI classes. This class is only touched when PlaceholderAPI is
 * installed, so the plugin never fails to load without it.
 */
public final class PlaceholderHook {

    private final WoodcutterJob plugin;
    private WoodcutterExpansion expansion;

    public PlaceholderHook(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            expansion = new WoodcutterExpansion(plugin);
            expansion.register();
            plugin.getLogger().info("PlaceholderAPI expansion registered.");
        } catch (Throwable t) {
            expansion = null;
            plugin.getLogger().warning("Could not register the PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    public void unregister() {
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
                // shutting down
            }
            expansion = null;
        }
    }
}
