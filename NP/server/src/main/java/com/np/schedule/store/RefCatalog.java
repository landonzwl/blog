package com.np.schedule.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把已经有档案行的引用改成存对方 id。名称和单号只在迁移时用来找到 id。
 */
public final class RefCatalog {

    public static final String[][] FIELDS = new String[][] {
            {"depts", "parent", "depts"},
            {"depts", "manager", "employees"},
            {"employees", "dept", "depts"},
            {"attendance", "name", "employees"},
            {"attendance", "shift", "shifts"},
            {"leaves", "type", "leaveTypes"},
            {"attendance", "dept", "depts"},
            {"leaves", "name", "employees"},
            {"payroll", "name", "employees"},
            {"payroll", "dept", "depts"},
            {"notices", "dept", "depts"},
            {"assets", "dept", "depts"},
            {"assets", "keeper", "employees"},
            {"warehouses", "dept", "depts"},
            {"warehouses", "keeper", "employees"},
            {"customers", "owner", "employees"},
            {"contracts", "customer", "customers"},
            {"contracts", "owner", "employees"},
            {"salesOrders", "contract", "contracts"},
            {"salesOrders", "customer", "customers"},
            {"materials", "unit", "units"},
            {"materials", "category", "categories"},
            {"salesOrders", "material", "materials"},
            {"salesOrders", "owner", "employees"},
            {"deliveries", "order", "salesOrders"},
            {"deliveries", "customer", "customers"},
            {"deliveries", "material", "materials"},
            {"deliveries", "warehouse", "warehouses"},
            {"suppliers", "owner", "employees"},
            {"suppliers", "category", "supplierCategories"},
            {"purchaseContracts", "supplier", "suppliers"},
            {"purchaseContracts", "owner", "employees"},
            {"purchaseOrders", "contract", "purchaseContracts"},
            {"purchaseOrders", "supplier", "suppliers"},
            {"purchaseOrders", "material", "materials"},
            {"purchaseOrders", "buyer", "employees"},
            {"receipts", "order", "purchaseOrders"},
            {"receipts", "supplier", "suppliers"},
            {"receipts", "material", "materials"},
            {"receipts", "warehouse", "warehouses"},
            {"stock", "material", "materials"},
            {"stock", "warehouse", "warehouses"},
            {"ioBills", "warehouse", "warehouses"},
            {"ioBills", "material", "materials"},
            {"transfers", "material", "materials"},
            {"transfers", "from", "warehouses"},
            {"transfers", "to", "warehouses"},
            {"counts", "warehouse", "warehouses"},
            {"counts", "material", "materials"},
            {"boms", "product", "materials"},
            {"boms", "material", "materials"},
            {"jobs", "product", "materials"},
            {"jobs", "owner", "employees"},
            {"jobs", "workshop", "depts"},
            {"jobs", "warehouse", "warehouses"},
            {"checks", "job", "jobs"},
            {"checks", "material", "materials"},
            {"checks", "owner", "employees"},
            {"receivables", "customer", "customers"},
            {"receivables", "source", "salesOrders"},
            {"payables", "supplier", "suppliers"},
            {"payables", "source", "purchaseOrders"},
            {"collects", "bill", "receivables"},
            {"collects", "contract", "contracts"},
            {"collects", "customer", "customers"},
            {"collects", "method", "payMethods"},
            {"payments", "bill", "payables"},
            {"payments", "contract", "purchaseContracts"},
            {"payments", "supplier", "suppliers"},
            {"payments", "method", "payMethods"},
            {"expenses", "applicant", "employees"},
            {"expenses", "type", "expenseTypes"},
            {"approvals", "applicant", "employees"}
    };

    private RefCatalog() {
    }

    public static boolean multi(String table, String field) {
        if ("notices".equals(table) && "dept".equals(field)) return true;
        if ("contracts".equals(table) && "customer".equals(field)) return true;
        if ("salesOrders".equals(table) && ("customer".equals(field) || "material".equals(field))) return true;
        if ("deliveries".equals(table) && ("order".equals(field) || "customer".equals(field) || "material".equals(field))) return true;
        if ("purchaseContracts".equals(table) && "supplier".equals(field)) return true;
        if ("purchaseOrders".equals(table) && ("supplier".equals(field) || "material".equals(field))) return true;
        if ("receipts".equals(table) && ("order".equals(field) || "supplier".equals(field) || "material".equals(field))) return true;
        if ("checks".equals(table) && ("job".equals(field) || "material".equals(field) || "owner".equals(field))) return true;
        if ("jobs".equals(table) && ("product".equals(field) || "owner".equals(field) || "workshop".equals(field) || "warehouse".equals(field))) return true;
        if ("collects".equals(table) && ("bill".equals(field) || "customer".equals(field) || "contract".equals(field))) return true;
        if ("payments".equals(table) && ("bill".equals(field) || "supplier".equals(field) || "contract".equals(field))) return true;
        if ("ioBills".equals(table) && ("material".equals(field) || "warehouse".equals(field))) return true;
        if ("transfers".equals(table) && ("material".equals(field) || "from".equals(field) || "to".equals(field))) return true;
        if ("counts".equals(table) && ("material".equals(field) || "warehouse".equals(field))) return true;
        if ("attendance".equals(table) && ("name".equals(field) || "dept".equals(field) || "shift".equals(field))) return true;
        if ("leaves".equals(table) && "type".equals(field)) return true;
        if ("leaves".equals(table) && "name".equals(field)) return true;
        if ("payroll".equals(table) && ("name".equals(field) || "dept".equals(field))) return true;
        if ("expenses".equals(table) && "applicant".equals(field)) return true;
        if ("expenses".equals(table) && "type".equals(field)) return true;
        if ("suppliers".equals(table) && "category".equals(field)) return true;
        if ("approvals".equals(table) && "applicant".equals(field)) return true;
        if ("assets".equals(table) && ("dept".equals(field) || "keeper".equals(field))) return true;
        return "employees".equals(table) && "dept".equals(field);
    }

