package com.regayagamtor.woodcutterjob.manager;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.config.Settings;
import com.regayagamtor.woodcutterjob.gui.WoodcuttingGui;
import com.regayagamtor.woodcutterjob.job.JobManager;
import com.regayagamtor.woodcutterjob.model.CloseBehavior;
import com.regayagamtor.woodcutterjob.model.CooldownData;
import com.regayagamtor.woodcutterjob.model.MissMode;
import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import com.regayagamtor.woodcutterjob.model.TreeData;
import com.regayagamtor.woodcutterjob.model.TreeLocation;
import com.regayagamtor.woodcutterjob.model.TreeType;
import com.regayagamtor.woodcutterjob.model.WoodcuttingSession;
import com.regayagamtor.woodcutterjob.model.WoodcuttingSession.State;
import com.regayagamtor.woodcutterjob.reward.RewardResult;
import com.regayagamtor.woodcutterjob.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core of the plugin: session lifecycle, click handling, completion.
 * The registered tree block is NEVER modified here - it only serves as a job point.
 */
public final class WoodcuttingManager {

    private static final long INTERACT_DEBOUNCE_MS = 400L;

    private final WoodcutterJob plugin;
    private final Map<UUID, WoodcuttingSession> sessions = new ConcurrentHashMap<>();
    private final Map<TreeLocation, UUID> locks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<>();

