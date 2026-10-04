package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.RegionData;
import com.regayagamtor.woodcutterjob.model.TreeType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class SqliteRegionRepository implements RegionRepository {

    private final SQLiteManager db;
    private final Logger log;

    public SqliteRegionRepository(SQLiteManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    @Override
    public Map<String, RegionData> loadAll() throws SQLException {
        Map<String, RegionData> result = new HashMap<>();
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT name, world, min_x, min_y, min_z, max_x, max_y, max_z, tree_type, cooldown_until FROM regions");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TreeType type = TreeType.parse(rs.getString(9));
                    if (type == null) {
                        log.warning("Skipping region '" + rs.getString(1) + "' with unknown type '" + rs.getString(9) + "'.");
                        continue;
                    }
                    RegionData region = new RegionData(rs.getString(1), rs.getString(2),
                            rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8),
                            type, rs.getLong(10));
                    result.put(region.name().toLowerCase(Locale.ROOT), region);
                }
            }
        }
        return result;
    }

    @Override
    public void save(RegionData r) throws SQLException {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "INSERT OR REPLACE INTO regions (name, world, min_x, min_y, min_z, max_x, max_y, max_z, tree_type, cooldown_until) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, r.name());
                ps.setString(2, r.world());
                ps.setInt(3, r.minX());
                ps.setInt(4, r.minY());
                ps.setInt(5, r.minZ());
                ps.setInt(6, r.maxX());
                ps.setInt(7, r.maxY());
                ps.setInt(8, r.maxZ());
                ps.setString(9, r.type().name());
                ps.setLong(10, r.cooldownUntil());
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void delete(String name) throws SQLException {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM regions WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        }
    }
}
