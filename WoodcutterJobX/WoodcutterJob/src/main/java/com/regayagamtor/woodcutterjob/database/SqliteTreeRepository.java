package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.TreeData;
import com.regayagamtor.woodcutterjob.model.TreeLocation;
import com.regayagamtor.woodcutterjob.model.TreeType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class SqliteTreeRepository implements TreeRepository {

    private final SQLiteManager db;
    private final Logger log;

    public SqliteTreeRepository(SQLiteManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    @Override
    public Map<TreeLocation, TreeData> loadAll() throws SQLException {
        Map<TreeLocation, TreeData> result = new HashMap<>();
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT world, x, y, z, tree_type, cooldown_until FROM trees");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TreeType type = TreeType.parse(rs.getString(5));
                    if (type == null) {
                        log.warning("Skipping tree with unknown type '" + rs.getString(5) + "' in the database.");
                        continue;
                    }
                    TreeLocation loc = new TreeLocation(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                    result.put(loc, new TreeData(loc, type, rs.getLong(6)));
                }
            }
        }
        return result;
    }

    @Override
    public void save(TreeData tree) throws SQLException {
        TreeLocation loc = tree.location();
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "INSERT OR REPLACE INTO trees (world, x, y, z, tree_type, cooldown_until) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, loc.world());
                ps.setInt(2, loc.x());
                ps.setInt(3, loc.y());
                ps.setInt(4, loc.z());
                ps.setString(5, tree.type().name());
                ps.setLong(6, tree.cooldownUntil());
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void delete(TreeLocation loc) throws SQLException {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "DELETE FROM trees WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, loc.world());
                ps.setInt(2, loc.x());
                ps.setInt(3, loc.y());
                ps.setInt(4, loc.z());
                ps.executeUpdate();
            }
        }
    }
}
