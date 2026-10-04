package com.regayagamtor.woodcutterjob.command;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.manager.MessageManager;
import com.regayagamtor.woodcutterjob.manager.RegionManager;
import com.regayagamtor.woodcutterjob.manager.SelectionManager.Point;
import com.regayagamtor.woodcutterjob.model.CooldownData;
import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import com.regayagamtor.woodcutterjob.model.RegionData;
import com.regayagamtor.woodcutterjob.model.RewardData;
import com.regayagamtor.woodcutterjob.model.TreeType;
import com.regayagamtor.woodcutterjob.reward.RewardResult;
import com.regayagamtor.woodcutterjob.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /woodcutter pos1 | pos2 | region | cooldown | reward | give | list | reload | stats | help
 * Everything that changes cooldowns or rewards is written to config.yml, so it survives restarts.
 */
public final class WoodcutterCommand implements TabExecutor {

    private static final Map<String, String> PERMISSIONS = Map.ofEntries(
            Map.entry("pos1", "woodcutter.region"),
            Map.entry("pos2", "woodcutter.region"),
            Map.entry("region", "woodcutter.region"),
            Map.entry("list", "woodcutter.info"),
            Map.entry("cooldown", "woodcutter.cooldown"),
            Map.entry("reward", "woodcutter.reward"),
            Map.entry("give", "woodcutter.give"),
            Map.entry("reload", "woodcutter.reload"),
            Map.entry("stats", "woodcutter.use"),
            Map.entry("help", "woodcutter.use"));
    private static final List<String> SUBCOMMANDS =
            List.of("pos1", "pos2", "region", "list", "cooldown", "reward", "give", "reload", "stats", "help");
    private static final int LIST_LIMIT = 15;
    private static final long MAX_COOLDOWN_SECONDS = 31_536_000L;
    private static final int MAX_AMOUNT = 2304;
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([smhd])", Pattern.CASE_INSENSITIVE);

    private static List<String> materialNames;

    private final WoodcutterJob plugin;

    public WoodcutterCommand(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    // ================================================================== dispatch

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager m = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String permission = PERMISSIONS.get(sub);
        if (permission == null) {
            m.send(sender, "unknown-subcommand");
            return true;
        }
        boolean allowed = sender.hasPermission(permission)
                || (sub.equals("region") && sender.hasPermission("woodcutter.info"));
        if (!allowed) {
            m.send(sender, "no-permission");
            return true;
        }

        switch (sub) {
            case "pos1" -> pos(sender, args, 0);
            case "pos2" -> pos(sender, args, 1);
            case "region" -> region(sender, args);
            case "list" -> regionList(sender);
            case "cooldown" -> cooldown(sender, args);
            case "reward" -> reward(sender, args);
            case "give" -> give(sender, args);
            case "reload" -> {
                int cancelled = plugin.reloadAll();
                m.send(sender, "reload-done", MessageManager.ph("count", String.valueOf(cancelled)));
            }
            case "stats" -> stats(sender);
            default -> m.send(sender, "help");
        }
        return true;
    }

    private boolean requirePerm(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    // ================================================================== pos1 / pos2

    private void pos(CommandSender sender, String[] a, int index) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        Location base = player.getLocation();
        int x = base.getBlockX();
        int y = base.getBlockY();
        int z = base.getBlockZ();
        if (a.length >= 4) {
            Integer px = parseCoord(a[1], x);
            Integer py = parseCoord(a[2], y);
            Integer pz = parseCoord(a[3], z);
            if (px == null || py == null || pz == null) {
                plugin.messages().send(sender, "invalid-coords");
                return;
            }
            x = px;
            y = py;
            z = pz;
        } else if (a.length != 1) {
            plugin.messages().send(sender, "usage-pos");
            return;
        }
        Point point = new Point(player.getWorld().getName(), x, y, z);
        plugin.selections().set(player.getUniqueId(), index, point);
        plugin.messages().send(sender, "pos-set",
                MessageManager.ph("index", String.valueOf(index + 1)),
                MessageManager.ph("location", point.coords()),
                MessageManager.ph("world", point.world()));
    }

