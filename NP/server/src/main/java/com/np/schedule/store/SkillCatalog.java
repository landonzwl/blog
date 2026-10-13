package com.np.schedule.store;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能主数据。人员、任务和员工档案只保存技能 id，名称从本表按 id 读取。
 * 一名人员的多个技能 id 用英文逗号拼在同一列里。
 */
public final class SkillCatalog {

    private static final Object LOCK = new Object();

    private SkillCatalog() {
    }

    public static void ensure(JdbcTemplate jdbc) {
        synchronized (LOCK) {
            ensureUnlocked(jdbc);
        }
    }

    /**
     * 空库第一次启动时写入起始技能。表里已有数据就不再写入，删除后的技能也不会被补回。
     */
    public static void seed(JdbcTemplate jdbc) {
        synchronized (LOCK) {
            ensureUnlocked(jdbc);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM np_skill", Integer.class);
            if (count != null && count > 0) return;
            jdbc.update("INSERT INTO np_skill (id, name) VALUES (?, ?)", "sk-general", "通用");
            jdbc.update("INSERT INTO np_skill (id, name) VALUES (?, ?)", "sk-all", "全班");
            jdbc.update("INSERT INTO np_skill (id, name) VALUES (?, ?)", "sk-day", "白班");
            jdbc.update("INSERT INTO np_skill (id, name) VALUES (?, ?)", "sk-night", "夜班");
        }
    }

    public static String idOf(JdbcTemplate jdbc, String token) {
        synchronized (LOCK) {
            ensureUnlocked(jdbc);
            String value = token == null ? "" : token.trim();
            if (value.isEmpty() || "[]".equals(value)) return "";
            if (value.length() > 32) value = value.substring(0, 32);
            String byId = find(jdbc, "id", value);
            if (byId != null) return byId;
            String byName = find(jdbc, "name", value);
            if (byName != null) return byName;
            String id = nextId(jdbc);
            jdbc.update("INSERT INTO np_skill (id, name) VALUES (?, ?)", id, value);
            return id;
        }
    }

    public static List<String> idsOf(JdbcTemplate jdbc, Object value) {
        List<String> ids = new ArrayList<String>();
        for (String token : tokens(value)) {
            String id = idOf(jdbc, token);
            if (id.isEmpty() || ids.contains(id)) continue;
            ids.add(id);
        }
        return ids;
    }