    public static void migrate(JdbcTemplate jdbc, ObjectMapper mapper) {
        if (!has(jdbc, "NP_CELL") || !has(jdbc, "NP_RECORD")) return;
        Map<String, Map<String, String>> byName = new LinkedHashMap<String, Map<String, String>>();
        Map<String, Map<String, String>> byCode = new LinkedHashMap<String, Map<String, String>>();
        Map<String, Map<String, Boolean>> ids = new LinkedHashMap<String, Map<String, Boolean>>();
        List<String[]> records = new ArrayList<String[]>();
        jdbc.query("SELECT id, entity_code FROM np_record", rs -> {
            records.add(new String[]{rs.getString(1), rs.getString(2)});
        });
        for (String[] record : records) {
            idBucket(ids, record[1]).put(record[0], Boolean.TRUE);
        }
        List<String[]> named = new ArrayList<String[]>();
        jdbc.query("SELECT r.entity_code, c.record_id, c.field_key, c.value_text FROM np_cell c "
                + "JOIN np_record r ON r.id = c.record_id WHERE c.field_key IN ('name', 'code')", rs -> {
            named.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
        });
        for (String[] row : named) {
            if (row[3] == null || row[3].trim().isEmpty()) continue;
            if ("name".equals(row[2])) bucket(byName, row[0]).put(row[3].trim(), row[1]);
            if ("code".equals(row[2])) bucket(byCode, row[0]).put(row[3].trim(), row[1]);
        }
        for (String[] field : FIELDS) {
            List<String[]> cells = new ArrayList<String[]>();
            jdbc.query("SELECT c.record_id, c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                    + "WHERE r.entity_code = ? AND c.field_key = ?", rs -> {
                cells.add(new String[]{rs.getString(1), rs.getString(2)});
            }, field[0], field[1]);
            for (String[] cell : cells) {
                String next = rewritten(mapper, cell[1], field[0], field[1], field[2], byName, byCode, ids);
                if (next == null || next.equals(cell[1] == null ? "" : cell[1])) continue;
                jdbc.update("UPDATE np_cell SET value_text = ?, value_num = NULL WHERE record_id = ? AND field_key = ?",
                        next, cell[0], field[1]);
            }
        }
    }

    private static String rewritten(ObjectMapper mapper, String stored, String table, String field, String target,
                                    Map<String, Map<String, String>> byName, Map<String, Map<String, String>> byCode,
                                    Map<String, Map<String, Boolean>> ids) {
        String text = stored == null ? "" : stored;
        if (text.startsWith("j:")) {
            try {
                Object parsed = mapper.readValue(text.substring(2), Object.class);
                if (parsed instanceof List) {
                    List<String> next = new ArrayList<String>();
                    for (Object item : (List<?>) parsed) {
                        String id = resolve(item == null ? "" : String.valueOf(item), target, byName, byCode, ids);
                        if (!id.isEmpty() && !next.contains(id)) next.add(id);
                    }
                    return "j:" + mapper.writeValueAsString(next);
                }
            } catch (Exception ex) {
                return text;
            }
        }
        if (multi(table, field) && text.contains(",")) {
            List<String> next = new ArrayList<String>();
            for (String part : text.split("[,，]")) {
                String id = resolve(part, target, byName, byCode, ids);
                if (!id.isEmpty() && !next.contains(id)) next.add(id);
            }
            try {
                return "j:" + mapper.writeValueAsString(next);
            } catch (Exception ex) {
                return text;
            }
        }
        String id = resolve(text, target, byName, byCode, ids);
        return id.isEmpty() ? text : id;
    }

    private static String resolve(String token, String target, Map<String, Map<String, String>> byName,
                                  Map<String, Map<String, String>> byCode, Map<String, Map<String, Boolean>> ids) {
        String value = token == null ? "" : token.trim();
        if (value.isEmpty() || "—".equals(value) || "-".equals(value) || "无".equals(value) || "无上级".equals(value)) return "";
        Map<String, Boolean> known = ids.get(target);
        if (known != null && known.containsKey(value)) return value;
        Map<String, String> names = byName.get(target);
        if (names != null && names.containsKey(value)) return names.get(value);
        Map<String, String> codes = byCode.get(target);
        if (codes != null && codes.containsKey(value)) return codes.get(value);
        return "";
    }

    private static Map<String, String> bucket(Map<String, Map<String, String>> book, String key) {
        Map<String, String> map = book.get(key);
        if (map == null) {
            map = new LinkedHashMap<String, String>();
            book.put(key, map);
        }
        return map;
    }

    private static Map<String, Boolean> idBucket(Map<String, Map<String, Boolean>> book, String key) {
        Map<String, Boolean> map = book.get(key);
        if (map == null) {
            map = new LinkedHashMap<String, Boolean>();
            book.put(key, map);
        }
        return map;
    }

    private static boolean has(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ? AND UPPER(TABLE_SCHEMA) IN (UPPER(DATABASE()), 'PUBLIC')",
                Integer.class, table);
        return count != null && count > 0;
    }
}
