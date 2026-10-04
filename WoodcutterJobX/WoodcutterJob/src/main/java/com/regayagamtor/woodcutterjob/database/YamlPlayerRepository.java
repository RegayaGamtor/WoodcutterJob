package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.PlayerJobData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** YAML implementation: plugins/WoodcutterJob/data/&lt;uuid&gt;.yml */
public final class YamlPlayerRepository implements PlayerRepository {

    private final File dir;

    public YamlPlayerRepository(File dataFolder) {
        this.dir = new File(dataFolder, "data");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create " + dir);
        }
    }

    @Override
    public synchronized PlayerJobData load(UUID uuid) {
        File file = new File(dir, uuid + ".yml");
        if (!file.isFile()) return null;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        PlayerJobData data = new PlayerJobData(uuid);
        data.restore(c.getInt("level", 1), c.getLong("xp", 0L), c.getInt("trees-cut", 0),
                c.getInt("successful-hits", 0), c.getLong("total-rewards", 0L), c.getDouble("total-earnings", 0.0));
        return data;
    }

    @Override
    public synchronized void save(PlayerJobData data) throws IOException {
        YamlConfiguration c = new YamlConfiguration();
        c.set("uuid", data.uuid().toString());
        c.set("level", data.level());
        c.set("xp", data.xp());
        c.set("trees-cut", data.treesCut());
        c.set("successful-hits", data.successfulHits());
        c.set("total-rewards", data.totalRewards());
        c.set("total-earnings", data.totalEarnings());
        c.save(new File(dir, data.uuid() + ".yml"));
    }
}
