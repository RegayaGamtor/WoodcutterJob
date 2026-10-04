package com.regayagamtor.woodcutterjob.reward;

import com.regayagamtor.woodcutterjob.WoodcutterJob;
import com.regayagamtor.woodcutterjob.model.RewardData;
import com.regayagamtor.woodcutterjob.model.TreeType;
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

    /** Rolls every reward entry of the tree type (chance + amount) and gives items and money. */
    public RewardResult give(Player player, TreeType type) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        int total = 0;

        for (RewardData reward : plugin.settings().rewardsFor(type)) {
            if (!reward.rollChance()) continue;
            int amount = reward.rollAmount();
            if (amount <= 0) continue;
            summary.merge(reward.label(), amount, Integer::sum);
            total += amount;
            split(stacks, reward.createStack(1), amount);
        }

        boolean full = deliver(player, stacks);

        double money = 0.0;
        double configured = plugin.settings().moneyFor(type);
        if (plugin.settings().moneyEnabled && configured > 0.0 && plugin.vault().isAvailable()) {
            if (plugin.vault().deposit(player, configured)) money = configured;
        }
        return new RewardResult(summary, total, money, full);
    }

    /** Gives {@code amount} of a stack (any item, split into legal stack sizes). @return true if some items were dropped. */
    public boolean giveItem(Player player, ItemStack base, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        split(stacks, base, amount);
        return deliver(player, stacks);
    }

    private static void split(List<ItemStack> out, ItemStack base, int amount) {
        int max = Math.max(1, base.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int part = Math.min(max, remaining);
            ItemStack stack = base.clone();
            stack.setAmount(part);
            out.add(stack);
            remaining -= part;
        }
    }

    private boolean deliver(Player player, List<ItemStack> stacks) {
        boolean full = false;
        if (!stacks.isEmpty()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
            for (ItemStack left : leftovers.values()) {
                full = true;
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        return full;
    }
}
