package com.regayagamtor.woodcutterjob.reward;

import org.bukkit.Material;

import java.util.Map;

/** What a player received for a finished tree. */
public record RewardResult(Map<Material, Integer> items, int totalItems, double money, boolean inventoryFull) {}