    // ================================================================== region

    private void region(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 2) {
            m.send(sender, "usage-region");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "create" -> regionCreate(sender, a);
            case "remove", "delete" -> regionRemove(sender, a);
            case "settype" -> regionSetType(sender, a);
            case "list" -> {
                if (requirePerm(sender, "woodcutter.info")) regionList(sender);
            }
            case "info" -> regionInfo(sender, a);
            default -> m.send(sender, "usage-region");
        }
    }

    /** /woodcutter region create <name> <type> [x1 y1 z1 x2 y2 z2 [world]] */
    private void regionCreate(CommandSender sender, String[] a) {
        if (!requirePerm(sender, "woodcutter.region")) return;
        MessageManager m = plugin.messages();
        if (a.length < 4 || (a.length > 4 && a.length < 10)) {
            m.send(sender, "usage-region-create");
            return;
        }
        String name = a[2];
        if (!RegionManager.isValidName(name)) {
            m.send(sender, "region-invalid-name");
            return;
        }
        if (plugin.regions().get(name) != null) {
            m.send(sender, "region-exists", MessageManager.ph("name", name));
            return;
        }
        TreeType type = TreeType.parse(a[3]);
        if (type == null) {
            m.send(sender, "invalid-type", MessageManager.ph("types", typeList()));
            return;
        }

        int x1;
        int y1;
        int z1;
        int x2;
        int y2;
        int z2;
        String world;
        if (a.length >= 10) {
            Location base = sender instanceof Player p ? p.getLocation() : null;
            Integer[] v = new Integer[6];
            for (int i = 0; i < 6; i++) {
                Integer baseValue = base == null ? null : switch (i % 3) {
                    case 0 -> base.getBlockX();
                    case 1 -> base.getBlockY();
                    default -> base.getBlockZ();
                };
                v[i] = parseCoord(a[4 + i], baseValue);
                if (v[i] == null) {
                    m.send(sender, "invalid-coords");
                    return;
                }
            }
            x1 = v[0];
            y1 = v[1];
            z1 = v[2];
            x2 = v[3];
            y2 = v[4];
            z2 = v[5];
            if (a.length >= 11) {
                world = a[10];
            } else if (sender instanceof Player p) {
                world = p.getWorld().getName();
            } else {
                m.send(sender, "world-required");
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                m.send(sender, "player-only");
                return;
            }
            Point[] sel = plugin.selections().get(player.getUniqueId());
            if (sel == null || sel[0] == null || sel[1] == null) {
                m.send(sender, "selection-incomplete");
                return;
            }
            if (!sel[0].world().equals(sel[1].world())) {
                m.send(sender, "selection-world-mismatch");
                return;
            }
            x1 = sel[0].x();
            y1 = sel[0].y();
            z1 = sel[0].z();
            x2 = sel[1].x();
            y2 = sel[1].y();
            z2 = sel[1].z();
            world = sel[0].world();
        }
        org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            m.send(sender, "world-not-found", MessageManager.ph("world", world));
            return;
        }
        world = bukkitWorld.getName(); // canonical capitalisation

        RegionData region = plugin.regions().create(name, world, x1, y1, z1, x2, y2, z2, type);
        if (region == null) {
            m.send(sender, "region-exists", MessageManager.ph("name", name));
            return;
        }
        m.send(sender, "region-created",
                MessageManager.ph("name", region.name()),
                MessageManager.ph("type", type.name()),
                MessageManager.ph("world", world),
                MessageManager.ph("coords", region.coords()),
                MessageManager.ph("volume", String.valueOf(region.volume())));
    }

    private void regionRemove(CommandSender sender, String[] a) {
        if (!requirePerm(sender, "woodcutter.region")) return;
        MessageManager m = plugin.messages();
        if (a.length < 3) {
            m.send(sender, "usage-region");
            return;
        }
        RegionData region = plugin.regions().get(a[2]);
        if (region == null) {
            m.send(sender, "region-not-found", MessageManager.ph("name", a[2]));
            return;
        }
        plugin.woodcutting().cancelRegion(region.name());
        plugin.regions().remove(region.name());
        m.send(sender, "region-removed", MessageManager.ph("name", region.name()));
    }

    private void regionSetType(CommandSender sender, String[] a) {
        if (!requirePerm(sender, "woodcutter.region")) return;
        MessageManager m = plugin.messages();
        if (a.length < 4) {
            m.send(sender, "usage-region");
            return;
        }
        RegionData region = plugin.regions().get(a[2]);
        if (region == null) {
            m.send(sender, "region-not-found", MessageManager.ph("name", a[2]));
            return;
        }
        TreeType type = TreeType.parse(a[3]);
        if (type == null) {
            m.send(sender, "invalid-type", MessageManager.ph("types", typeList()));
            return;
        }
        region.setType(type);
        plugin.regions().save(region);
        m.send(sender, "region-type-set",
                MessageManager.ph("name", region.name()),
                MessageManager.ph("type", type.name()));
    }

    private void regionInfo(CommandSender sender, String[] a) {
        if (!requirePerm(sender, "woodcutter.info")) return;
        MessageManager m = plugin.messages();
        if (a.length < 3) {
            m.send(sender, "usage-region");
            return;
        }
        RegionData region = plugin.regions().get(a[2]);
        if (region == null) {
            m.send(sender, "region-not-found", MessageManager.ph("name", a[2]));
            return;
        }
        CooldownData cd = plugin.cooldowns().get(region);
        m.send(sender, "region-info",
                MessageManager.ph("name", region.name()),
                MessageManager.ph("world", region.world()),
                MessageManager.ph("coords", region.coords()),
                MessageManager.ph("volume", String.valueOf(region.volume())),
                MessageManager.ph("type", region.type().name()),
                MessageManager.ph("cooldown", cd.active() ? TextUtil.formatDuration(cd.remainingMillis()) : "READY"),
                MessageManager.ph("status", region.getWorld() == null ? "WORLD NOT LOADED" : "OK"));
    }

    private void regionList(CommandSender sender) {
        List<RegionData> list = new ArrayList<>(plugin.regions().all());
        list.sort(Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT)));
        plugin.messages().send(sender, "region-list-header", MessageManager.ph("count", String.valueOf(list.size())));
        int shown = Math.min(LIST_LIMIT, list.size());
        for (int i = 0; i < shown; i++) {
            RegionData r = list.get(i);
            CooldownData cd = plugin.cooldowns().get(r);
            plugin.messages().send(sender, "region-list-entry",
                    MessageManager.ph("index", String.valueOf(i + 1)),
                    MessageManager.ph("name", r.name()),
                    MessageManager.ph("world", r.world()),
                    MessageManager.ph("coords", r.coords()),
                    MessageManager.ph("type", r.type().name()),
                    MessageManager.ph("cooldown", cd.active() ? TextUtil.formatDuration(cd.remainingMillis()) : "READY"));
        }
        if (list.size() > shown) {
            plugin.messages().send(sender, "region-list-more", MessageManager.ph("count", String.valueOf(list.size() - shown)));
        }
    }

    // ================================================================== cooldown

    /** /woodcutter cooldown [show] | <default|fail|all|TYPE> <time> | reset <region|all> */
    private void cooldown(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length == 1 || a[1].equalsIgnoreCase("show")) {
            cooldownShow(sender);
            return;
        }
        if (a[1].equalsIgnoreCase("reset")) {
            cooldownReset(sender, a);
            return;
        }
        if (a.length < 3) {
            m.send(sender, "usage-cooldown");
            return;
        }
        Long seconds = parseDuration(a[2]);
        if (seconds == null) {
            m.send(sender, "invalid-duration");
            return;
        }
        FileConfiguration c = plugin.getConfig();
        String target = a[1].toUpperCase(Locale.ROOT);
        String shownTarget;
        switch (target) {
            case "DEFAULT" -> {
                c.set("cooldown.default-seconds", seconds);
                shownTarget = "default";
            }
            case "FAIL" -> {
                c.set("cooldown.fail-seconds", seconds);
                shownTarget = "fail";
            }
            case "ALL" -> {
                c.set("cooldown.default-seconds", seconds);
                for (TreeType t : TreeType.values()) c.set("cooldown.per-type." + t.name(), seconds);
                shownTarget = "semua tipe";
            }
            default -> {
                TreeType type = TreeType.parse(target);
                if (type == null) {
                    m.send(sender, "invalid-cooldown-target", MessageManager.ph("types", typeList()));
                    return;
                }
                c.set("cooldown.per-type." + type.name(), seconds);
                shownTarget = type.name();
            }
        }
        plugin.saveConfigAndRefresh();
        m.send(sender, "cooldown-set",
                MessageManager.ph("target", shownTarget),
                MessageManager.ph("time", seconds == 0 ? "0s" : TextUtil.formatDuration(seconds * 1000L)));
    }

    private void cooldownShow(CommandSender sender) {
        var s = plugin.settings();
        StringJoiner types = new StringJoiner(", ");
        for (TreeType t : TreeType.values()) {
            types.add(t.name() + "=" + TextUtil.formatDuration(s.cooldownSeconds(t) * 1000L));
        }
        plugin.messages().send(sender, "cooldown-show",
                MessageManager.ph("default", TextUtil.formatDuration(s.cooldownDefaultSeconds * 1000L)),
                MessageManager.ph("fail", TextUtil.formatDuration(s.cooldownFailSeconds * 1000L)),
                MessageManager.ph("types", types.toString()));
    }

    private void cooldownReset(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 3) {
            m.send(sender, "usage-cooldown");
            return;
        }
        if (a[2].equalsIgnoreCase("all")) {
            for (RegionData r : plugin.regions().all()) plugin.cooldowns().clear(r);
            m.send(sender, "cooldown-reset-all", MessageManager.ph("count", String.valueOf(plugin.regions().count())));
            return;
        }
        RegionData region = plugin.regions().get(a[2]);
        if (region == null) {
            m.send(sender, "region-not-found", MessageManager.ph("name", a[2]));
            return;
        }
        plugin.cooldowns().clear(region);
        m.send(sender, "cooldown-reset", MessageManager.ph("name", region.name()));
    }

    // ================================================================== reward

    private void reward(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 2) {
            m.send(sender, "usage-reward");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "list" -> rewardList(sender, a);
            case "add" -> rewardAdd(sender, a);
            case "remove" -> rewardRemove(sender, a);
            case "money" -> rewardMoney(sender, a);
            default -> m.send(sender, "usage-reward");
        }
    }

    private void rewardList(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 3) {
            m.send(sender, "usage-reward");
            return;
        }
        TreeType type = TreeType.parse(a[2]);
        if (type == null) {
            m.send(sender, "invalid-type", MessageManager.ph("types", typeList()));
            return;
        }
        List<RewardData> list = plugin.settings().rewardsFor(type);
        m.send(sender, "reward-list-header", MessageManager.ph("type", type.name()));
        int i = 1;
        for (RewardData r : list) {
            m.send(sender, "reward-list-entry",
                    MessageManager.ph("index", String.valueOf(i++)),
                    MessageManager.ph("item", r.label() + (r.isCustom() ? " (custom)" : "")),
                    MessageManager.ph("material", r.material().name()),
                    MessageManager.ph("amount", r.amountText()),
                    MessageManager.ph("chance", trimNumber(r.chance())));
        }
        m.send(sender, "reward-list-money",
                MessageManager.ph("money", trimNumber(plugin.settings().moneyFor(type))),
                MessageManager.ph("enabled", plugin.settings().moneyEnabled ? "ON" : "OFF"));
    }

    /** /woodcutter reward add <TYPE> <MATERIAL|hand> <amount|min-max> [chance] */
    private void rewardAdd(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 5) {
            m.send(sender, "usage-reward");
            return;
        }
        TreeType type = TreeType.parse(a[2]);
        if (type == null) {
            m.send(sender, "invalid-type", MessageManager.ph("types", typeList()));
            return;
        }

        Material material;
        ItemStack template = null;
        if (a[3].equalsIgnoreCase("hand")) {
            if (!(sender instanceof Player player)) {
                m.send(sender, "player-only");
                return;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                m.send(sender, "reward-hand-empty");
                return;
            }
            material = held.getType();
            if (held.hasItemMeta()) {
                template = held.clone();
                template.setAmount(1);
            }
        } else {
            material = Material.matchMaterial(a[3]);
            if (material == null || !material.isItem() || material.isAir()) {
                m.send(sender, "reward-invalid-item", MessageManager.ph("item", a[3]));
                return;
            }
        }

        int[] range = parseAmount(a[4]);
        if (range == null) {
            m.send(sender, "invalid-amount");
            return;
        }
        double chance = 100.0;
        if (a.length >= 6) {
            Double parsed = parseDouble(a[5].endsWith("%") ? a[5].substring(0, a[5].length() - 1) : a[5]);
            if (parsed == null || parsed < 0.0 || parsed > 100.0) {
                m.send(sender, "reward-invalid-chance");
                return;
            }
            chance = parsed;
        }

        RewardData added = new RewardData(material, range[0], range[1], chance, template);
        List<Map<String, Object>> maps = new ArrayList<>();
        for (RewardData r : plugin.settings().rewardsFor(type)) maps.add(r.toMap());
        maps.add(added.toMap());
        plugin.getConfig().set("rewards." + type.name() + ".items", maps);
        plugin.saveConfigAndRefresh();

        m.send(sender, "reward-added",
                MessageManager.ph("type", type.name()),
                MessageManager.ph("item", added.label() + (added.isCustom() ? " (custom)" : "")),
                MessageManager.ph("amount", added.amountText()),
                MessageManager.ph("chance", trimNumber(chance)),
                MessageManager.ph("index", String.valueOf(maps.size())));
    }

    private void rewardRemove(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 4) {
            m.send(sender, "usage-reward");
            return;
        }
        TreeType type = TreeType.parse(a[2]);
        if (type == null) {
            m.send(sender, "invalid-type", MessageManager.ph("types", typeList()));
            return;
        }
        Integer index = parseInt(a[3]);
        List<RewardData> current = plugin.settings().rewardsFor(type);
        if (index == null || index < 1 || index > current.size()) {
            m.send(sender, "reward-invalid-index", MessageManager.ph("max", String.valueOf(current.size())));
            return;
        }
        if (current.size() == 1) {
            m.send(sender, "reward-last-item");
            return;
        }
        RewardData removed = current.get(index - 1);
        List<Map<String, Object>> maps = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            if (i != index - 1) maps.add(current.get(i).toMap());
        }
        plugin.getConfig().set("rewards." + type.name() + ".items", maps);
        plugin.saveConfigAndRefresh();
        m.send(sender, "reward-removed",
                MessageManager.ph("type", type.name()),
                MessageManager.ph("item", removed.label()));
    }

    /** /woodcutter reward money enable|disable  or  /woodcutter reward money <TYPE> <amount> */
    private void rewardMoney(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 3) {
            m.send(sender, "usage-reward");
            return;
        }
        FileConfiguration c = plugin.getConfig();
        String op = a[2].toLowerCase(Locale.ROOT);
        if (op.equals("enable") || op.equals("disable") || op.equals("on") || op.equals("off")) {
            boolean on = op.equals("enable") || op.equals("on");
            c.set("reward-money.enabled", on);
            plugin.saveConfigAndRefresh();
            m.send(sender, "reward-money-toggle", MessageManager.ph("state", on ? "ON" : "OFF"));
            if (on && !plugin.vault().isAvailable()) m.send(sender, "reward-vault-missing");
            return;
        }
        TreeType type = TreeType.parse(a[2]);
        if (type == null) {
            m.send(sender, "invalid-type", MessageManager.ph("types", typeList()));
            return;
        }
        Double amount = a.length >= 4 ? parseDouble(a[3]) : null;
        if (amount == null || amount < 0.0) {
            m.send(sender, "usage-reward");
            return;
        }
        c.set("reward-money." + type.name(), amount);
        plugin.saveConfigAndRefresh();
        m.send(sender, "reward-money-set",
                MessageManager.ph("type", type.name()),
                MessageManager.ph("money", trimNumber(amount)));
        if (!plugin.settings().moneyEnabled) m.send(sender, "reward-money-disabled");
        else if (!plugin.vault().isAvailable()) m.send(sender, "reward-vault-missing");
    }

    // ================================================================== give

    /** /woodcutter give <player> <TYPE>  (rolls that tree's rewards)  or  <player> <material> [amount] */
    private void give(CommandSender sender, String[] a) {
        MessageManager m = plugin.messages();
        if (a.length < 3) {
            m.send(sender, "usage-give");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            m.send(sender, "player-not-found", MessageManager.ph("player", a[1]));
            return;
        }

        TreeType type = TreeType.parse(a[2]);
        if (type != null) {
            RewardResult result = plugin.rewards().give(target, type);
            m.send(sender, "give-reward-sender",
                    MessageManager.ph("player", target.getName()),
                    MessageManager.ph("tree", type.displayName()),
                    MessageManager.ph("rewards", result.describe()));
            m.send(target, "give-reward-target",
                    MessageManager.ph("tree", type.displayName()),
                    MessageManager.ph("rewards", result.describe()));
            if (result.inventoryFull()) m.send(target, "inventory-full");
            return;
        }

        Material material = Material.matchMaterial(a[2]);
        if (material == null || !material.isItem() || material.isAir()) {
            m.send(sender, "reward-invalid-item", MessageManager.ph("item", a[2]));
            return;
        }
        int amount = 1;
        if (a.length >= 4) {
            Integer parsed = parseInt(a[3]);
            if (parsed == null || parsed < 1 || parsed > MAX_AMOUNT) {
                m.send(sender, "invalid-amount");
                return;
            }
            amount = parsed;
        }
        boolean full = plugin.rewards().giveItem(target, new ItemStack(material), amount);
        String label = amount + "x " + TextUtil.pretty(material);
        m.send(sender, "give-item-sender",
                MessageManager.ph("player", target.getName()),
                MessageManager.ph("item", label));
        m.send(target, "give-item-target", MessageManager.ph("item", label));
        if (full) m.send(target, "inventory-full");
    }

    // ================================================================== stats

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

    // ================================================================== parsing helpers

    private static String typeList() {
        StringJoiner j = new StringJoiner(", ");
        for (TreeType t : TreeType.values()) j.add(t.name());
        return j.toString();
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        try {
            double v = Double.parseDouble(raw.trim());
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** "12", "-5", "~" or "~3" (relative to {@code base}; null base = relative not allowed). */
    private static Integer parseCoord(String raw, Integer base) {
        if (raw.startsWith("~")) {
            if (base == null) return null;
            if (raw.length() == 1) return base;
            Integer offset = parseInt(raw.substring(1));
            return offset == null ? null : base + offset;
        }
        return parseInt(raw);
    }

    /** "5" -> [5,5], "2-6" -> [2,6]; both within 1..2304, min <= max. */
    private static int[] parseAmount(String raw) {
        String[] parts = raw.split("-", -1);
        Integer min;
        Integer max;
        if (parts.length == 1) {
            min = parseInt(parts[0]);
            max = min;
        } else if (parts.length == 2) {
            min = parseInt(parts[0]);
            max = parseInt(parts[1]);
        } else {
            return null;
        }
        if (min == null || max == null || min < 1 || max < min || max > MAX_AMOUNT) return null;
        return new int[] {min, max};
    }

    /** "300" (seconds), "5m", "1h30m", "2d" -> seconds; null if invalid. Max one year. */
    private static Long parseDuration(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        Integer plain = parseInt(v);
        if (plain != null) return plain < 0 || plain > MAX_COOLDOWN_SECONDS ? null : (long) plain;

        Matcher matcher = DURATION_PART.matcher(v);
        long total = 0L;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) return null;
            consumed = matcher.end();
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2).charAt(0)) {
                case 's' -> amount;
                case 'm' -> amount * 60L;
                case 'h' -> amount * 3600L;
                default -> amount * 86400L;
            };
            if (total > MAX_COOLDOWN_SECONDS) return null;
        }
        return consumed == v.length() && consumed > 0 ? total : null;
    }

    private static String trimNumber(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }

    // ================================================================== tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sender.hasPermission(PERMISSIONS.get(sub))) out.add(sub);
            }
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String current = args[args.length - 1];
        switch (sub) {
            case "region" -> {
                if (args.length == 2) {
                    out.addAll(List.of("create", "remove", "settype", "list", "info"));
                } else {
                    String op = args[1].toLowerCase(Locale.ROOT);
                    if (args.length == 3 && (op.equals("remove") || op.equals("settype") || op.equals("info"))) {
                        out.addAll(regionNames());
                    } else if ((args.length == 4 && (op.equals("settype") || op.equals("create")))) {
                        out.addAll(typeNames());
                    } else if (op.equals("create") && args.length >= 5 && args.length <= 10) {
                        out.add("~");
                    }
                }
            }
            case "cooldown" -> {
                if (args.length == 2) {
                    out.addAll(List.of("show", "reset", "default", "fail", "all"));
                    out.addAll(typeNames());
                } else if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("reset")) {
                        out.add("all");
                        out.addAll(regionNames());
                    } else {
                        out.addAll(List.of("30", "60", "5m", "30m", "1h"));
                    }
                }
            }
            case "reward" -> {
                if (args.length == 2) {
                    out.addAll(List.of("list", "add", "remove", "money"));
                } else if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("money")) out.addAll(List.of("enable", "disable"));
                    out.addAll(typeNames());
                } else if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
                    out.add("hand");
                    out.addAll(materialMatches(current));
                } else if (args.length == 5 && args[1].equalsIgnoreCase("add")) {
                    out.addAll(List.of("1", "5", "1-3", "2-5"));
                } else if (args.length == 6 && args[1].equalsIgnoreCase("add")) {
                    out.addAll(List.of("100", "50", "25", "10", "1"));
                }
            }
            case "give" -> {
                if (args.length == 2) {
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                } else if (args.length == 3) {
                    out.addAll(typeNames());
                    out.addAll(materialMatches(current));
                } else if (args.length == 4) {
                    out.addAll(List.of("1", "16", "64"));
                }
            }
            default -> { }
        }
        return filter(out, current);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
        return out;
    }

    private List<String> regionNames() {
        List<String> names = new ArrayList<>();
        for (RegionData r : plugin.regions().all()) names.add(r.name());
        return names;
    }

    private static List<String> typeNames() {
        List<String> names = new ArrayList<>();
        for (TreeType t : TreeType.values()) names.add(t.name());
        return names;
    }

    /** Item names matching the typed prefix (capped: there are over a thousand items). */
    private static List<String> materialMatches(String prefix) {
        if (materialNames == null) {
            List<String> all = new ArrayList<>();
            for (Material mat : Material.values()) {
                if (mat.isItem() && !mat.isAir() && !mat.name().startsWith("LEGACY_")) {
                    all.add(mat.name().toLowerCase(Locale.ROOT));
                }
            }
            materialNames = all;
        }
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : materialNames) {
            if (name.startsWith(p)) {
                out.add(name);
                if (out.size() >= 40) break;
            }
        }
        return out;
    }
}