    public WoodcuttingManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public int activeSessions() {
        return sessions.size();
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

    /** Validates everything and, if allowed, opens the minigame. Called for RIGHT_CLICK_BLOCK on a registered tree. */
    public void start(Player player, TreeData tree, Block block) {
        Settings s = plugin.settings();
        MessageManager m = plugin.messages();

        if (!player.hasPermission("woodcutter.use")) {
            m.send(player, "no-permission");
            return;
        }
        if (!s.isWorldEnabled(block.getWorld().getName())) {
            m.send(player, "world-disabled");
            return;
        }
        TreeType current = TreeType.fromMaterial(block.getType());
        if (current == null) {
            tree.setInvalid(true);
            m.send(player, "tree-invalid");
            return;
        }
        tree.setInvalid(false);
        if (current != tree.type()) {
            tree.setType(current);
            plugin.trees().save(tree);
        }
        if (!s.enabledTreeTypes.contains(current)) {
            m.send(player, "tree-type-disabled", MessageManager.ph("type", current.name()));
            return;
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        if (!s.enabledAxes.contains(held)) {
            s.soundFail.play(player);
            m.send(player, "no-axe");
            return;
        }
        CooldownData cd = plugin.cooldowns().get(tree);
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
            boolean sameTree = existing.treeLocation().equals(tree.location());
            if (sameTree && existing.state() == State.PAUSED && !isPausedExpired(existing)) {
                resume(existing, player);
                return;
            }
            if (sameTree && existing.state() == State.ACTIVE) {
                if (plugin.gui().open(player, existing)) startMoveTask(existing);
                return;
            }
            endSession(existing, State.CANCELLED, true);
        }

        UUID holder = locks.get(tree.location());
        if (holder != null && !holder.equals(id)) {
            WoodcuttingSession other = sessions.get(holder);
            if (other == null) {
                locks.remove(tree.location(), holder);
            } else if (other.state() == State.PAUSED && isPausedExpired(other)) {
                endSession(other, State.CANCELLED, false);
            } else {
                m.send(player, "tree-in-use");
                return;
            }
        }

        WoodcuttingSession session = new WoodcuttingSession(id, tree.location(), current, s.requiredSuccesses, held);
        session.setGreenIndex(ThreadLocalRandom.current().nextInt(s.targetSlots));
        session.setDirection(ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        sessions.put(id, session);
        locks.put(tree.location(), id);

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

    // ------------------------------------------------------------------ clicks

    /** Called for every (already cancelled) LEFT/RIGHT click in the GUI's top inventory. */
    public void handleClick(Player player, WoodcuttingGui gui, int rawSlot) {
        WoodcuttingSession s = sessions.get(player.getUniqueId());
        if (s == null || !s.sessionId().equals(gui.sessionId()) || s.state() != State.ACTIVE) {
            return; // stale GUI / closed session: ignore
        }
        Settings st = plugin.settings();
        long now = System.currentTimeMillis();
        if (now - s.lastClickMillis() < st.minClickIntervalMs) return; // anti spam
        s.setLastClickMillis(now);

        int index = gui.indexOfSlot(rawSlot);
        if (index < 0) return;

        TreeData tree = plugin.trees().get(s.treeLocation());
        Block block = s.treeLocation().getBlock();
        if (tree == null || block == null || TreeType.fromMaterial(block.getType()) == null) {
            plugin.messages().send(player, "tree-invalid");
            endSession(s, State.CANCELLED, true);
            return;
        }
        if (!isNear(player, s, st)) {
            plugin.messages().send(player, "too-far");
            endSession(s, State.CANCELLED, true);
            return;
        }

        if (index == s.greenIndex()) {
            onHit(player, s, block);
        } else {
            onMiss(player, s);
        }
    }

    private boolean isNear(Player player, WoodcuttingSession s, Settings st) {
        Location center = s.treeLocation().center();
        if (center == null || !player.getWorld().equals(center.getWorld())) return false;
        if (st.maxDistance <= 0.0) return true;
        return player.getLocation().distanceSquared(center) <= st.maxDistance * st.maxDistance;
    }

    private void onHit(Player player, WoodcuttingSession s, Block block) {
        Settings st = plugin.settings();
        s.addHit();

        player.swingMainHand();
        st.soundSuccess.play(player);
        st.soundWoodHit.playAt(block.getLocation().add(0.5, 0.5, 0.5));
        Location fx = effectLocation(player, block);
        st.particleHitBlock.spawn(block.getWorld(), fx, block.getBlockData());
        st.particleHitCrit.spawn(block.getWorld(), fx, null);

        if (s.isComplete()) {
            complete(s, player);
            return;
        }
        plugin.messages().actionBar(player, "hit-actionbar", MessageManager.ph("percent", TextUtil.percent(s.progress())));
        relocate(s, true);
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
                    MessageManager.ph("percent", TextUtil.percent(s.progress())));
        } else {
            plugin.messages().actionBar(player, "miss-actionbar");
        }
        if (st.missRandomize) relocate(s, false);
        plugin.gui().render(s);
    }

    /** Moves the green target to a new index (different from the old one when configured). */
    private void relocate(WoodcuttingSession s, boolean afterHit) {
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

    private Location effectLocation(Player player, Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Vector dir = player.getLocation().toVector().subtract(center.toVector());
        dir.setY(0);
        if (dir.lengthSquared() > 1.0E-6) {
            center.add(dir.normalize().multiply(0.6));
        }
        return center;
    }

    // ------------------------------------------------------------------ end states

    private void complete(WoodcuttingSession s, Player player) {
        if (!s.transition(State.ACTIVE, State.COMPLETED)) return; // guarantees the reward is given only once
        Settings st = plugin.settings();
        MessageManager m = plugin.messages();

        TreeData tree = plugin.trees().get(s.treeLocation());
        endSession(s, State.COMPLETED, true);
        if (tree == null) {
            m.send(player, "cancelled");
            return;
        }
        if (plugin.cooldowns().isActive(tree)) {
            // Should be impossible thanks to the tree lock; never pay twice for the same cooldown window.
            m.send(player, "cooldown", MessageManager.ph("time", TextUtil.formatDuration(plugin.cooldowns().get(tree).remainingMillis())));
            return;
        }

        plugin.cooldowns().start(tree);
        RewardResult reward = plugin.rewards().give(player, s.treeType());

        PlayerJobData data = plugin.playerData().get(player.getUniqueId());
        data.incrementTreesCut();
        data.addSuccessfulHits(s.totalHits());
        data.addTotalRewards(reward.totalItems());
        data.addTotalEarnings(reward.money());
        JobManager.XpResult xp = plugin.jobs().addXp(data, s.treeType());
        plugin.playerData().saveAsync(data);

        st.soundComplete.play(player);
        Block block = tree.location().getBlock();
        if (block != null) {
            st.particleComplete.spawn(block.getWorld(), block.getLocation().add(0.5, 1.0, 0.5), null);
        }

        m.send(player, "success",
                MessageManager.ph("rewards", describe(reward)),
                MessageManager.ph("xp", String.valueOf(xp.xpGained())),
                MessageManager.ph("tree", s.treeType().displayName()));
        if (reward.inventoryFull()) m.send(player, "inventory-full");
        if (reward.money() > 0.0) {
            m.send(player, "money-received", MessageManager.ph("money", String.format(java.util.Locale.US, "%.2f", reward.money())));
        }
        if (xp.leveledUp()) {
            st.soundLevelUp.play(player);
            m.send(player, "level-up", MessageManager.ph("level", String.valueOf(xp.newLevel())));
        }
    }

    private String describe(RewardResult reward) {
        if (reward.items().isEmpty()) return "-";
        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<Material, Integer> e : reward.items().entrySet()) {
            joiner.add(e.getValue() + "x " + TextUtil.pretty(e.getKey()));
        }
        return joiner.toString();
    }

    private void fail(WoodcuttingSession s, Player player) {
        if (!s.transition(State.ACTIVE, State.FAILED)) return;
        TreeData tree = plugin.trees().get(s.treeLocation());
        endSession(s, State.FAILED, true);
        if (tree != null) plugin.cooldowns().startFail(tree);
        plugin.messages().send(player, "failed");
    }

    /** Called from InventoryCloseEvent for a minigame GUI. */
    public void onGuiClosed(Player player, WoodcuttingGui gui) {
        WoodcuttingSession s = sessions.get(player.getUniqueId());
        if (s == null || !s.sessionId().equals(gui.sessionId()) || s.state() != State.ACTIVE) return;

        CloseBehavior behavior = plugin.settings().closeBehavior;
        if (!player.isOnline()) behavior = CloseBehavior.CANCEL;
        switch (behavior) {
            case CANCEL -> {
                endSession(s, State.CANCELLED, false);
                plugin.messages().send(player, "cancelled");
            }
            case FAIL -> {
                if (s.transition(State.ACTIVE, State.FAILED)) {
                    TreeData tree = plugin.trees().get(s.treeLocation());
                    endSession(s, State.FAILED, false);
                    if (tree != null) plugin.cooldowns().startFail(tree);
                    plugin.messages().send(player, "failed");
                }
            }
            case RESUME -> {
                s.forceState(State.PAUSED);
                s.setPausedAt(System.currentTimeMillis());
                stopTask(s);
            }
        }
    }

    /** Removes the session and its tree lock; optionally closes the GUI. Never gives rewards. */
    private void endSession(WoodcuttingSession s, State finalState, boolean closeGui) {
        s.forceState(finalState);
        stopTask(s);
        sessions.remove(s.playerId(), s);
        locks.remove(s.treeLocation(), s.playerId());
        if (closeGui && s.gui() != null) {
            Player p = Bukkit.getPlayer(s.playerId());
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() == s.gui()) {
                p.closeInventory();
            }
        }
    }

    /** Disconnect: drop the session without reward and without leaving any state behind. */
    public void onQuit(Player player) {
        WoodcuttingSession s = sessions.get(player.getUniqueId());
        if (s != null) endSession(s, State.CANCELLED, false);
        lastInteract.remove(player.getUniqueId());
        plugin.adminModes().clear(player.getUniqueId());
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
        stopTask(s);
        if (!st.movingEnabled || st.targetSlots < 2) return;
        int period = st.movingSpeedTicks;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickMove(s), period, period);
        s.setMoveTask(task);
    }

    private void tickMove(WoodcuttingSession s) {
        if (s.state() != State.ACTIVE || sessions.get(s.playerId()) != s) {
            stopTask(s);
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

    private void stopTask(WoodcuttingSession s) {
        BukkitTask task = s.moveTask();
        if (task != null) {
            task.cancel();
            s.setMoveTask(null);
        }
    }
}
