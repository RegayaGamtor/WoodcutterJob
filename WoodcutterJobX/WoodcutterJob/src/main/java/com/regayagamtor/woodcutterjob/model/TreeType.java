package com.regayagamtor.woodcutterjob.model;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Supported tree types and the block materials that belong to each. */
public enum TreeType {
    OAK(Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD),
    BIRCH(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD),
    SPRUCE(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD),
    JUNGLE(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD),
    ACACIA(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD),
    DARK_OAK(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD),
    MANGROVE(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD),
    CHERRY(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD);

    private static final Map<Material, TreeType> BY_MATERIAL = new EnumMap<>(Material.class);

    static {
        for (TreeType type : values()) {
            for (Material material : type.materials) {
                BY_MATERIAL.put(material, type);
            }
        }
    }

    private final Material defaultLog;
    private final Set<Material> materials;

    TreeType(Material... materials) {
        this.defaultLog = materials[0];
        this.materials = EnumSet.copyOf(Arrays.asList(materials));
    }

    public Material defaultLog() {
        return defaultLog;
    }

    /** Human readable name, e.g. "Dark Oak". */
    public String displayName() {
        StringBuilder sb = new StringBuilder();
        for (String part : name().toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** @return the tree type of a block material, or null if it is not a supported log. */
    public static TreeType fromMaterial(Material material) {
        return BY_MATERIAL.get(material);
    }

    /** @return the tree type for a name (case-insensitive), or null. */
    public static TreeType parse(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
