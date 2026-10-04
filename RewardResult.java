package com.regayagamtor.woodcutterjob.reward;

import java.util.Map;
import java.util.StringJoiner;

/** What a player received. {@code items} maps a display label to the total amount. */
public record RewardResult(Map<String, Integer> items, int totalItems, double money, boolean inventoryFull) {

    /** "8x Oak Log, 2x Stick" or "-" when nothing dropped. */
    public String describe() {
        if (items.isEmpty()) return "-";
        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            joiner.add(e.getValue() + "x " + e.getKey());
        }
        return joiner.toString();
    }
}
