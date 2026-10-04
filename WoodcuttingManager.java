package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.config.Settings;
import com.regayagamtor.woodcutterjob.gui.WoodcuttingGui;
import com.regayagamtor.woodcutterjob.job.JobManager;
import com.regayagamtor.woodcutterjob.model.CloseBehavior;
import com.regayagamtor.woodcutterjob.model.CooldownData;
import com.regayagamtor.woodcutterjob.model.MissMode;
import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import com.regayagamtor.woodcutterjob.model.RegionData;
import com.regayagamtor.woodcutterjob.model.WoodcuttingSession;
import com.regayagamtor.woodcutterjob.model.WoodcuttingSession.Phase;
import com.regayagamtor.woodcutterjob.model.WoodcuttingSession.State;
import com.regayagamtor.woodcutterjob.reward.RewardResult;
import com.regayagamtor.woodcutterjob.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core of the plugin: session lifecycle, minigame clicks, automatic chopping, completion.
 *
 * Flow: aim into a region with an axe -> GUI minigame (green clicks) -> the GUI closes and the player
 * swings by himself, every swing adds progress (boss bar / action bar) -> 100% -> reward + cooldown.
 */
public final class WoodcuttingManager {

    private static final long INTERACT_DEBOUNCE_MS = 400L;

    private final WoodcutterJob plugin;
    private final Map<UUID, WoodcuttingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> locks = new ConcurrentHashMap<>(); // lower-case region name -> player
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<>();

    public WoodcuttingManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public int activeSessions() {
        return sessions.size();
    }

    private static String key(String regionName) {
        return regionName.toLowerCase(Locale.ROOT);
    }

    /** @return true if this player interacted too recently (spam protection for messages/GUI opening). */
    public boolean debounce(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastInteract.get(player.getUniqueId());
        if (last != null && now - last < INTERACT_DEBOUNCE_MS) return true;
        lastInteract.put(player.getUniqueId(), now);
        return false;
    }

    // ------------------------------------------------------------------ start

