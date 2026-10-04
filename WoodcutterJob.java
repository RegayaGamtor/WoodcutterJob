package com.regayagamtor.woodcutterjob;

import com.regayagamtor.woodcutterjob.command.WoodcutterCommand;
import com.regayagamtor.woodcutterjob.config.Settings;
import com.regayagamtor.woodcutterjob.cooldown.CooldownManager;
import com.regayagamtor.woodcutterjob.database.DatabaseManager;
import com.regayagamtor.woodcutterjob.hook.PlaceholderHook;
import com.regayagamtor.woodcutterjob.hook.VaultHook;
import com.regayagamtor.woodcutterjob.job.JobManager;
import com.regayagamtor.woodcutterjob.listener.GuiListener;
import com.regayagamtor.woodcutterjob.listener.PlayerConnectionListener;
import com.regayagamtor.woodcutterjob.listener.RegionInteractListener;
import com.regayagamtor.woodcutterjob.manager.GuiManager;
import com.regayagamtor.woodcutterjob.manager.MessageManager;
import com.regayagamtor.woodcutterjob.manager.PlayerDataManager;
import com.regayagamtor.woodcutterjob.manager.RegionManager;
import com.regayagamtor.woodcutterjob.manager.SelectionManager;
import com.regayagamtor.woodcutterjob.manager.WoodcuttingManager;
import com.regayagamtor.woodcutterjob.reward.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * WoodcutterJob by RegayaGamtor.
 * Aim into a woodcutting region with an axe -> GUI minigame -> automatic chopping -> reward -> persistent cooldown.
 */
public final class WoodcutterJob extends JavaPlugin {

    private Settings settings;
    private MessageManager messages;
    private DatabaseManager database;
    private RegionManager regions;
    private SelectionManager selections;
    private CooldownManager cooldowns;
    private PlayerDataManager playerData;
    private JobManager jobs;
    private VaultHook vault;
    private RewardManager rewards;
    private GuiManager gui;
    private WoodcuttingManager woodcutting;
    private PlaceholderHook placeholderHook;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig(), getLogger());

        messages = new MessageManager(this);
        messages.load();

        database = new DatabaseManager(this);
        database.init();

        regions = new RegionManager(this);
        regions.load();
        selections = new SelectionManager();
        playerData = new PlayerDataManager(this);
        cooldowns = new CooldownManager(this);
        jobs = new JobManager(this);
        vault = new VaultHook(this);
        vault.setup();
        rewards = new RewardManager(this);
        gui = new GuiManager(this);
        woodcutting = new WoodcuttingManager(this);

        Bukkit.getPluginManager().registerEvents(new RegionInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        PluginCommand command = getCommand("woodcutter");
        if (command != null) {
            WoodcutterCommand executor = new WoodcutterCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderHook = new PlaceholderHook(this);
            placeholderHook.register();
        }

        // Players already online (e.g. /reload): load their data now.
        for (Player p : Bukkit.getOnlinePlayers()) {
            playerData.load(p.getUniqueId());
        }

        long period = settings.autosaveSeconds * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            playerData.saveAll();
            playerData.cleanupOffline();
        }, period, period);

        getLogger().info("WoodcutterJob " + getPluginMeta().getVersion() + " enabled (by RegayaGamtor).");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) autosaveTask.cancel();
        if (placeholderHook != null) placeholderHook.unregister();
        if (woodcutting != null) woodcutting.shutdown();
        if (playerData != null) playerData.saveAll();
        if (regions != null) regions.saveAll();
        if (database != null) database.close(); // waits for queued saves
    }

    /**
     * Reloads config.yml and messages.yml. Regions and cooldowns are untouched (they live in storage/memory);
     * active minigames are cancelled because GUI layout settings may have changed.
     *
     * @return number of cancelled sessions
     */
    public int reloadAll() {
        int cancelled = woodcutting.cancelAll();
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        messages.load();
        gui.rebuildTemplates();
        vault.setup();
        return cancelled;
    }

    /**
     * Called by the in-game commands (cooldown / reward) after they changed {@code getConfig()}:
     * writes config.yml and rebuilds the validated settings. Running sessions are not touched.
     */
    public void saveConfigAndRefresh() {
        saveConfig();
        settings = new Settings(getConfig(), getLogger());
    }

    public Settings settings() { return settings; }
    public MessageManager messages() { return messages; }
    public DatabaseManager database() { return database; }
    public RegionManager regions() { return regions; }
    public SelectionManager selections() { return selections; }
    public CooldownManager cooldowns() { return cooldowns; }
    public PlayerDataManager playerData() { return playerData; }
    public JobManager jobs() { return jobs; }
    public VaultHook vault() { return vault; }
    public RewardManager rewards() { return rewards; }
    public GuiManager gui() { return gui; }
    public WoodcuttingManager woodcutting() { return woodcutting; }
}