    public static String join(List<String> ids) {
        StringBuilder text = new StringBuilder();
        if (ids == null) return "";
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) continue;
            if (text.length() > 0) text.append(',');
            text.append(id.trim());
        }
        return text.toString();
    }

    public static Map<String, String> book(JdbcTemplate jdbc) {
        ensure(jdbc);
        Map<String, String> map = new LinkedHashMap<String, String>();
        jdbc.query("SELECT id, name FROM np_skill ORDER BY name, id", rs -> {
            map.put(rs.getString("id"), rs.getString("name"));
        });
        return map;
    }

    public static List<Map<String, String>> rows(JdbcTemplate jdbc) {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (Map.Entry<String, String> entry : book(jdbc).entrySet()) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("id", entry.getKey());
            row.put("name", entry.getValue());
            list.add(row);
        }
        return list;
    }

    public static void migrateSchedule(JdbcTemplate jdbc) {
        ensure(jdbc);
        if (!has(jdbc, "NP_STAFF")) return;
        List<String[]> staff = new ArrayList<String[]>();
        jdbc.query("SELECT id, skill FROM np_staff", rs -> {
            staff.add(new String[]{rs.getString("id"), rs.getString("skill")});
        });
        Map<String, List<String>> extra = new LinkedHashMap<String, List<String>>();
        if (has(jdbc, "NP_STAFF_SKILL")) {
            jdbc.query("SELECT staff_id, skill FROM np_staff_skill", rs -> {
                List<String> list = extra.get(rs.getString("staff_id"));
                if (list == null) {
                    list = new ArrayList<String>();
                    extra.put(rs.getString("staff_id"), list);
                }
                list.add(rs.getString("skill"));
            });
        }
        for (String[] row : staff) {
            List<String> tokens = new ArrayList<String>(tokens(row[1]));
            List<String> more = extra.get(row[0]);
            if (more != null) tokens.addAll(more);
            String joined = join(idsOf(jdbc, tokens));
            if (!joined.equals(row[1] == null ? "" : row[1])) {
                jdbc.update("UPDATE np_staff SET skill = ? WHERE id = ?", joined, row[0]);
            }
        }
        if (has(jdbc, "NP_STAFF_SKILL")) jdbc.update("DELETE FROM np_staff_skill");
        if (!has(jdbc, "NP_TASK")) return;
        List<String[]> tasks = new ArrayList<String[]>();
        jdbc.query("SELECT id, skill FROM np_task", rs -> {
            tasks.add(new String[]{rs.getString("id"), rs.getString("skill")});
        });
        for (String[] row : tasks) {
            String next = idOf(jdbc, row[1]);
            if (!next.equals(row[1])) jdbc.update("UPDATE np_task SET skill = ? WHERE id = ?", next, row[0]);
        }
    }

    public static void migrateCells(JdbcTemplate jdbc) {
        ensure(jdbc);
        if (!has(jdbc, "NP_CELL") || !has(jdbc, "NP_RECORD")) return;
        List<String[]> rows = new ArrayList<String[]>();
        jdbc.query("SELECT c.record_id, c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE c.field_key = 'skill' AND r.entity_code = 'employees'", rs -> {
            rows.add(new String[]{rs.getString(1), rs.getString(2)});
        });
        for (String[] row : rows) {
            String joined = join(idsOf(jdbc, row[1]));
            if (joined.equals(row[1] == null ? "" : row[1])) continue;
            jdbc.update("UPDATE np_cell SET value_text = ?, value_num = NULL WHERE record_id = ? AND field_key = 'skill'", joined, row[0]);
        }
    }

    public static List<String> tokens(Object value) {
        List<String> list = new ArrayList<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) addTokens(list, item == null ? "" : String.valueOf(item));
        } else {
            addTokens(list, value == null ? "" : String.valueOf(value));
        }
        return list;
    }

    private static void addTokens(List<String> list, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("j:")) text = text.substring(2).trim();
        if (text.startsWith("[")) {
            text = text.substring(1);
            if (text.endsWith("]")) text = text.substring(0, text.length() - 1);
        }
        for (String part : text.split("[,，]")) {
            String item = part.trim();
            if (item.startsWith("\"")) item = item.substring(1);
            if (item.endsWith("\"")) item = item.substring(0, item.length() - 1);
            item = item.trim();
            if (item.isEmpty() || "[]".equals(item) || list.contains(item) || list.size() >= 16) continue;
            list.add(item);
        }
    }

    private static void ensureUnlocked(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS np_skill ("
                + "id VARCHAR(32) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, UNIQUE (name))");
    }

    private static String nextId(JdbcTemplate jdbc) {
        List<String> ids = jdbc.query("SELECT id FROM np_skill", (rs, row) -> rs.getString(1));
        int max = 0;
        for (String id : ids) {
            if (id != null && id.matches("sk\\d+")) {
                int value = Integer.parseInt(id.substring(2));
                if (value > max) max = value;
            }
        }
        return "sk" + (max + 1);
    }

    private static String find(JdbcTemplate jdbc, String column, String value) {
        List<String> rows = jdbc.query("SELECT id FROM np_skill WHERE " + column + " = ?", (rs, row) -> rs.getString(1), value);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static boolean has(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ? AND UPPER(TABLE_SCHEMA) IN (UPPER(DATABASE()), 'PUBLIC')",
                Integer.class, table);
        return count != null && count > 0;
    }
}
