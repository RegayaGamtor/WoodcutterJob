package com.regayagamtor.woodcutterjob.database;

import com.regayagamtor.woodcutterjob.model.TreeData;
import com.regayagamtor.woodcutterjob.model.TreeLocation;

import java.util.Map;

/** Storage abstraction for registered trees (YAML or SQLite). */
public interface TreeRepository {

    Map<TreeLocation, TreeData> loadAll() throws Exception;

    /** Insert or update. */
    void save(TreeData tree) throws Exception;

    void delete(TreeLocation location) throws Exception;
}