    /** Validates everything and, if allowed, opens the minigame. Called when a player aims into a region with an axe. */
    public void start(Player player, RegionData region) {
        Settings s = plugin.settings();
        MessageManager m = plugin.messages();

        if (!player.hasPermission("woodcutter.use")) {
            m.send(player, "no-permission");
            return;
        }
        if (!s.isWorldEnabled(region.world())) {
            m.send(player, "world-disabled");
            return;
        }
        if (!s.enabledTreeTypes.contains(region.type())) {
            m.send(player, "tree-type-disabled", MessageManager.ph("type", region.type().name()));
            return;
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        if (!s.enabledAxes.contains(held)) {
            s.soundFail.play(player);
            m.send(player, "no-axe");
            return;
        }
        CooldownData cd = plugin.cooldowns().get(region);
        if (cd.active()) {
            s.soundDenied.play(player);
            if (s.cooldownShowMessage) {
                if (s.cooldownShowTime) {
                    m.send(player, "cooldown", MessageManager.ph("time", TextUtil.formatDuration(cd.remainingMillis())));
                } else {
                    m.send(player, "cooldown-no-time");
                }
            }
            return;
        }

        UUID id = player.getUniqueId();
        WoodcuttingSession existing = sessions.get(id);
        if (existing != null) {
            boolean sameRegion = existing.regionName().equalsIgnoreCase(region.name());
            if (sameRegion && existing.phase() == Phase.CHOPPING) {
                return; // already chopping this region, nothing to click
            }
            if (sameRegion && existing.state() == State.PAUSED && !isPausedExpired(existing)) {
                resume(existing, player);
                return;
            }
            if (sameRegion && existing.state() == State.ACTIVE) {
                if (plugin.gui().open(player, existing)) startMoveTask(existing);
                return;
            }
            endSession(existing, State.CANCELLED, true);
        }

        String lockKey = key(region.name());
        UUID holder = locks.get(lockKey);
        if (holder != null && !holder.equals(id)) {
            WoodcuttingSession other = sessions.get(holder);
            if (other == null) {
                locks.remove(lockKey, holder);
            } else if (other.state() == State.PAUSED && isPausedExpired(other)) {
                endSession(other, State.CANCELLED, false);
            } else {
                m.send(player, "tree-in-use");
                return;
            }
        }

        WoodcuttingSession session = new WoodcuttingSession(id, region.name(), region.type(), s.requiredSuccesses, held);
        session.setGreenIndex(ThreadLocalRandom.current().nextInt(s.targetSlots));
        session.setDirection(ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        sessions.put(id, session);
        locks.put(lockKey, id);

        if (!plugin.gui().open(player, session)) {
            endSession(session, State.CANCELLED, false);
            return;
        }
        startMoveTask(session);
    }

    private void resume(WoodcuttingSession session, Player player) {
        session.forceState(State.ACTIVE);
        if (!plugin.gui().open(player, session)) {
            endSession(session, State.CANCELLED, false);
            return;
        }
        startMoveTask(session);
        plugin.messages().send(player, "resumed");
    }

    private boolean isPausedExpired(WoodcuttingSession s) {
        return System.currentTimeMillis() - s.pausedAt() > plugin.settings().resumeTimeoutSeconds * 1000L;
    }

    // ------------------------------------------------------------------ minigame clicks

    /** Called for every (already cancelled) LEFT/RIGHT click in the GUI's top inventory. */
    public void handleClick(Player player, WoodcuttingGui gui, int rawSlot) {
        WoodcuttingSession s = sessions.get(player.getUniqueId());
        if (s == null || !s.sessionId().equals(gui.sessionId()) || s.state() != State.ACTIVE
                || s.phase() != Phase.MINIGAME) {
            return; // stale GUI / closed session / already chopping: ignore
        }
        Settings st = plugin.settings();
        long now = System.currentTimeMillis();
        if (now - s.lastClickMillis() < st.minClickIntervalMs) return; // anti spam
        s.setLastClickMillis(now);

        int index = gui.indexOfSlot(rawSlot);
        if (index < 0) return;

        RegionData region = plugin.regions().get(s.regionName());
        if (region == null) {
            plugin.messages().send(player, "region-gone");
            endSession(s, State.CANCELLED, true);
            return;
        }
        if (!isNear(player, region, st)) {
            plugin.messages().send(player, "too-far");
            endSession(s, State.CANCELLED, true);
            return;
        }

        if (index == s.greenIndex()) {
            onHit(player, s);
        } else {
            onMiss(player, s);
        }
    }

    private boolean isNear(Player player, RegionData region, Settings st) {
        World world = region.getWorld();
        if (world == null || !player.getWorld().equals(world)) return false;
        if (st.maxDistance <= 0.0) return true;
        return region.distanceSquared(player.getLocation()) <= st.maxDistance * st.maxDistance;
    }

    private void onHit(Player player, WoodcuttingSession s) {
        Settings st = plugin.settings();
        s.addHit();

        player.swingMainHand();
        st.soundSuccess.play(player);
        swingEffects(player, s);

        if (s.isComplete()) {
            beginChopping(s, player);
            return;
        }
        plugin.messages().actionBar(player, "hit-actionbar",
                MessageManager.ph("hits", String.valueOf(s.displayHits())),
                MessageManager.ph("required", String.valueOf(s.required())),
                MessageManager.ph("percent", TextUtil.percent(s.progress())));
        relocate(s);
        plugin.gui().render(s);
    }

    private void onMiss(Player player, WoodcuttingSession s) {
        Settings st = plugin.settings();
        st.soundFail.play(player);
        MissMode mode = st.missMode;
        if (mode == MissMode.FAIL_MINIGAME) {
            fail(s, player);
            return;
        }
        if (mode == MissMode.LOSE_PROGRESS) {
            s.losePercent(st.penaltyPercent);
            plugin.messages().actionBar(player, "miss-penalty-actionbar",
                    MessageManager.ph("penalty", TextUtil.percent(st.penaltyPercent)),
                    MessageManager.ph("hits", String.valueOf(s.displayHits())),
                    MessageManager.ph("required", String.valueOf(s.required())),
                    MessageManager.ph("percent", TextUtil.percent(s.progress())));
        } else {
            plugin.messages().actionBar(player, "miss-actionbar");
        }
        if (st.missRandomize) relocate(s);
        plugin.gui().render(s);
    }

    /** Moves the green target to a new index (different from the old one when configured). */
    private void relocate(WoodcuttingSession s) {
        Settings st = plugin.settings();
        int n = st.targetSlots;
        int old = s.greenIndex();
        int next;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (st.differentPosition && n > 1) {
            next = rnd.nextInt(n - 1);
            if (next >= old) next++;
        } else {
            next = rnd.nextInt(n);
        }
        s.setGreenIndex(next);
        if (st.movingEnabled) s.setDirection(rnd.nextBoolean() ? 1 : -1);
    }

    /** Sound + particles where the player is "hitting": a bit in front of his eyes. */
    private void swingEffects(Player player, WoodcuttingSession s) {
        Settings st = plugin.settings();
        Location eye = player.getEyeLocation();
        Location fx = eye.clone().add(eye.getDirection().multiply(1.5));
        st.soundWoodHit.playAt(fx);
        st.particleHitBlock.spawn(player.getWorld(), fx, s.treeType().defaultLog().createBlockData());
        st.particleHitCrit.spawn(player.getWorld(), fx, null);
    }

    // ------------------------------------------------------------------ chopping phase

    /** Minigame finished: close the GUI and let the player swing automatically until 100%. */
    private void beginChopping(WoodcuttingSession s, Player player) {
        s.setPhase(Phase.CHOPPING);
        stopMoveTask(s);
        plugin.messages().actionBar(player, "chop-start");

        // Close the GUI one tick later (never close an inventory from inside its own click event).
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(s.playerId()) != s || s.state() != State.ACTIVE) return;
            Player p = Bukkit.getPlayer(s.playerId());
            if (p == null || !p.isOnline()) {
                endSession(s, State.CANCELLED, false);
                return;
            }
            WoodcuttingGui gui = s.gui();
            if (gui != null && p.getOpenInventory().getTopInventory().getHolder() == gui) {
                p.closeInventory();
            }
            s.setGui(null);
            startChopTask(s, p);
        });
    }

    private void startChopTask(WoodcuttingSession s, Player player) {
        Settings st = plugin.settings();
        if (st.chopBossbar) {
            BossBar bar = BossBar.bossBar(chopTitle(s), 0.0f, st.chopBossbarColor, st.chopBossbarOverlay);
            s.setBossBar(bar);
            player.showBossBar(bar);
        }
        int period = st.chopIntervalTicks;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickChop(s), period, period);
        s.setChopTask(task);
    }

    private net.kyori.adventure.text.Component chopTitle(WoodcuttingSession s) {
        return plugin.messages().component("chop-bossbar",
                MessageManager.ph("tree", s.treeType().displayName()),
                MessageManager.ph("percent", TextUtil.percent(s.chopProgress())));
    }

    private void tickChop(WoodcuttingSession s) {
        if (s.state() != State.ACTIVE || s.phase() != Phase.CHOPPING || sessions.get(s.playerId()) != s) {
            stopChopTask(s);
            return;
        }
        Settings st = plugin.settings();
        MessageManager m = plugin.messages();
        Player p = Bukkit.getPlayer(s.playerId());
        if (p == null || !p.isOnline() || p.isDead()) {
            endSession(s, State.CANCELLED, false);
            return;
        }
        RegionData region = plugin.regions().get(s.regionName());
        if (region == null) {
            m.send(p, "region-gone");
            endSession(s, State.CANCELLED, true);
            return;
        }
        if (!isNear(p, region, st)) {
            m.send(p, "too-far");
            endSession(s, State.CANCELLED, true);
            return;
        }
        if (!st.enabledAxes.contains(p.getInventory().getItemInMainHand().getType())) {
            m.send(p, "chop-no-axe");
            endSession(s, State.CANCELLED, true);
            return;
        }

        // one automatic swing
        p.swingMainHand();
        st.soundSuccess.play(p);
        swingEffects(p, s);
        double progress = s.addChopSwing(100.0 / st.chopHits);

        if (s.isChopComplete()) {
            complete(s, p);
            return;
        }
        BossBar bar = s.bossBar();
        if (bar != null) {
            bar.progress((float) Math.max(0.0, Math.min(1.0, progress / 100.0)));
            bar.name(chopTitle(s));
        }
        if (st.chopActionbar) {
            m.actionBar(p, "chop-actionbar",
                    MessageManager.ph("tree", s.treeType().displayName()),
                    MessageManager.ph("percent", TextUtil.percent(progress)),
                    MessageManager.phc("progress_bar", TextUtil.progressBar(progress, st)));
        }
    }

    // ------------------------------------------------------------------ end states

    private void complete(WoodcuttingSession s, Player player) {
        if (!s.transition(State.ACTIVE, State.COMPLETED)) return; // guarantees the reward is given only once
        Settings st = plugin.settings();
        MessageManager m = plugin.messages();

        RegionData region = plugin.regions().get(s.regionName());
        endSession(s, State.COMPLETED, true);
        if (region == null) {
            m.send(player, "cancelled");
            return;
        }
        if (plugin.cooldowns().isActive(region)) {
            // Should be impossible thanks to the region lock; never pay twice for the same cooldown window.
            m.send(player, "cooldown", MessageManager.ph("time", TextUtil.formatDuration(plugin.cooldowns().get(region).remainingMillis())));
            return;
        }

        plugin.cooldowns().start(region);
        RewardResult reward = plugin.rewards().give(player, s.treeType());

        PlayerJobData data = plugin.playerData().get(player.getUniqueId());
        data.incrementTreesCut();
        data.addSuccessfulHits(s.totalHits());
        data.addTotalRewards(reward.totalItems());
        data.addTotalEarnings(reward.money());
        JobManager.XpResult xp = plugin.jobs().addXp(data, s.treeType());
        plugin.playerData().saveAsync(data);

        st.soundComplete.play(player);
        Location fx = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.5));
        st.particleComplete.spawn(player.getWorld(), fx, null);

        m.send(player, "success",
                MessageManager.ph("rewards", reward.describe()),
                MessageManager.ph("xp", String.valueOf(xp.xpGained())),
                MessageManager.ph("tree", s.treeType().displayName()));
        if (reward.inventoryFull()) m.send(player, "inventory-full");
        if (reward.money() > 0.0) {
            m.send(player, "money-received", MessageManager.ph("money", String.format(Locale.US, "%.2f", reward.money())));
        }
        if (xp.leveledUp()) {
            st.soundLevelUp.play(player);
            m.send(player, "level-up", MessageManager.ph("level", String.valueOf(xp.newLevel())));
        }
    }

    private void fail(WoodcuttingSession s, Player player) {
        if (!s.transition(State.ACTIVE, State.FAILED)) return;
        RegionData region = plugin.regions().get(s.regionName());
        endSession(s, State.FAILED, true);
        if (region != null) plugin.cooldowns().startFail(region);
        plugin.messages().send(player, "failed");
    }

    /** Called from InventoryCloseEvent for a minigame GUI. */
    public void onGuiClosed(Player player, WoodcuttingGui gui) {
        WoodcuttingSession s = sessions.get(player.getUniqueId());
        if (s == null || !s.sessionId().equals(gui.sessionId()) || s.state() != State.ACTIVE
                || s.phase() != Phase.MINIGAME) return;

        CloseBehavior behavior = plugin.settings().closeBehavior;
        if (!player.isOnline()) behavior = CloseBehavior.CANCEL;
        switch (behavior) {
            case CANCEL -> {
                endSession(s, State.CANCELLED, false);
                plugin.messages().send(player, "cancelled");
            }
            case FAIL -> {
                if (s.transition(State.ACTIVE, State.FAILED)) {
                    RegionData region = plugin.regions().get(s.regionName());
                    endSession(s, State.FAILED, false);
                    if (region != null) plugin.cooldowns().startFail(region);
                    plugin.messages().send(player, "failed");
                }
            }
            case RESUME -> {
                s.forceState(State.PAUSED);
                s.setPausedAt(System.currentTimeMillis());
                stopMoveTask(s);
            }
        }
    }

    /** Removes the session and its region lock; optionally closes the GUI. Never gives rewards. */
    private void endSession(WoodcuttingSession s, State finalState, boolean closeGui) {
        s.forceState(finalState);
        stopTasks(s);
        sessions.remove(s.playerId(), s);
        locks.remove(key(s.regionName()), s.playerId());

        Player p = Bukkit.getPlayer(s.playerId());
        BossBar bar = s.bossBar();
        if (bar != null) {
            if (p != null) p.hideBossBar(bar);
            s.setBossBar(null);
        }
        if (closeGui && s.gui() != null && p != null
                && p.getOpenInventory().getTopInventory().getHolder() == s.gui()) {
            p.closeInventory();
        }
    }

    /** Disconnect: drop the session without reward and without leaving any state behind. */
    public void onQuit(Player player) {
        WoodcuttingSession s = sessions.get(player.getUniqueId());
        if (s != null) endSession(s, State.CANCELLED, false);
        lastInteract.remove(player.getUniqueId());
        plugin.selections().clear(player.getUniqueId());
    }

    /** Cancels every session (reload) and notifies the players. @return how many were cancelled. */
    public int cancelAll() {
        List<WoodcuttingSession> copy = new ArrayList<>(sessions.values());
        for (WoodcuttingSession s : copy) {
            Player p = Bukkit.getPlayer(s.playerId());
            endSession(s, State.CANCELLED, true);
            if (p != null) plugin.messages().send(p, "cancelled");
        }
        return copy.size();
    }

    /** Cancels the sessions running on one region (region removed). @return how many were cancelled. */
    public int cancelRegion(String regionName) {
        int count = 0;
        for (WoodcuttingSession s : new ArrayList<>(sessions.values())) {
            if (!s.regionName().equalsIgnoreCase(regionName)) continue;
            Player p = Bukkit.getPlayer(s.playerId());
            endSession(s, State.CANCELLED, true);
            if (p != null) plugin.messages().send(p, "cancelled");
            count++;
        }
        return count;
    }

    /** Server shutdown: close everything silently. */
    public void shutdown() {
        for (WoodcuttingSession s : new ArrayList<>(sessions.values())) {
            endSession(s, State.CANCELLED, true);
        }
        sessions.clear();
        locks.clear();
        lastInteract.clear();
    }

    // ------------------------------------------------------------------ moving target

    private void startMoveTask(WoodcuttingSession s) {
        Settings st = plugin.settings();
        stopMoveTask(s);
        if (!st.movingEnabled || st.targetSlots < 2) return;
        int period = st.movingSpeedTicks;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickMove(s), period, period);
        s.setMoveTask(task);
    }

    private void tickMove(WoodcuttingSession s) {
        if (s.state() != State.ACTIVE || s.phase() != Phase.MINIGAME || sessions.get(s.playerId()) != s) {
            stopMoveTask(s);
            return;
        }
        Player p = Bukkit.getPlayer(s.playerId());
        if (p == null || s.gui() == null) {
            endSession(s, State.CANCELLED, false);
            return;
        }
        int n = plugin.settings().targetSlots;
        int old = s.greenIndex();
        int dir = s.direction();
        int next = old + dir;
        if (next < 0 || next >= n) {
            dir = -dir;
            s.setDirection(dir);
            next = old + dir;
        }
        if (next < 0 || next >= n) return;
        plugin.gui().moveTarget(s, old, next);
    }

    private void stopMoveTask(WoodcuttingSession s) {
        BukkitTask task = s.moveTask();
        if (task != null) {
            task.cancel();
            s.setMoveTask(null);
        }
    }

    private void stopChopTask(WoodcuttingSession s) {
        BukkitTask task = s.chopTask();
        if (task != null) {
            task.cancel();
            s.setChopTask(null);
        }
    }

    private void stopTasks(WoodcuttingSession s) {
        stopMoveTask(s);
        stopChopTask(s);
    }
}
