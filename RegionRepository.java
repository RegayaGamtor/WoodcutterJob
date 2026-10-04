package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.RegionData;

import java.util.Map;

/** Storage abstraction for woodcutting regions (YAML or SQLite). Keys are lower-case region names. */
public interface RegionRepository {

    Map<String, RegionData> loadAll() throws Exception;

    /** Insert or update. */
    void save(RegionData region) throws Exception;

    void delete(String name) throws Exception;
}
