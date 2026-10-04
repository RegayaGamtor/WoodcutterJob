package com.regayagamtor.woodcutterjob.hook;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/** Optional Vault economy access through reflection: no compile-time or runtime dependency on Vault. */
public final class VaultHook {

    private final WoodcutterJob plugin;
    private Object economy;
    private Method depositMethod;

    public VaultHook(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        economy = null;
        depositMethod = null;
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            if (rsp == null) {
                plugin.getLogger().info("Vault found, but no economy plugin is registered. Money rewards are disabled.");
                return;
            }
            economy = rsp.getProvider();
            depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            plugin.getLogger().info("Vault economy hooked.");
        } catch (Exception | LinkageError ex) {
            economy = null;
            depositMethod = null;
            plugin.getLogger().warning("Could not hook into Vault: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return economy != null && depositMethod != null;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isAvailable() || amount <= 0.0) return false;
        try {
            depositMethod.invoke(economy, player, amount);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Vault deposit failed: " + ex.getMessage());
            return false;
        }
    }
}
