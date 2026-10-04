package com.regayagamtor.woodcutterjob.command;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.manager.AdminModeManager.Mode;
import com.regayagamtor.woodcutterjob.manager.MessageManager;
import com.regayagamtor.woodcutterjob.model.CooldownData;
import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import com.regayagamtor.woodcutterjob.model.TreeData;
import com.regayagamtor.woodcutterjob.model.TreeLocation;
import com.regayagamtor.woodcutterjob.model.TreeStatus;
import com.regayagamtor.woodcutterjob.util.TextUtil;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /woodcutter set | remove | info | list | reload | stats | cancel | help */
public final class WoodcutterCommand implements TabExecutor {

    private static final Map<String, String> PERMISSIONS = Map.of(
            "set", "woodcutter.set",
            "remove", "woodcutter.remove",
            "info", "woodcutter.info",
            "list", "woodcutter.info",
            "reload", "woodcutter.reload",
            "cancel", "woodcutter.set",
            "stats", "woodcutter.use",
            "help", "woodcutter.use");
    private static final List<String> SUBCOMMANDS = List.of("set", "remove", "info", "list", "reload", "stats", "cancel", "help");
    private static final int LIST_LIMIT = 15;

    private final WoodcutterJob plugin;

    public WoodcutterCommand(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager m = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String permission = PERMISSIONS.get(sub);
        if (permission == null) {
            m.send(sender, "unknown-subcommand");
            return true;
        }
        if (!sender.hasPermission(permission)) {
            m.send(sender, "no-permission");
            return true;
        }

        switch (sub) {
            case "set" -> startMode(sender, Mode.SET, "mode-set");
            case "remove" -> startMode(sender, Mode.REMOVE, "mode-remove");
            case "info" -> info(sender);
            case "list" -> list(sender);
            case "reload" -> {
                int cancelled = plugin.reloadAll();
                m.send(sender, "reload-done", MessageManager.ph("count", String.valueOf(cancelled)));
            }
            case "stats" -> stats(sender);
            case "cancel" -> {
                if (sender instanceof Player p) {
                    plugin.adminModes().clear(p.getUniqueId());
                    m.send(sender, "mode-cancelled");
                } else {
                    m.send(sender, "player-only");
                }
            }
            default -> m.send(sender, "help");
        }
        return true;
    }

    private void startMode(CommandSender sender, Mode mode, String messageKey) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        plugin.adminModes().set(player.getUniqueId(), mode);
        plugin.messages().send(player, messageKey,
                MessageManager.ph("seconds", String.valueOf(plugin.settings().pendingTimeoutSeconds)));
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target != null) {
            TreeData tree = plugin.trees().get(TreeLocation.of(target));
            if (tree != null) {
                plugin.trees().sendInfo(player, tree);
                return;
            }
        }
        startMode(sender, Mode.INFO, "mode-info");
    }

    private void list(CommandSender sender) {
        int valid = 0;
        int invalid = 0;
        int unloaded = 0;
        List<TreeData> trees = new ArrayList<>(plugin.trees().all());
        List<TreeStatus> statuses = new ArrayList<>(trees.size());
        for (TreeData t : trees) {
            TreeStatus status = plugin.trees().statusOf(t);
            statuses.add(status);
            switch (status) {
                case VALID -> valid++;
                case UNLOADED -> unloaded++;
                default -> invalid++;
            }
        }
        plugin.messages().send(sender, "list-header",
                MessageManager.ph("count", String.valueOf(trees.size())),
                MessageManager.ph("valid", String.valueOf(valid)),
                MessageManager.ph("invalid", String.valueOf(invalid)),
                MessageManager.ph("unloaded", String.valueOf(unloaded)));
        int shown = Math.min(LIST_LIMIT, trees.size());
        for (int i = 0; i < shown; i++) {
            TreeData t = trees.get(i);
            CooldownData cd = plugin.cooldowns().get(t);
            plugin.messages().send(sender, "list-entry",
                    MessageManager.ph("index", String.valueOf(i + 1)),
                    MessageManager.ph("world", t.location().world()),
                    MessageManager.ph("location", t.location().coords()),
                    MessageManager.ph("type", t.type().name()),
                    MessageManager.ph("status", statuses.get(i).name()),
                    MessageManager.ph("cooldown", cd.active() ? TextUtil.formatDuration(cd.remainingMillis()) : "READY"));
        }
        if (trees.size() > shown) {
            plugin.messages().send(sender, "list-more", MessageManager.ph("count", String.valueOf(trees.size() - shown)));
        }
    }

    private void stats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        PlayerJobData d = plugin.playerData().get(player.getUniqueId());
        long next = plugin.jobs().xpToNext(d);
        plugin.messages().send(player, "stats",
                MessageManager.ph("player", player.getName()),
                MessageManager.ph("level", String.valueOf(d.level())),
                MessageManager.ph("xp", String.valueOf(d.xp())),
                MessageManager.ph("xp_next", next < 0 ? "MAX" : String.valueOf(next)),
                MessageManager.ph("trees", String.valueOf(d.treesCut())),
                MessageManager.ph("hits", String.valueOf(d.successfulHits())),
                MessageManager.ph("rewards", String.valueOf(d.totalRewards())),
                MessageManager.ph("earnings", String.format(Locale.US, "%.2f", d.totalEarnings())));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix) && sender.hasPermission(PERMISSIONS.get(sub))) out.add(sub);
            }
        }
        return out;
    }
}
