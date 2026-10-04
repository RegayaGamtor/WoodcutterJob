package com.regayagamtor.woodcutterjob.reward;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.RewardData;
import com.regayagamtor.woodcutterjob.model.TreeType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rolls and hands out rewards. Items that do not fit are dropped, never deleted. */
public final class RewardManager {

    private final WoodcutterJob plugin;

    public RewardManager(WoodcutterJob plugin) {
        this.plugin = plugin;
    }

    public RewardResult give(Player player, TreeType type) {
        Map<Material, Integer> summary = new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        int total = 0;

        for (RewardData reward : plugin.settings().rewardsFor(type)) {
            if (!reward.rollChance()) continue;
            int amount = reward.rollAmount();
            if (amount <= 0) continue;
            summary.merge(reward.material(), amount, Integer::sum);
            total += amount;
            int max = Math.max(1, reward.material().getMaxStackSize());
            int remaining = amount;
            while (remaining > 0) {
                int part = Math.min(max, remaining);
                stacks.add(new ItemStack(reward.material(), part));
                remaining -= part;
            }
        }

        boolean full = false;
        if (!stacks.isEmpty()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
            for (ItemStack left : leftovers.values()) {
                full = true;
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        double money = 0.0;
        double configured = plugin.settings().moneyFor(type);
        if (plugin.settings().moneyEnabled && configured > 0.0 && plugin.vault().isAvailable()) {
            if (plugin.vault().deposit(player, configured)) money = configured;
        }
        return new RewardResult(summary, total, money, full);
    }
}
