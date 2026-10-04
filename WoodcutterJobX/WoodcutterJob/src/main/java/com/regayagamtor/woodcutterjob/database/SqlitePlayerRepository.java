package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.PlayerJobData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** SQLite implementation. Uses PreparedStatement + try-with-resources everywhere. */
public final class SqlitePlayerRepository implements PlayerRepository {

    private final SQLiteManager db;

    public SqlitePlayerRepository(SQLiteManager db) {
        this.db = db;
    }

    @Override
    public PlayerJobData load(UUID uuid) throws SQLException {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT level, xp, trees_cut, successful_hits, total_rewards, total_earnings FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    PlayerJobData data = new PlayerJobData(uuid);
                    data.restore(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getLong(5), rs.getDouble(6));
                    return data;
                }
            }
        }
    }

    @Override
    public void save(PlayerJobData data) throws SQLException {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "INSERT OR REPLACE INTO players "
                            + "(uuid, level, xp, trees_cut, successful_hits, total_rewards, total_earnings) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, data.uuid().toString());
                ps.setInt(2, data.level());
                ps.setLong(3, data.xp());
                ps.setInt(4, data.treesCut());
                ps.setInt(5, data.successfulHits());
                ps.setLong(6, data.totalRewards());
                ps.setDouble(7, data.totalEarnings());
                ps.executeUpdate();
            }
        }
    }
}
