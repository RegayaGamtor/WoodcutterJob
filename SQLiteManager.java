package com.regayagamtor.woodcutterjob.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/** Owns the single SQLite connection (opened lazily, re-opened if closed) and creates the tables. */
public final class SQLiteManager {

    private final File file;
    private final Logger log;
    private Connection connection;

    public SQLiteManager(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** @return the shared connection. Callers must synchronize on this manager while using it. */
    public synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            open();
        }
        return connection;
    }

    private void open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver (org.sqlite.JDBC) is not available on this server.", ex);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create directory " + parent);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("CREATE TABLE IF NOT EXISTS players ("
                    + "uuid TEXT PRIMARY KEY, "
                    + "level INTEGER NOT NULL DEFAULT 1, "
                    + "xp INTEGER NOT NULL DEFAULT 0, "
                    + "trees_cut INTEGER NOT NULL DEFAULT 0, "
                    + "successful_hits INTEGER NOT NULL DEFAULT 0, "
                    + "total_rewards INTEGER NOT NULL DEFAULT 0, "
                    + "total_earnings REAL NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS regions ("
                    + "name TEXT PRIMARY KEY, "
                    + "world TEXT NOT NULL, "
                    + "min_x INTEGER NOT NULL, "
                    + "min_y INTEGER NOT NULL, "
                    + "min_z INTEGER NOT NULL, "
                    + "max_x INTEGER NOT NULL, "
                    + "max_y INTEGER NOT NULL, "
                    + "max_z INTEGER NOT NULL, "
                    + "tree_type TEXT NOT NULL, "
                    + "cooldown_until INTEGER NOT NULL DEFAULT 0)");
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                log.warning("Error while closing the SQLite connection: " + ex.getMessage());
            }
            connection = null;
        }
    }
}
