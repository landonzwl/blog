package com.np.schedule.store;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位主数据。员工档案只保存岗位 id，名称按 id 读取。
 */
public final class PostCatalog {

    private static final Object LOCK = new Object();

    private PostCatalog() {
    }

    public static void ensure(JdbcTemplate jdbc) {
        synchronized (LOCK) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS np_post ("
                    + "id VARCHAR(32) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, UNIQUE (name))");
        }
    }

    public static String idOf(JdbcTemplate jdbc, String token) {
        synchronized (LOCK) {
            ensure(jdbc);
            String value = token == null ? "" : token.trim();
            if (value.isEmpty() || "[]".equals(value)) return "";
            if (value.length() > 32) value = value.substring(0, 32);
            String byId = find(jdbc, "id", value);
            if (byId != null) return byId;
            String byName = find(jdbc, "name", value);
            if (byName != null) return byName;
            String id = nextId(jdbc);
            jdbc.update("INSERT INTO np_post (id, name) VALUES (?, ?)", id, value);
            return id;
        }
    }

    public static List<Map<String, String>> rows(JdbcTemplate jdbc) {
        ensure(jdbc);
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        jdbc.query("SELECT id, name FROM np_post ORDER BY name, id", rs -> {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("id", rs.getString("id"));
            row.put("name", rs.getString("name"));
            list.add(row);
        });
        return list;
    }

    public static void migrateCells(JdbcTemplate jdbc) {
        ensure(jdbc);
        if (!has(jdbc, "NP_CELL") || !has(jdbc, "NP_RECORD")) return;
        List<String[]> rows = new ArrayList<String[]>();
        jdbc.query("SELECT c.record_id, c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE c.field_key = 'post' AND r.entity_code = 'employees'", rs -> {
            rows.add(new String[]{rs.getString(1), rs.getString(2)});
        });
        for (String[] row : rows) {
            String id = idOf(jdbc, row[1]);
            if (id.equals(row[1] == null ? "" : row[1])) continue;
            jdbc.update("UPDATE np_cell SET value_text = ?, value_num = NULL WHERE record_id = ? AND field_key = 'post'", id, row[0]);
        }
    }

    public static void delete(JdbcTemplate jdbc, String id) {
        ensure(jdbc);
        jdbc.update("DELETE FROM np_post WHERE id = ?", id);
    }

    private static String nextId(JdbcTemplate jdbc) {
        List<String> ids = jdbc.query("SELECT id FROM np_post", (rs, row) -> rs.getString(1));
        int max = 0;
        for (String id : ids) {
            if (id != null && id.matches("p\\d+")) {
                int value = Integer.parseInt(id.substring(1));
                if (value > max) max = value;
            }
        }
        return "p" + (max + 1);
    }

    private static String find(JdbcTemplate jdbc, String column, String value) {
        List<String> rows = jdbc.query("SELECT id FROM np_post WHERE " + column + " = ?", (rs, row) -> rs.getString(1), value);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static boolean has(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ? AND UPPER(TABLE_SCHEMA) IN (UPPER(DATABASE()), 'PUBLIC')",
                Integer.class, table);
        return count != null && count > 0;
    }
}
