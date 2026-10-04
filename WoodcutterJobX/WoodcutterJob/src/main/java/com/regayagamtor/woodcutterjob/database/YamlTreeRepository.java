package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.TreeData;
import com.regayagamtor.woodcutterjob.model.TreeLocation;
import com.regayagamtor.woodcutterjob.model.TreeType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** YAML implementation: plugins/WoodcutterJob/data/trees.yml (whole file is rewritten on change). */
public final class YamlTreeRepository implements TreeRepository {

    private final File file;
    private final Logger log;
    private final Map<TreeLocation, TreeData> known = new HashMap<>();

    public YamlTreeRepository(File dataFolder, Logger log) {
        File dir = new File(dataFolder, "data");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create " + dir);
        }
        this.file = new File(dir, "trees.yml");
        this.log = log;
    }

    @Override
    public synchronized Map<TreeLocation, TreeData> loadAll() {
        known.clear();
        if (file.isFile()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> m : c.getMapList("trees")) {
                try {
                    TreeType type = TreeType.parse(String.valueOf(m.get("type")));
                    if (type == null || m.get("world") == null) {
                        log.warning("Skipping invalid tree entry in trees.yml: " + m);
                        continue;
                    }
                    TreeLocation loc = new TreeLocation(String.valueOf(m.get("world")),
                            ((Number) m.get("x")).intValue(), ((Number) m.get("y")).intValue(), ((Number) m.get("z")).intValue());
                    long cooldown = m.get("cooldown-until") instanceof Number n ? n.longValue() : 0L;
                    known.put(loc, new TreeData(loc, type, cooldown));
                } catch (RuntimeException ex) {
                    log.warning("Skipping broken tree entry in trees.yml: " + m);
                }
            }
        }
        return new HashMap<>(known);
    }

    @Override
    public synchronized void save(TreeData tree) throws IOException {
        known.put(tree.location(), tree);
        write();
    }

    @Override
    public synchronized void delete(TreeLocation location) throws IOException {
        known.remove(location);
        write();
    }

    private void write() throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TreeData t : known.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", t.location().world());
            m.put("x", t.location().x());
            m.put("y", t.location().y());
            m.put("z", t.location().z());
            m.put("type", t.type().name());
            m.put("cooldown-until", t.cooldownUntil());
            list.add(m);
        }
        YamlConfiguration c = new YamlConfiguration();
        c.set("trees", list);
        c.save(file);
    }
}
