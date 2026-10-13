package com.np.schedule.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.np.schedule.common.DataConflict;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ErpDatabase {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };
    private static final TypeReference<Map<String, List<Map<String, Object>>>> TABLES_TYPE =
            new TypeReference<Map<String, List<Map<String, Object>>>>() {
            };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final Set<String> seedIds = new HashSet<String>();
    private final Map<String, Map<String, String>> cellBase = new HashMap<String, Map<String, String>>();
    private final Map<String, Integer> sortBase = new HashMap<String, Integer>();
    private final Map<String, String> originBase = new HashMap<String, String>();
    private final Map<String, String> typeBase = new HashMap<String, String>();
    private final Map<String, Set<String>> numberFields = new HashMap<String, Set<String>>();
    private final Map<String, Set<String>> textFields = new HashMap<String, Set<String>>();
    private String orgBase = "";
    private boolean seed = true;

    @org.springframework.beans.factory.annotation.Value("${np.seed:true}")
    public void setSeed(boolean seed) {
        this.seed = seed;
    }

    public boolean seedEnabled() {
        return seed;
    }

    public ErpDatabase(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.mapper = mapper;
    }

    public static ErpDatabase memory() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:erp" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return new ErpDatabase(new JdbcTemplate(dataSource), new ObjectMapper());
    }

    public void prepare(ErpStore store) {
        ensureSchema();
        ensureColumn("np_erp_doc", "origin", "ALTER TABLE np_erp_doc ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'business'");
        loadSeedIds();
        EntityCatalog.ensure(jdbc);
        rememberNumbers();
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM np_erp_state", Integer.class);
        if (rows == null || rows == 0) {
            if (seed) {
                loadSeed(store);
            } else {
                store.orgSites = "";
            }
            save(store);
            if (seed) {
                SkillCatalog.seed(jdbc);
            }
            SkillCatalog.migrateCells(jdbc);
            PostCatalog.migrateCells(jdbc);
            VocabCatalog.ensure(jdbc, seed);
            EntityCatalog.renameBomFields(jdbc);
            RefCatalog.migrate(jdbc, mapper);
            loadInto(store);
            return;
        }
        for (String id : seedIds) {
            jdbc.update("UPDATE np_erp_doc SET origin = 'seed' WHERE id = ? AND origin <> 'seed'", id);
        }
        importLegacyDocs();
        for (String id : seedIds) {
            jdbc.update("UPDATE np_record SET origin = 'seed' WHERE id = ? AND origin <> 'seed'", id);
        }
        SkillCatalog.migrateCells(jdbc);
        PostCatalog.migrateCells(jdbc);
        VocabCatalog.ensure(jdbc, seed);
        EntityCatalog.renameBomFields(jdbc);
        RefCatalog.migrate(jdbc, mapper);
        loadInto(store);
    }

    public String skillIds(Object value) {
        return SkillCatalog.join(SkillCatalog.idsOf(jdbc, value));
    }

    public List<Map<String, String>> skills() {
        return SkillCatalog.rows(jdbc);
    }

    public String postId(Object value) {
        return PostCatalog.idOf(jdbc, value == null ? "" : String.valueOf(value));
    }

    public List<Map<String, String>> posts() {
        return PostCatalog.rows(jdbc);
    }

    public void deletePost(String id) {
        PostCatalog.delete(jdbc, id);
    }

    public void loadInto(ErpStore store) {
        store.tables.clear();
        Set<String> types = new LinkedHashSet<String>();
        for (String type : ErpStore.TABLES) types.add(type);
        jdbc.query("SELECT code FROM np_meta_entity", rs -> {
            types.add(rs.getString("code"));
        });
        for (String type : types) store.tables.put(type, new ArrayList<Map<String, Object>>());
        cellBase.clear();
        sortBase.clear();
        originBase.clear();
        typeBase.clear();
        Map<String, Map<String, Object>> rows = new LinkedHashMap<String, Map<String, Object>>();
        jdbc.query("SELECT id, entity_code, sort_no, origin FROM np_record ORDER BY sort_no, id", rs -> {
            String id = rs.getString("id");
            String type = rs.getString("entity_code");
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", id);
            rows.put(id, row);
            List<Map<String, Object>> list = store.tables.get(type);
            if (list == null) {
                list = new ArrayList<Map<String, Object>>();
                store.tables.put(type, list);
            }
            list.add(row);
            sortBase.put(id, rs.getInt("sort_no"));
            originBase.put(id, rs.getString("origin"));
            typeBase.put(id, type);
            cellBase.put(id, new LinkedHashMap<String, String>());
        });
        jdbc.query("SELECT record_id, field_key, value_text, value_num FROM np_cell ORDER BY record_id, field_key", rs -> {
            Map<String, Object> row = rows.get(rs.getString("record_id"));
            if (row == null) return;
            Object value = cellValue(rs.getString("value_text"), rs.getBigDecimal("value_num"));
            row.put(rs.getString("field_key"), value);
            cellBase.get(rs.getString("record_id")).put(rs.getString("field_key"), sig(value));
        });
        jdbc.query("SELECT next_seq, notice, version FROM np_erp_state WHERE id = 1", rs -> {
            store.seq = rs.getInt("next_seq");
            store.notice = rs.getString("notice");
            store.version = rs.getInt("version");
        });
        jdbc.query("SELECT name, sites FROM np_org WHERE id = 1", rs -> {
            store.orgName = rs.getString("name");
            store.orgSites = rs.getString("sites");
        });
        orgBase = store.orgName + "\u001f" + store.orgSites;
        rememberNumbers();
    }

    public String storedPayload(String id) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        jdbc.query("SELECT field_key, value_text, value_num FROM np_cell WHERE record_id = ? ORDER BY field_key", rs -> {
            row.put(rs.getString("field_key"), cellValue(rs.getString("value_text"), rs.getBigDecimal("value_num")));
        }, id);
        try {
            return mapper.writeValueAsString(row);
        } catch (Exception ex) {
            throw new IllegalStateException("业务单据无法读取", ex);
        }
    }

    public String textCell(String id, String key) {
        try {
            return jdbc.queryForObject("SELECT value_text FROM np_cell WHERE record_id = ? AND field_key = ?", String.class, id, key);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public String originOf(String id) {
        String origin = originBase.get(id);
        return origin == null || origin.isEmpty() ? "business" : origin;
    }

    public void save(ErpStore store) {
        final Map<String, Map<String, String>> nextCells = new HashMap<String, Map<String, String>>();
        final Map<String, Integer> nextSort = new HashMap<String, Integer>();
        final Map<String, String> nextOrigin = new HashMap<String, String>();
        final Map<String, String> nextType = new HashMap<String, String>();
        transactions.execute(status -> {
            Integer current = lockVersion();
            if (current != null && current.intValue() != store.version) {
                throw new DataConflict();
            }
            int next = current == null ? 1 : current + 1;
            store.version = next;
            writeDocs(store, nextCells, nextSort, nextOrigin, nextType);
            int updated = jdbc.update("UPDATE np_erp_state SET next_seq = ?, notice = ?, version = ? WHERE id = 1",
                    store.seq, cut(store.notice, 255), store.version);
            if (updated == 0) {
                jdbc.update("INSERT INTO np_erp_state (id, next_seq, notice, version) VALUES (1, ?, ?, ?)",
                        store.seq, cut(store.notice, 255), store.version);
            }
            String org = store.orgName + "\u001f" + store.orgSites;
            if (!org.equals(orgBase)) {
                int orgRows = jdbc.update("UPDATE np_org SET name = ?, sites = ? WHERE id = 1", cut(store.orgName, 64), cut(store.orgSites, 255));
                if (orgRows == 0) {
                    jdbc.update("INSERT INTO np_org (id, name, sites) VALUES (1, ?, ?)", cut(store.orgName, 64), cut(store.orgSites, 255));
                }
            }
            return null;
        });
        cellBase.clear();
        cellBase.putAll(nextCells);
        sortBase.clear();
        sortBase.putAll(nextSort);
        originBase.clear();
        originBase.putAll(nextOrigin);
        typeBase.clear();
        typeBase.putAll(nextType);
        orgBase = store.orgName + "\u001f" + store.orgSites;
    }

    private void writeDocs(ErpStore store, Map<String, Map<String, String>> nextCells, Map<String, Integer> nextSort,
                           Map<String, String> nextOrigin, Map<String, String> nextType) {
        Set<String> keep = new HashSet<String>();
        boolean first = cellBase.isEmpty();
        for (Map.Entry<String, List<Map<String, Object>>> entry : store.tables.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            int fresh = 0;
            int lowest = Integer.MAX_VALUE;
            boolean any = false;
            for (Map<String, Object> row : rows) {
                String id = String.valueOf(row.get("id"));
                keep.add(id);
                if (sortBase.containsKey(id)) {
                    any = true;
                    lowest = Math.min(lowest, sortBase.get(id));
                } else {
                    fresh++;
                }
            }
            int cursor = first ? 0 : (any ? lowest - fresh : -fresh);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                String id = String.valueOf(row.get("id"));
                String origin = seedIds.contains(id) ? "seed" : (originBase.containsKey(id) ? originBase.get(id) : "business");
                int sort = sortBase.containsKey(id) ? sortBase.get(id) : (first ? i : cursor++);
                Map<String, String> cells = cellsOf(row);
                if (!cellBase.containsKey(id)) {
                    jdbc.update("INSERT INTO np_record (id, entity_code, sort_no, origin) VALUES (?, ?, ?, ?)", id, entry.getKey(), sort, origin);
                    for (Map.Entry<String, String> cell : cells.entrySet()) insertCell(id, cell.getKey(), row.get(cell.getKey()));
                } else {
                    if (!origin.equals(originBase.get(id)) || sort != sortBase.get(id) || !entry.getKey().equals(typeBase.get(id))) {
                        jdbc.update("UPDATE np_record SET entity_code = ?, sort_no = ?, origin = ? WHERE id = ?", entry.getKey(), sort, origin, id);
                    }
                    Map<String, String> previous = cellBase.get(id);
                    for (Map.Entry<String, String> cell : cells.entrySet()) {
                        if (!previous.containsKey(cell.getKey())) insertCell(id, cell.getKey(), row.get(cell.getKey()));
                        else if (!cell.getValue().equals(previous.get(cell.getKey()))) updateCell(id, cell.getKey(), row.get(cell.getKey()));
                    }
                    for (String key : new ArrayList<String>(previous.keySet())) {
                        if (!cells.containsKey(key)) jdbc.update("DELETE FROM np_cell WHERE record_id = ? AND field_key = ?", id, key);
                    }
                }
                nextCells.put(id, cells);
                nextSort.put(id, sort);
                nextOrigin.put(id, origin);
                nextType.put(id, entry.getKey());
            }
        }
        for (String id : new ArrayList<String>(cellBase.keySet())) {
            if (!keep.contains(id)) {
                jdbc.update("DELETE FROM np_file WHERE record_id = ?", id);
                jdbc.update("DELETE FROM np_cell WHERE record_id = ?", id);
                jdbc.update("DELETE FROM np_record WHERE id = ?", id);
            }
        }
    }

    public boolean isNumber(String entity, String field) {
        Set<String> fields = numberFields.get(entity);
        return fields != null && fields.contains(field);
    }

    public boolean isLongText(String entity, String field) {
        if ("remark".equals(field) || "body".equals(field)) return true;
        Set<String> fields = textFields.get(entity);
        return fields != null && fields.contains(field);
    }

    public MetaSpec customSpec(String code) {
        Integer builtin = queryInt("SELECT builtin FROM np_meta_entity WHERE code = ?", code);
        if (builtin == null || builtin.intValue() == 1) return null;
        String prefix = jdbc.queryForObject("SELECT code_prefix FROM np_meta_entity WHERE code = ?", String.class, code);
        String status = jdbc.queryForObject("SELECT create_status FROM np_meta_entity WHERE code = ?", String.class, code);
        String saved = jdbc.queryForObject("SELECT saved_text FROM np_meta_entity WHERE code = ?", String.class, code);
        List<String[]> required = new ArrayList<String[]>();
        jdbc.query("SELECT field_key, label FROM np_meta_field WHERE entity_code = ? AND required_flag = 1 ORDER BY sort_no", rs -> {
            required.add(new String[] { rs.getString("field_key"), rs.getString("label") });
        }, code);
        return new MetaSpec(prefix, status, saved, required.toArray(new String[required.size()][]));
    }

    public String actionTarget(String entity, String action) {
        try {
            return jdbc.queryForObject("SELECT target_status FROM np_meta_action WHERE entity_code = ? AND action_key = ?", String.class, entity, action);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public String actionWhen(String entity, String action) {
        try {
            return jdbc.queryForObject("SELECT when_status FROM np_meta_action WHERE entity_code = ? AND action_key = ?", String.class, entity, action);
        } catch (EmptyResultDataAccessException ex) {
            return "";
        }
    }

    public String actionLabel(String entity, String action) {
        try {
            return jdbc.queryForObject("SELECT label FROM np_meta_action WHERE entity_code = ? AND action_key = ?", String.class, entity, action);
        } catch (EmptyResultDataAccessException ex) {
            return action;
        }
    }

    public boolean knownEntity(String code) {
        Integer count = queryInt("SELECT COUNT(*) FROM np_meta_entity WHERE code = ?", code);
        return count != null && count.intValue() > 0;
    }

    public String entityTitle(String code) {
        try {
            return jdbc.queryForObject("SELECT title FROM np_meta_entity WHERE code = ?", String.class, code);
        } catch (EmptyResultDataAccessException ex) {
            return "";
        }
    }

    public boolean customEntity(String code) {
        Integer builtin = queryInt("SELECT builtin FROM np_meta_entity WHERE code = ?", code);
        return builtin != null && builtin.intValue() == 0;
    }

    public String defineEntity(String code, String title, String module, String prefix, String status, String saved, List<Map<String, Object>> fields) {
        if (code == null || !code.matches("[a-z][a-zA-Z0-9]{0,31}")) return "对象编码需以小写字母开头，只含字母和数字";
        if (title == null || title.trim().isEmpty()) return "请填写对象名称";
        Integer exists = queryInt("SELECT COUNT(*) FROM np_meta_entity WHERE code = ?", code);
        if (exists != null && exists.intValue() > 0) return "业务对象已存在，内置对象请追加字段";
        if (fields == null || fields.isEmpty()) return "请至少定义一个字段";
        String prefixText = prefix == null ? "" : prefix.trim();
        String moduleText = module == null || module.trim().isEmpty() ? "扩展业务" : module.trim();
        String statusText = status == null || status.trim().isEmpty() ? "草稿" : status.trim();
        String savedText = saved == null || saved.trim().isEmpty() ? "已保存" : saved.trim();
        jdbc.update("INSERT INTO np_meta_entity (code, title, module_name, code_prefix, create_status, saved_text, builtin) VALUES (?, ?, ?, ?, ?, ?, 0)",
                code, cut(title.trim(), 64), cut(moduleText, 32), cut(prefixText, 8), cut(statusText, 32), cut(savedText, 64));
        int sort = 1;
        for (Map<String, Object> field : fields) {
            String message = insertField(code, field, sort++);
            if (message != null) return message;
        }
        rememberNumbers();
        return null;
    }

    public String addField(String code, Map<String, Object> field) {
        Integer exists = queryInt("SELECT COUNT(*) FROM np_meta_entity WHERE code = ?", code);
        if (exists == null || exists.intValue() == 0) return "业务对象不存在";
        Integer sort = queryInt("SELECT COALESCE(MAX(sort_no), 0) + 1 FROM np_meta_field WHERE entity_code = ?", code);
        String message = insertField(code, field, sort == null ? 1 : sort.intValue());
        if (message == null) rememberNumbers();
        return message;
    }

    public String addAction(String code, String action, String label, String when, String target) {
        if (!customEntity(code)) return "只有企业自建对象可以追加状态动作";
        if (action == null || !action.matches("[a-z][a-zA-Z0-9]{0,31}")) return "动作编码需以小写字母开头";
        if (target == null || target.trim().isEmpty()) return "请填写目标状态";
        jdbc.update("INSERT INTO np_meta_action (entity_code, action_key, label, when_status, target_status) VALUES (?, ?, ?, ?, ?)",
                code, action, cut(label == null || label.trim().isEmpty() ? action : label.trim(), 32), cut(when == null ? "" : when.trim(), 64), cut(target.trim(), 32));
        return null;
    }

    public String removeEntity(String code) {
        if (!customEntity(code)) return "内置业务对象不能删除";
        List<String> ids = jdbc.queryForList("SELECT id FROM np_record WHERE entity_code = ?", String.class, code);
        for (String id : ids) {
            jdbc.update("DELETE FROM np_file WHERE record_id = ?", id);
            jdbc.update("DELETE FROM np_cell WHERE record_id = ?", id);
        }
        if (!ids.isEmpty()) jdbc.update("DELETE FROM np_record WHERE entity_code = ?", code);
        jdbc.update("DELETE FROM np_meta_action WHERE entity_code = ?", code);
        jdbc.update("DELETE FROM np_meta_field WHERE entity_code = ?", code);
        jdbc.update("DELETE FROM np_meta_retired WHERE entity_code = ?", code);
        jdbc.update("DELETE FROM np_meta_entity WHERE code = ?", code);
        rememberNumbers();
        return null;
    }

    public void updateOrg(String name, String sites) {
        jdbc.update("UPDATE np_org SET name = ?, sites = ? WHERE id = 1", cut(name, 64), cut(sites, 255));
    }

    public Map<String, Object> meta() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        Map<String, Object> contract = new LinkedHashMap<String, Object>();
        contract.put("version", "1");
        contract.put("envelope", "{code,msg,data}，code 为 200 表示成功");
        contract.put("list", "GET /api/np/admin/v1/{entity}?page&size&status&q");
        contract.put("read", "GET /api/np/admin/v1/{entity}/{id}");
        contract.put("create", "POST /api/np/admin/v1/{entity}");
        contract.put("update", "PUT /api/np/admin/v1/{entity}/{id}");
        contract.put("delete", "DELETE /api/np/admin/v1/{entity}/{id}");
        contract.put("action", "POST /api/np/admin/v1/{entity}/{id}/actions/{action}");
        contract.put("define", "POST /api/np/admin/v1/meta/entities");
        contract.put("extend", "POST /api/np/admin/v1/meta/entities/{code}/fields");
        contract.put("transition", "POST /api/np/admin/v1/meta/entities/{code}/actions");
        contract.put("org", "GET/PUT /api/np/admin/v1/org");
        contract.put("record", "字段平铺在对象上，并带 id 与 origin。origin 为 seed 表示演示，business 表示企业录入");
        contract.put("storage", "每个字段一行，文本在 value_text，数字在 value_num，可直接用 SQL 查询");
        contract.put("conflict", "版本冲突返回 code 409");
        contract.put("rejected", "业务拒绝返回 code 422，说明在 msg");
        Map<String, String> schedule = new LinkedHashMap<String, String>();
        schedule.put("list", "GET /api/np/admin/board");
        schedule.put("create", "POST /api/np/admin/tasks/custom");
        schedule.put("update", "PUT /api/np/admin/tasks/{id}");
        schedule.put("delete", "DELETE /api/np/admin/tasks/{id}");
        schedule.put("publish", "POST /api/np/admin/tasks/{id}/publish");
        schedule.put("revert", "POST /api/np/admin/tasks/{id}/revert");
        contract.put("schedule", schedule);
        body.put("contract", contract);
        jdbc.query("SELECT name, sites FROM np_org WHERE id = 1", rs -> {
            Map<String, Object> org = new LinkedHashMap<String, Object>();
            org.put("name", rs.getString("name"));
            org.put("sites", rs.getString("sites"));
            body.put("org", org);
        });
        List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();
        jdbc.query("SELECT code, title, module_name, code_prefix, create_status, saved_text, builtin FROM np_meta_entity ORDER BY builtin DESC, code", rs -> {
            Map<String, Object> entity = new LinkedHashMap<String, Object>();
            String code = rs.getString("code");
            entity.put("code", code);
            entity.put("title", rs.getString("title"));
            entity.put("module", rs.getString("module_name"));
            entity.put("prefix", rs.getString("code_prefix"));
            entity.put("status", rs.getString("create_status"));
            entity.put("saved", rs.getString("saved_text"));
            entity.put("builtin", rs.getInt("builtin") == 1);
            entity.put("fields", fieldsOf(code));
            entity.put("actions", actionsOf(code));
            entities.add(entity);
        });
        body.put("entities", entities);
        return body;
    }

    public Map<String, Object> page(String entity, String status, String keyword, int page, int size) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        String statusText = status == null ? "" : status.trim();
        String word = keyword == null ? "" : keyword.trim();
        String like = "%" + word + "%";
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM np_record r WHERE r.entity_code = ? AND (? = '' OR EXISTS (SELECT 1 FROM np_cell c WHERE c.record_id = r.id AND c.field_key = 'status' AND c.value_text = ?)) AND (? = '' OR EXISTS (SELECT 1 FROM np_cell c WHERE c.record_id = r.id AND (c.value_text LIKE ? OR CAST(c.value_num AS CHAR) LIKE ?)))",
                Integer.class, entity, statusText, statusText, word, like, like);
        List<String> ids = jdbc.queryForList("SELECT r.id FROM np_record r WHERE r.entity_code = ? AND (? = '' OR EXISTS (SELECT 1 FROM np_cell c WHERE c.record_id = r.id AND c.field_key = 'status' AND c.value_text = ?)) AND (? = '' OR EXISTS (SELECT 1 FROM np_cell c WHERE c.record_id = r.id AND (c.value_text LIKE ? OR CAST(c.value_num AS CHAR) LIKE ?))) ORDER BY r.sort_no, r.id LIMIT ? OFFSET ?",
                String.class, entity, statusText, statusText, word, like, like, safeSize, (safePage - 1) * safeSize);
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        for (String id : ids) records.add(readRecord(id));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("entity", entity);
        body.put("page", safePage);
        body.put("size", safeSize);
        body.put("total", total == null ? 0 : total.intValue());
        Integer version = queryInt("SELECT version FROM np_erp_state WHERE id = 1");
        body.put("version", version == null ? 0 : version.intValue());
        body.put("records", records);
        return body;
    }

    public Map<String, Object> readRecord(String id) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        try {
            String origin = jdbc.queryForObject("SELECT origin FROM np_record WHERE id = ?", String.class, id);
            row.put("id", id);
            row.put("origin", origin);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
        jdbc.query("SELECT field_key, value_text, value_num FROM np_cell WHERE record_id = ? ORDER BY field_key", rs -> {
            row.put(rs.getString("field_key"), cellValue(rs.getString("value_text"), rs.getBigDecimal("value_num")));
        }, id);
        return row;
    }

    public boolean recordIn(String entity, String id) {
        Integer count = queryInt("SELECT COUNT(*) FROM np_record WHERE id = ? AND entity_code = ?", id, entity);
        return count != null && count.intValue() > 0;
    }

    private List<Map<String, Object>> fieldsOf(String code) {
        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        jdbc.query("SELECT field_key, label, data_type, required_flag, options_text FROM np_meta_field WHERE entity_code = ? ORDER BY sort_no", rs -> {
            Map<String, Object> field = new LinkedHashMap<String, Object>();
            field.put("key", rs.getString("field_key"));
            field.put("label", rs.getString("label"));
            field.put("type", rs.getString("data_type"));
            field.put("required", rs.getInt("required_flag") == 1);
            field.put("options", rs.getString("options_text"));
            fields.add(field);
        }, code);
        return fields;
    }

    private List<Map<String, Object>> actionsOf(String code) {
        List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
        jdbc.query("SELECT action_key, label, when_status, target_status FROM np_meta_action WHERE entity_code = ? ORDER BY action_key", rs -> {
            Map<String, Object> action = new LinkedHashMap<String, Object>();
            action.put("key", rs.getString("action_key"));
            action.put("label", rs.getString("label"));
            action.put("when", rs.getString("when_status"));
            action.put("target", rs.getString("target_status"));
            actions.add(action);
        }, code);
        return actions;
    }

    private String insertField(String code, Map<String, Object> field, int sort) {
        if (field == null || field.get("key") == null) return "请填写字段编码";
        String key = String.valueOf(field.get("key")).trim();
        if (!key.matches("[a-zA-Z][a-zA-Z0-9]{0,31}")) return "字段编码需以字母开头";
        Integer exists = queryInt("SELECT COUNT(*) FROM np_meta_field WHERE entity_code = ? AND field_key = ?", code, key);
        if (exists != null && exists.intValue() > 0) return "字段已存在";
        String type = field.get("type") == null ? "string" : String.valueOf(field.get("type")).trim();
        if (!"string".equals(type) && !"number".equals(type) && !"date".equals(type) && !"datetime".equals(type) && !"enum".equals(type) && !"text".equals(type)) {
            return "字段类型只能是 string、number、text、date、datetime、enum";
        }
        String label = field.get("label") == null ? key : String.valueOf(field.get("label")).trim();
        boolean required = Boolean.TRUE.equals(field.get("required")) || "true".equals(String.valueOf(field.get("required")));
        String options = field.get("options") == null ? "" : String.valueOf(field.get("options"));
        jdbc.update("INSERT INTO np_meta_field (entity_code, field_key, label, data_type, required_flag, sort_no, options_text) VALUES (?, ?, ?, ?, ?, ?, ?)",
                code, key, cut(label, 64), type, required ? 1 : 0, sort, cut(options, 255));
        jdbc.update("DELETE FROM np_meta_retired WHERE entity_code = ? AND field_key = ?", code, key);
        return null;
    }

    public String removeField(String code, String key) {
        if (key == null || "id".equals(key) || "code".equals(key) || "status".equals(key) || "origin".equals(key)) {
            return "编号和状态不能删除";
        }
        Integer exists = queryInt("SELECT COUNT(*) FROM np_meta_field WHERE entity_code = ? AND field_key = ?", code, key);
        if (exists == null || exists.intValue() == 0) return "字段不存在";
        Integer used = queryInt("SELECT COUNT(*) FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE r.entity_code = ? AND c.field_key = ? AND (c.value_num IS NOT NULL OR (c.value_text IS NOT NULL AND TRIM(c.value_text) <> ''))", code, key);
        if (used != null && used.intValue() > 0) return "该字段已有数据，不能删除";
        jdbc.update("DELETE FROM np_cell WHERE field_key = ? AND record_id IN (SELECT id FROM np_record WHERE entity_code = ?)", key, code);
        jdbc.update("DELETE FROM np_meta_field WHERE entity_code = ? AND field_key = ?", code, key);
        jdbc.update("DELETE FROM np_meta_retired WHERE entity_code = ? AND field_key = ?", code, key);
        jdbc.update("INSERT INTO np_meta_retired (entity_code, field_key) VALUES (?, ?)", code, key);
        rememberNumbers();
        return null;
    }

    private void importLegacyDocs() {
        Integer records = queryInt("SELECT COUNT(*) FROM np_record");
        Integer docs = queryInt("SELECT COUNT(*) FROM np_erp_doc");
        if (records != null && records.intValue() > 0) return;
        if (docs == null || docs.intValue() == 0) return;
        transactions.execute(status -> {
            jdbc.query("SELECT id, doc_type, sort_no, origin, payload FROM np_erp_doc ORDER BY sort_no, id", rs -> {
                String id = rs.getString("id");
                jdbc.update("INSERT INTO np_record (id, entity_code, sort_no, origin) VALUES (?, ?, ?, ?)",
                        id, rs.getString("doc_type"), rs.getInt("sort_no"), rs.getString("origin"));
                try {
                    Map<String, Object> row = mapper.readValue(rs.getString("payload"), MAP_TYPE);
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        if ("id".equals(entry.getKey()) || "origin".equals(entry.getKey()) || entry.getValue() == null) continue;
                        insertCell(id, entry.getKey(), entry.getValue());
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException("业务单据无法迁移", ex);
                }
            });
            return null;
        });
    }

    private void rememberNumbers() {
        numberFields.clear();
        textFields.clear();
        jdbc.query("SELECT entity_code, field_key, data_type FROM np_meta_field WHERE data_type IN ('number', 'text')", rs -> {
            boolean text = "text".equals(rs.getString("data_type"));
            Map<String, Set<String>> target = text ? textFields : numberFields;
            Set<String> fields = target.get(rs.getString("entity_code"));
            if (fields == null) {
                fields = new HashSet<String>();
                target.put(rs.getString("entity_code"), fields);
            }
            fields.add(rs.getString("field_key"));
        });
    }

    public String copyEntity(String source, String code, String title) {
        if (code == null || !code.matches("[a-z][a-zA-Z0-9]{0,31}")) return "对象编码需以小写字母开头，只含字母和数字";
        if (title == null || title.trim().isEmpty()) return "请填写对象名称";
        if (!knownEntity(source)) return "业务对象不存在";
        if (knownEntity(code)) return "业务对象已存在";
        jdbc.update("INSERT INTO np_meta_entity (code, title, module_name, code_prefix, create_status, saved_text, builtin) "
                        + "SELECT ?, ?, module_name, '', create_status, saved_text, 0 FROM np_meta_entity WHERE code = ?",
                code, cut(title.trim(), 64), source);
        jdbc.update("INSERT INTO np_meta_field (entity_code, field_key, label, data_type, required_flag, sort_no, options_text) "
                        + "SELECT ?, field_key, label, data_type, required_flag, sort_no, options_text FROM np_meta_field WHERE entity_code = ?",
                code, source);
        jdbc.update("INSERT INTO np_meta_action (entity_code, action_key, label, when_status, target_status) "
                        + "SELECT ?, action_key, label, when_status, target_status FROM np_meta_action WHERE entity_code = ? AND target_status <> ''",
                code, source);
        rememberNumbers();
        return null;
    }

    public String storeFile(String recordId, String name, String mime, byte[] content) {
        Integer rows = queryInt("SELECT COUNT(*) FROM np_record WHERE id = ?", recordId);
        if (rows == null || rows.intValue() == 0) return null;
        if (content == null || content.length == 0 || content.length > 2 * 1024 * 1024) return null;
        String id = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String fileName = name == null || name.trim().isEmpty() ? "附件" : name.trim();
        jdbc.update("INSERT INTO np_file (id, record_id, file_name, mime, content) VALUES (?, ?, ?, ?, ?)",
                id, recordId, cut(fileName, 128), cut(mime == null || mime.isEmpty() ? "application/octet-stream" : mime, 128), content);
        return id;
    }

    public List<Map<String, Object>> listFiles(String recordId) {
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        jdbc.query("SELECT id, file_name, mime, OCTET_LENGTH(content) AS size FROM np_file WHERE record_id = ? ORDER BY id", rs -> {
            Map<String, Object> file = new LinkedHashMap<String, Object>();
            file.put("id", rs.getString("id"));
            file.put("name", rs.getString("file_name"));
            file.put("mime", rs.getString("mime"));
            file.put("size", rs.getInt("size"));
            files.add(file);
        }, recordId);
        return files;
    }

    public Map<String, Object> readFile(String id) {
        try {
            return jdbc.queryForObject("SELECT id, record_id, file_name, mime, content FROM np_file WHERE id = ?", (rs, row) -> {
                Map<String, Object> file = new LinkedHashMap<String, Object>();
                file.put("id", rs.getString("id"));
                file.put("recordId", rs.getString("record_id"));
                file.put("name", rs.getString("file_name"));
                file.put("mime", rs.getString("mime"));
                file.put("content", rs.getBytes("content"));
                return file;
            }, id);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public void deleteFile(String id) {
        jdbc.update("DELETE FROM np_file WHERE id = ?", id);
    }

    private Map<String, String> cellsOf(Map<String, Object> row) {
        Map<String, String> cells = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if ("id".equals(entry.getKey()) || "origin".equals(entry.getKey()) || entry.getValue() == null) continue;
            cells.put(entry.getKey(), sig(entry.getValue()));
        }
        return cells;
    }

    private void insertCell(String id, String key, Object value) {
        Object[] stored = stored(value);
        jdbc.update("INSERT INTO np_cell (record_id, field_key, value_text, value_num) VALUES (?, ?, ?, ?)", id, cut(key, 64), stored[0], stored[1]);
    }

    private void updateCell(String id, String key, Object value) {
        Object[] stored = stored(value);
        jdbc.update("UPDATE np_cell SET value_text = ?, value_num = ? WHERE record_id = ? AND field_key = ?", stored[0], stored[1], id, key);
    }

    private Object[] stored(Object value) {
        if (value instanceof Number) return new Object[] { null, decimal((Number) value) };
        if (value instanceof List || value instanceof Map) {
            try {
                return new Object[] { "j:" + mapper.writeValueAsString(value), null };
            } catch (Exception ex) {
                throw new IllegalStateException("业务单据无法保存", ex);
            }
        }
        return new Object[] { String.valueOf(value), null };
    }

    private Object cellValue(String text, BigDecimal number) {
        if (number != null) {
            BigDecimal plain = number.stripTrailingZeros();
            if (plain.scale() <= 0 && plain.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0 && plain.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0) {
                return Integer.valueOf(plain.intValue());
            }
            return Double.valueOf(plain.doubleValue());
        }
        if (text != null && text.startsWith("j:")) {
            try {
                return mapper.readValue(text.substring(2), Object.class);
            } catch (Exception ex) {
                return text;
            }
        }
        return text == null ? "" : text;
    }

    private String sig(Object value) {
        if (value instanceof Number) return "n:" + decimal((Number) value).stripTrailingZeros().toPlainString();
        if (value instanceof List || value instanceof Map) {
            try {
                return "j:" + mapper.writeValueAsString(value);
            } catch (Exception ex) {
                throw new IllegalStateException("业务单据无法保存", ex);
            }
        }
        return "t:" + String.valueOf(value);
    }

    private static BigDecimal decimal(Number value) {
        if (value instanceof BigDecimal) return ((BigDecimal) value).stripTrailingZeros();
        return new BigDecimal(value.toString());
    }

    private Integer queryInt(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, Integer.class, args);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private Integer lockVersion() {
        try {
            return jdbc.queryForObject("SELECT version FROM np_erp_state WHERE id = 1 FOR UPDATE", Integer.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void loadSeed(ErpStore store) {
        try (InputStream input = new ClassPathResource("np-erp-seed.json").getInputStream()) {
            Map<String, Object> seed = mapper.readValue(input, MAP_TYPE);
            store.seq = ((Number) seed.get("seq")).intValue();
            store.notice = seed.get("notice") == null ? "" : String.valueOf(seed.get("notice"));
            Map<String, List<Map<String, Object>>> tables = mapper.convertValue(seed.get("tables"), TABLES_TYPE);
            store.tables.clear();
            for (String type : ErpStore.TABLES) {
                List<Map<String, Object>> rows = tables.get(type);
                store.tables.put(type, rows == null ? new ArrayList<Map<String, Object>>() : new ArrayList<Map<String, Object>>(rows));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("业务初始数据无法读取", ex);
        }
    }

    private void loadSeedIds() {
        seedIds.clear();
        try (InputStream input = new ClassPathResource("np-erp-seed.json").getInputStream()) {
            Map<String, Object> seed = mapper.readValue(input, MAP_TYPE);
            Map<String, List<Map<String, Object>>> tables = mapper.convertValue(seed.get("tables"), TABLES_TYPE);
            for (List<Map<String, Object>> rows : tables.values()) {
                if (rows == null) continue;
                for (Map<String, Object> row : rows) {
                    if (row.get("id") != null) seedIds.add(String.valueOf(row.get("id")));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("业务初始数据无法读取", ex);
        }
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ? AND TABLE_SCHEMA IN (DATABASE(), 'PUBLIC')",
                Integer.class, table);
        if (tables == null || tables == 0) return;
        Integer columns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA IN (DATABASE(), 'PUBLIC')",
                Integer.class, table, column);
        if (columns != null && columns > 0) return;
        jdbc.execute(ddl);
    }

    private void ensureSchema() {
        DataSource dataSource = jdbc.getDataSource();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("np-erp-schema.sql"), "UTF-8"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
