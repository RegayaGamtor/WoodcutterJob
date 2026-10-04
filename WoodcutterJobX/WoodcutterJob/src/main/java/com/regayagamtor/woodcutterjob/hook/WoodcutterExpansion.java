package com.regayagamtor.woodcutterjob.hook;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** %woodcutter_level%, %woodcutter_xp%, %woodcutter_trees_cut%, %woodcutter_successful_hits% (+ extras). */
public final class WoodcutterExpansion extends PlaceholderExpansion {

    private final WoodcutterJob plugin;

    public WoodcutterExpansion(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "woodcutter";
    }

    @Override
    public @NotNull String getAuthor() {
        return "RegayaGamtor";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        PlayerJobData data = plugin.playerData().peek(player.getUniqueId());
        String key = params.toLowerCase(Locale.ROOT);
        if (data == null) {
            return key.equals("level") ? "1" : "0";
        }
        return switch (key) {
            case "level" -> String.valueOf(data.level());
            case "xp" -> String.valueOf(data.xp());
            case "trees_cut" -> String.valueOf(data.treesCut());
            case "successful_hits" -> String.valueOf(data.successfulHits());
            case "total_rewards" -> String.valueOf(data.totalRewards());
            case "total_earnings" -> String.format(Locale.US, "%.2f", data.totalEarnings());
            case "xp_next" -> {
                long next = plugin.jobs().xpToNext(data);
                yield next < 0 ? "MAX" : String.valueOf(next);
            }
            default -> null;
        };
    }
}
