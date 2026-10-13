package com.np.schedule.store;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单位和分类是物料上的档案。下拉选项来自这两张表，已有文本会补成档案行。
 */
public final class VocabCatalog {

    private VocabCatalog() {
    }

    public static void ensure(JdbcTemplate jdbc) {
        ensure(jdbc, true);
    }

    public static void ensure(JdbcTemplate jdbc, boolean defaults) {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'NP_RECORD' AND UPPER(TABLE_SCHEMA) IN (UPPER(DATABASE()), 'PUBLIC')",
                Integer.class);
        if (tables == null || tables == 0) return;
        ensureKind(jdbc, "units", "vu", "unit");
        ensureKind(jdbc, "categories", "vc", "category");
        ensureMethods(jdbc, defaults);
        ensureNamed(jdbc, "shifts", "sh", "attendance", "shift", defaults ? new String[] {"早班", "中班", "晚班"} : new String[0]);
        ensureNamed(jdbc, "leaveTypes", "lt", "leaves", "type", defaults ? new String[] {"事假", "病假", "年假"} : new String[0]);
        ensureNamed(jdbc, "supplierCategories", "sg", "suppliers", "category", defaults ? new String[] {"五金", "日化"} : new String[0]);
        ensureNamed(jdbc, "expenseTypes", "xt", "expenses", "type", defaults ? new String[] {"交通", "办公"} : new String[0]);
    }

    private static void ensureKind(JdbcTemplate jdbc, String entity, String prefix, String field) {
        Map<String, Boolean> ids = new LinkedHashMap<String, Boolean>();
        Map<String, String> byName = new LinkedHashMap<String, String>();
        jdbc.query("SELECT id FROM np_record WHERE entity_code = ?", rs -> {
            ids.put(rs.getString(1), Boolean.TRUE);
        }, entity);
        jdbc.query("SELECT c.record_id, c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE r.entity_code = ? AND c.field_key = 'name'", rs -> {
            String name = rs.getString(2);
            if (name != null && !name.trim().isEmpty()) byName.put(name.trim(), rs.getString(1));
        }, entity);
        List<String> tokens = new ArrayList<String>();
        jdbc.query("SELECT c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE r.entity_code = 'materials' AND c.field_key = ?", rs -> {
            collect(tokens, rs.getString(1));
        }, field);
        int seq = ids.size();
        for (String token : tokens) {
            if (ids.containsKey(token) || byName.containsKey(token)) continue;
            seq += 1;
            String id = prefix + seq;
            while (ids.containsKey(id)) {
                seq += 1;
                id = prefix + seq;
            }
            jdbc.update("INSERT INTO np_record (id, entity_code, sort_no, origin) VALUES (?, ?, ?, 'business')", id, entity, seq);
            jdbc.update("INSERT INTO np_cell (record_id, field_key, value_text, value_num) VALUES (?, 'name', ?, NULL)", id, token);
            jdbc.update("INSERT INTO np_cell (record_id, field_key, value_text, value_num) VALUES (?, 'status', '启用', NULL)", id);
            ids.put(id, Boolean.TRUE);
            byName.put(token, id);
        }
    }

    private static void ensureMethods(JdbcTemplate jdbc, boolean defaults) {
        Map<String, Boolean> ids = new LinkedHashMap<String, Boolean>();
        Map<String, String> byName = new LinkedHashMap<String, String>();
        jdbc.query("SELECT id FROM np_record WHERE entity_code = 'payMethods'", rs -> {
            ids.put(rs.getString(1), Boolean.TRUE);
        });
        jdbc.query("SELECT c.record_id, c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE r.entity_code = 'payMethods' AND c.field_key = 'name'", rs -> {
            String name = rs.getString(2);
            if (name != null && !name.trim().isEmpty()) byName.put(name.trim(), rs.getString(1));
        });
        List<String> tokens = new ArrayList<String>();
        jdbc.query("SELECT c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE r.entity_code IN ('collects', 'payments') AND c.field_key = 'method'", rs -> {
            collect(tokens, rs.getString(1));
        });
        if (defaults && tokens.isEmpty() && byName.isEmpty()) {
            tokens.add("银行转账");
            tokens.add("现金");
        }
        int seq = ids.size();
        for (String token : tokens) {
            if (ids.containsKey(token) || byName.containsKey(token)) continue;
            seq += 1;
            String id = "pm" + seq;
            while (ids.containsKey(id)) {
                seq += 1;
                id = "pm" + seq;
            }
            jdbc.update("INSERT INTO np_record (id, entity_code, sort_no, origin) VALUES (?, 'payMethods', ?, 'business')", id, seq);
            jdbc.update("INSERT INTO np_cell (record_id, field_key, value_text, value_num) VALUES (?, 'name', ?, NULL)", id, token);
            jdbc.update("INSERT INTO np_cell (record_id, field_key, value_text, value_num) VALUES (?, 'status', '启用', NULL)", id);
            ids.put(id, Boolean.TRUE);
            byName.put(token, id);
        }
    }

    private static void ensureNamed(JdbcTemplate jdbc, String entity, String prefix, String sourceEntity, String sourceField, String[] fallback) {
        Map<String, Boolean> ids = new LinkedHashMap<String, Boolean>();
        Map<String, Boolean> taken = new LinkedHashMap<String, Boolean>();
        Map<String, String> byName = new LinkedHashMap<String, String>();
        jdbc.query("SELECT id FROM np_record", rs -> {
            taken.put(rs.getString(1), Boolean.TRUE);
        });
        jdbc.query("SELECT id FROM np_record WHERE entity_code = ?", rs -> {
            ids.put(rs.getString(1), Boolean.TRUE);
        }, entity);
        jdbc.query("SELECT c.record_id, c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE r.entity_code = ? AND c.field_key = 'name'", rs -> {
            String name = rs.getString(2);
            if (name != null && !name.trim().isEmpty()) byName.put(name.trim(), rs.getString(1));
        }, entity);
        List<String> tokens = new ArrayList<String>();
        jdbc.query("SELECT c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE r.entity_code = ? AND c.field_key = ?", rs -> {
            collect(tokens, rs.getString(1));
        }, sourceEntity, sourceField);
        if (tokens.isEmpty() && byName.isEmpty()) {
            for (String name : fallback) tokens.add(name);
        }
        int seq = ids.size();
        for (String token : tokens) {
            if (ids.containsKey(token) || byName.containsKey(token)) continue;
            seq += 1;
            String id = prefix + seq;
            while (taken.containsKey(id)) {
                seq += 1;
                id = prefix + seq;
            }
            jdbc.update("INSERT INTO np_record (id, entity_code, sort_no, origin) VALUES (?, ?, ?, 'business')", id, entity, seq);
            jdbc.update("INSERT INTO np_cell (record_id, field_key, value_text, value_num) VALUES (?, 'name', ?, NULL)", id, token);
            jdbc.update("INSERT INTO np_cell (record_id, field_key, value_text, value_num) VALUES (?, 'status', '启用', NULL)", id);
            ids.put(id, Boolean.TRUE);
            taken.put(id, Boolean.TRUE);
            byName.put(token, id);
        }
    }

    private static void collect(List<String> tokens, String stored) {
        String text = stored == null ? "" : stored.trim();
        if (text.isEmpty() || text.startsWith("j:")) {
            if (text.startsWith("j:") && text.contains("\"")) {
                String body = text.substring(2);
                for (String part : body.split("\"")) {
                    String token = part.trim();
                    if (token.isEmpty() || "[".equals(token) || "]".equals(token) || ",".equals(token) || token.startsWith("[") || token.startsWith(",")) continue;
                    if (!tokens.contains(token)) tokens.add(token);
                }
            }
            return;
        }
        if (!tokens.contains(text)) tokens.add(text);
    }
}
