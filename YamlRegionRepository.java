package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.RegionData;
import com.regayagamtor.woodcutterjob.model.TreeType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** YAML implementation: plugins/WoodcutterJob/data/regions.yml (whole file is rewritten on change). */
public final class YamlRegionRepository implements RegionRepository {

    private final File file;
    private final Logger log;
    private final Map<String, RegionData> known = new HashMap<>();

    public YamlRegionRepository(File dataFolder, Logger log) {
        File dir = new File(dataFolder, "data");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create " + dir);
        }
        this.file = new File(dir, "regions.yml");
        this.log = log;
    }

    @Override
    public synchronized Map<String, RegionData> loadAll() {
        known.clear();
        if (file.isFile()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> m : c.getMapList("regions")) {
                try {
                    TreeType type = TreeType.parse(String.valueOf(m.get("type")));
                    if (type == null || m.get("name") == null || m.get("world") == null) {
                        log.warning("Skipping invalid region entry in regions.yml: " + m);
                        continue;
                    }
                    long cooldown = m.get("cooldown-until") instanceof Number n ? n.longValue() : 0L;
                    RegionData region = new RegionData(String.valueOf(m.get("name")), String.valueOf(m.get("world")),
                            num(m, "min-x"), num(m, "min-y"), num(m, "min-z"),
                            num(m, "max-x"), num(m, "max-y"), num(m, "max-z"), type, cooldown);
                    known.put(region.name().toLowerCase(Locale.ROOT), region);
                } catch (RuntimeException ex) {
                    log.warning("Skipping broken region entry in regions.yml: " + m);
                }
            }
        }
        return new HashMap<>(known);
    }

    private static int num(Map<?, ?> m, String key) {
        return ((Number) m.get(key)).intValue();
    }

    @Override
    public synchronized void save(RegionData region) throws IOException {
        known.put(region.name().toLowerCase(Locale.ROOT), region);
        write();
    }

    @Override
    public synchronized void delete(String name) throws IOException {
        known.remove(name.toLowerCase(Locale.ROOT));
        write();
    }

    private void write() throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RegionData r : known.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.name());
            m.put("world", r.world());
            m.put("min-x", r.minX());
            m.put("min-y", r.minY());
            m.put("min-z", r.minZ());
            m.put("max-x", r.maxX());
            m.put("max-y", r.maxY());
            m.put("max-z", r.maxZ());
            m.put("type", r.type().name());
            m.put("cooldown-until", r.cooldownUntil());
            list.add(m);
        }
        YamlConfiguration c = new YamlConfiguration();
        c.set("regions", list);
        c.save(file);
    }
}
