package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.PlayerJobData;

import java.util.UUID;

/** Storage abstraction for player job data (YAML or SQLite). Methods may block; never call on the main thread per click. */
public interface PlayerRepository {

    /** @return stored data or null if the player has none yet. */
    PlayerJobData load(UUID uuid) throws Exception;

    void save(PlayerJobData data) throws Exception;
}
