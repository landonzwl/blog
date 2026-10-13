package com.np.schedule.store;

import com.np.schedule.model.AssignmentItem;
import com.np.schedule.model.BlockItem;
import com.np.schedule.model.Dept;
import com.np.schedule.model.StaffMember;
import com.np.schedule.model.TaskItem;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import com.np.schedule.common.DataConflict;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ScheduleDatabase {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Map<String, String> deptBase = new HashMap<String, String>();
    private final Map<String, String> staffBase = new HashMap<String, String>();
    private final Map<String, String> taskBase = new HashMap<String, String>();
    private final Map<String, String> assignBase = new HashMap<String, String>();
    private final Map<String, String> blockBase = new HashMap<String, String>();
    private final Map<Integer, String> gapBase = new HashMap<Integer, String>();
    private boolean seed = true;

    @org.springframework.beans.factory.annotation.Value("${np.seed:true}")
    public void setSeed(boolean seed) {
        this.seed = seed;
    }

    public ScheduleDatabase(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public static ScheduleDatabase memory() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:np" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return new ScheduleDatabase(new JdbcTemplate(dataSource));
    }

    public void prepare(ScheduleStore store) {
        ensureSchema();
        ensureScript("np-erp-schema.sql");
        ensureVersion("np_state", "ALTER TABLE np_state ADD COLUMN version INT NOT NULL DEFAULT 0");
        ensureVersion("np_erp_state", "ALTER TABLE np_erp_state ADD COLUMN version INT NOT NULL DEFAULT 0");
        ensureColumn("np_task", "origin", "ALTER TABLE np_task ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'live'");
        ensureColumn("np_task", "staff_dept", "ALTER TABLE np_task ADD COLUMN staff_dept VARCHAR(255) NOT NULL DEFAULT ''");
        ensureColumn("np_task", "prefer_own", "ALTER TABLE np_task ADD COLUMN prefer_own VARCHAR(8) NOT NULL DEFAULT '否'");
        widenTo("np_task", "skill", 255);
        widenTo("np_staff", "skill", 255);
        SkillCatalog.ensure(jdbc);
        ensureStaffSkills();
        ensureColumn("np_erp_doc", "origin", "ALTER TABLE np_erp_doc ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'business'");
        if (seed && mysql() && count("np_state") == 0 && new File("data/np.mv.db").isFile()) {
            importLegacy();
            ensureStaffSkills();
        }
        if (count("np_state") == 0) {
            if (seed) {
                SkillCatalog.seed(jdbc);
                ScheduleStore.fillSeed(store);
            } else {
                store.seq = 1;
                store.currentStaffId = "";
            }
            save(store);
        }
        SkillCatalog.migrateSchedule(jdbc);
        loadInto(store);
    }

    public String skillId(String token) {
        return SkillCatalog.idOf(jdbc, token);
    }

    public List<String> skillIds(List<String> tokens) {
        return SkillCatalog.idsOf(jdbc, tokens);
    }

    public Map<String, String> skillBook() {
        return SkillCatalog.book(jdbc);
    }

    public List<Map<String, String>> skillRows() {
        return SkillCatalog.rows(jdbc);
    }

    public List<String> employeeSkillCallers(String skillId) {
        List<String> callers = new ArrayList<String>();
        if (countTables("NP_CELL") == 0 || countTables("NP_RECORD") == 0) return callers;
        List<String[]> rows = new ArrayList<String[]>();
        jdbc.query("SELECT c.value_text, (SELECT n.value_text FROM np_cell n WHERE n.record_id = c.record_id AND n.field_key = 'name') "
                + "FROM np_cell c JOIN np_record r ON r.id = c.record_id WHERE c.field_key = 'skill' AND r.entity_code = 'employees'", rs -> {
            rows.add(new String[]{rs.getString(1), rs.getString(2)});
        });
        for (String[] row : rows) {
            if (!holdsSkill(row[0], skillId)) continue;
            String person = row[1] == null || row[1].trim().isEmpty() ? "未命名" : row[1].trim();
            callers.add("员工档案 · " + person);
        }
        return callers;
    }

    public void stripEmployeeSkill(String skillId, String replacement) {
        if (countTables("NP_CELL") == 0 || countTables("NP_RECORD") == 0) return;
        List<String[]> rows = new ArrayList<String[]>();
        jdbc.query("SELECT c.record_id, c.value_text FROM np_cell c JOIN np_record r ON r.id = c.record_id "
                + "WHERE c.field_key = 'skill' AND r.entity_code = 'employees'", rs -> {
            rows.add(new String[]{rs.getString(1), rs.getString(2)});
        });
        for (String[] row : rows) {
            if (!holdsSkill(row[1], skillId)) continue;
            jdbc.update("UPDATE np_cell SET value_text = ?, value_num = NULL WHERE record_id = ? AND field_key = 'skill'",
                    withoutSkill(row[1], skillId, replacement), row[0]);
        }
    }

    public void deleteSkill(String id) {
        jdbc.update("DELETE FROM np_skill WHERE id = ?", id);
    }

    public static boolean holdsSkill(String stored, String skillId) {
        return splitSkill(stored).contains(skillId);
    }

    public static String withoutSkill(String stored, String skillId, String replacement) {
        List<String> next = new ArrayList<String>();
        for (String token : splitSkill(stored)) {
            if (skillId.equals(token) || next.contains(token)) continue;
            next.add(token);
        }
        if (next.isEmpty()) return replacement == null ? "" : replacement;
        StringBuilder text = new StringBuilder();
        for (String token : next) {
            if (text.length() > 0) text.append(',');
            text.append(token);
        }
        return text.toString();
    }

    private static List<String> splitSkill(String stored) {
        List<String> list = new ArrayList<String>();
        String text = stored == null ? "" : stored.trim();
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
            if (!item.isEmpty() && !list.contains(item)) list.add(item);
        }
        return list;
    }

    public void loadInto(ScheduleStore store) {
        store.depts.clear();
        store.staff.clear();
        store.tasks.clear();
        store.assignments.clear();
        store.blocks.clear();
        store.gaps.clear();
        jdbc.query("SELECT id, name FROM np_dept ORDER BY id", rs -> {
            store.depts.add(new Dept(rs.getString("id"), rs.getString("name")));
        });
        jdbc.query("SELECT id, name, dept_id, skill, max_minutes FROM np_staff ORDER BY id", rs -> {
            store.staff.add(new StaffMember(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("dept_id"),
                    rs.getString("skill"),
                    rs.getInt("max_minutes")));
        });
        jdbc.query("SELECT id, name, dept_id, start_ms, end_ms, start_text, end_text, headcount, skill, staff_dept, prefer_own, priority, status, origin FROM np_task ORDER BY id", rs -> {
            TaskItem task = new TaskItem();
            task.id = rs.getString("id");
            task.name = rs.getString("name");
            task.deptId = rs.getString("dept_id");
            task.start = rs.getLong("start_ms");
            task.end = rs.getLong("end_ms");
            task.startText = rs.getString("start_text");
            task.endText = rs.getString("end_text");
            task.headcount = rs.getInt("headcount");
            task.skill = rs.getString("skill");
            task.staffDept = rs.getString("staff_dept") == null ? "" : rs.getString("staff_dept");
            task.preferOwn = rs.getString("prefer_own") == null || rs.getString("prefer_own").isEmpty() ? "否" : rs.getString("prefer_own");
            task.priority = rs.getInt("priority");
            task.status = rs.getString("status");
            task.origin = rs.getString("origin");
            store.tasks.add(task);
        });
        jdbc.query("SELECT id, staff_id, task_id, source, status FROM np_assignment ORDER BY id", rs -> {
            store.assignments.add(new AssignmentItem(
                    rs.getString("id"),
                    rs.getString("staff_id"),
                    rs.getString("task_id"),
                    rs.getString("source"),
                    rs.getString("status")));
        });
        jdbc.query("SELECT id, staff_id, start_ms, end_ms, start_text, end_text, reason FROM np_block ORDER BY id", rs -> {
            BlockItem block = new BlockItem();
            block.id = rs.getString("id");
            block.staffId = rs.getString("staff_id");
            block.start = rs.getLong("start_ms");
            block.end = rs.getLong("end_ms");
            block.startText = rs.getString("start_text");
            block.endText = rs.getString("end_text");
            block.reason = rs.getString("reason");
            store.blocks.add(block);
        });
        jdbc.query("SELECT message FROM np_gap ORDER BY pos", rs -> {
            store.gaps.add(rs.getString("message"));
        });
        jdbc.query("SELECT next_seq, admin_notice, staff_notice, variance, current_staff_id, version FROM np_state WHERE id = 1", rs -> {
            store.seq = rs.getInt("next_seq");
            store.adminNotice = rs.getString("admin_notice");
            store.staffNotice = rs.getString("staff_notice");
            store.variance = rs.getDouble("variance");
            store.currentStaffId = rs.getString("current_staff_id");
            store.version = rs.getInt("version");
        });
        remember(store);
    }

    public String storedTaskName(String id) {
        return jdbc.queryForObject("SELECT name FROM np_task WHERE id = ?", String.class, id);
    }

    public void save(ScheduleStore store) {
        transactions.execute(status -> {
            Integer current = lockVersion("SELECT version FROM np_state WHERE id = 1 FOR UPDATE");
            if (current != null && current.intValue() != store.version) {
                throw new DataConflict();
            }
            int next = current == null ? 1 : current + 1;
            store.version = next;
            write(store);
            return null;
        });
        remember(store);
    }

    private void write(ScheduleStore store) {
        Set<String> depts = ids(store.depts);
        Set<String> staff = new HashSet<String>();
        Set<String> tasks = new HashSet<String>();
        Set<String> assignments = new HashSet<String>();
        Set<String> blocks = new HashSet<String>();
        for (StaffMember person : store.staff) staff.add(person.id);
        for (TaskItem task : store.tasks) tasks.add(task.id);
        for (AssignmentItem item : store.assignments) assignments.add(item.id);
        for (BlockItem block : store.blocks) blocks.add(block.id);
        removeMissing("np_assignment", assignBase, assignments);
        removeMissing("np_block", blockBase, blocks);
        removeMissing("np_task", taskBase, tasks);
        for (String id : new ArrayList<String>(staffBase.keySet())) {
            if (!staff.contains(id)) jdbc.update("DELETE FROM np_staff_skill WHERE staff_id = ?", id);
        }
        removeMissing("np_staff", staffBase, staff);
        removeMissing("np_dept", deptBase, depts);
        for (Dept dept : store.depts) {
            String sig = dept.name;
            if (!deptBase.containsKey(dept.id)) {
                jdbc.update("INSERT INTO np_dept (id, name) VALUES (?, ?)", dept.id, dept.name);
            } else if (!sig.equals(deptBase.get(dept.id))) {
                jdbc.update("UPDATE np_dept SET name = ? WHERE id = ?", dept.name, dept.id);
            }
        }
        for (StaffMember person : store.staff) {
            String sig = staffSig(person);
            String stored = storedSkill(person);
            if (!staffBase.containsKey(person.id)) {
                jdbc.update("INSERT INTO np_staff (id, name, dept_id, skill, max_minutes) VALUES (?, ?, ?, ?, ?)",
                        person.id, person.name, person.deptId, stored, person.maxMinutes);
            } else if (!sig.equals(staffBase.get(person.id))) {
                jdbc.update("UPDATE np_staff SET name = ?, dept_id = ?, skill = ?, max_minutes = ? WHERE id = ?",
                        person.name, person.deptId, stored, person.maxMinutes, person.id);
            }
        }
        for (TaskItem task : store.tasks) {
            String origin = task.origin == null || task.origin.isEmpty() ? "live" : task.origin;
            String sig = taskSig(task);
            if (!taskBase.containsKey(task.id)) {
                jdbc.update("INSERT INTO np_task (id, name, dept_id, start_ms, end_ms, start_text, end_text, headcount, skill, staff_dept, prefer_own, priority, status, origin) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        task.id, cut(task.name, 128), task.deptId, task.start, task.end, task.startText, task.endText,
                        task.headcount, cut(task.skill, 255), cut(task.staffDept, 255), preferOwn(task), task.priority, task.status, origin);
            } else if (!sig.equals(taskBase.get(task.id))) {
                jdbc.update("UPDATE np_task SET name = ?, dept_id = ?, start_ms = ?, end_ms = ?, start_text = ?, end_text = ?, headcount = ?, skill = ?, staff_dept = ?, prefer_own = ?, priority = ?, status = ?, origin = ? WHERE id = ?",
                        cut(task.name, 128), task.deptId, task.start, task.end, task.startText, task.endText,
                        task.headcount, cut(task.skill, 255), cut(task.staffDept, 255), preferOwn(task), task.priority, task.status, origin, task.id);
            }
        }
        for (AssignmentItem item : store.assignments) {
            String sig = assignSig(item);
            if (!assignBase.containsKey(item.id)) {
                jdbc.update("INSERT INTO np_assignment (id, staff_id, task_id, source, status) VALUES (?, ?, ?, ?, ?)",
                        item.id, item.staffId, item.taskId, item.source, item.status);
            } else if (!sig.equals(assignBase.get(item.id))) {
                jdbc.update("UPDATE np_assignment SET staff_id = ?, task_id = ?, source = ?, status = ? WHERE id = ?",
                        item.staffId, item.taskId, item.source, item.status, item.id);
            }
        }
        for (BlockItem block : store.blocks) {
            String sig = blockSig(block);
            if (!blockBase.containsKey(block.id)) {
                jdbc.update("INSERT INTO np_block (id, staff_id, start_ms, end_ms, start_text, end_text, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        block.id, block.staffId, block.start, block.end, block.startText, block.endText, cut(block.reason, 20));
            } else if (!sig.equals(blockBase.get(block.id))) {
                jdbc.update("UPDATE np_block SET staff_id = ?, start_ms = ?, end_ms = ?, start_text = ?, end_text = ?, reason = ? WHERE id = ?",
                        block.staffId, block.start, block.end, block.startText, block.endText, cut(block.reason, 20), block.id);
            }
        }
        Set<Integer> gapKeep = new HashSet<Integer>();
        for (int i = 0; i < store.gaps.size(); i++) {
            String message = cut(store.gaps.get(i), 255);
            gapKeep.add(i);
            if (!gapBase.containsKey(i)) {
                jdbc.update("INSERT INTO np_gap (pos, message) VALUES (?, ?)", i, message);
            } else if (!message.equals(gapBase.get(i))) {
                jdbc.update("UPDATE np_gap SET message = ? WHERE pos = ?", message, i);
            }
        }
        for (Integer pos : new ArrayList<Integer>(gapBase.keySet())) {
            if (!gapKeep.contains(pos)) jdbc.update("DELETE FROM np_gap WHERE pos = ?", pos);
        }
        int updated = jdbc.update("UPDATE np_state SET next_seq = ?, admin_notice = ?, staff_notice = ?, variance = ?, current_staff_id = ?, version = ? WHERE id = 1",
                store.seq, cut(store.adminNotice, 255), cut(store.staffNotice, 255), store.variance, store.currentStaffId, store.version);
        if (updated == 0) {
            jdbc.update("INSERT INTO np_state (id, next_seq, admin_notice, staff_notice, variance, current_staff_id, version) VALUES (1, ?, ?, ?, ?, ?, ?)",
                    store.seq, cut(store.adminNotice, 255), cut(store.staffNotice, 255), store.variance, store.currentStaffId, store.version);
        }
    }

    private void remember(ScheduleStore store) {
        deptBase.clear();
        staffBase.clear();
        taskBase.clear();
        assignBase.clear();
        blockBase.clear();
        gapBase.clear();
        for (Dept dept : store.depts) deptBase.put(dept.id, dept.name);
        for (StaffMember person : store.staff) staffBase.put(person.id, staffSig(person));
        for (TaskItem task : store.tasks) taskBase.put(task.id, taskSig(task));
        for (AssignmentItem item : store.assignments) assignBase.put(item.id, assignSig(item));
        for (BlockItem block : store.blocks) blockBase.put(block.id, blockSig(block));
        for (int i = 0; i < store.gaps.size(); i++) gapBase.put(i, cut(store.gaps.get(i), 255));
    }

    private void removeMissing(String table, Map<String, String> baseline, Set<String> keep) {
        for (String id : new ArrayList<String>(baseline.keySet())) {
            if (!keep.contains(id)) jdbc.update("DELETE FROM " + table + " WHERE id = ?", id);
        }
    }

    private Set<String> ids(List<Dept> depts) {
        Set<String> ids = new HashSet<String>();
        for (Dept dept : depts) ids.add(dept.id);
        return ids;
    }

    private static String staffSig(StaffMember person) {
        return person.name + "\u001f" + person.deptId + "\u001f" + person.skill + "\u001f" + person.maxMinutes;
    }

    private static String storedSkill(StaffMember person) {
        String skill = person.skill == null ? "" : person.skill;
        return skill.length() <= 255 ? skill : skill.substring(0, 255);
    }

    private void ensureStaffSkills() {
        if (countTables("NP_STAFF") == 0 || countTables("NP_STAFF_SKILL") > 0) return;
        jdbc.execute("CREATE TABLE np_staff_skill ("
                + "staff_id VARCHAR(32) NOT NULL, skill VARCHAR(32) NOT NULL, "
                + "PRIMARY KEY (staff_id, skill), "
                + "CONSTRAINT fk_np_staff_skill_staff FOREIGN KEY (staff_id) REFERENCES np_staff (id))");
    }

    private String taskSig(TaskItem task) {
        String origin = task.origin == null || task.origin.isEmpty() ? "live" : task.origin;
        return cut(task.name, 128) + "\u001f" + task.deptId + "\u001f" + task.start + "\u001f" + task.end + "\u001f"
                + task.startText + "\u001f" + task.endText + "\u001f" + task.headcount + "\u001f" + task.skill + "\u001f"
                + task.staffDept + "\u001f" + preferOwn(task) + "\u001f"
                + task.priority + "\u001f" + task.status + "\u001f" + origin;
    }

    private static String assignSig(AssignmentItem item) {
        return item.staffId + "\u001f" + item.taskId + "\u001f" + item.source + "\u001f" + item.status;
    }

    private String blockSig(BlockItem block) {
        return block.staffId + "\u001f" + block.start + "\u001f" + block.end + "\u001f" + block.startText + "\u001f"
                + block.endText + "\u001f" + cut(block.reason, 20);
    }

    private void ensureSchema() {
        if (countTables("NP_STATE") > 0) {
            return;
        }
        ensureScript("np-schema.sql");
    }

    private void ensureScript(String script) {
        DataSource dataSource = jdbc.getDataSource();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(script), "UTF-8"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void ensureVersion(String table, String ddl) {
        ensureColumn(table, "version", ddl);
    }

    private void ensureColumn(String table, String column, String ddl) {
        if (countTables(table) == 0 || countColumns(table, column) > 0) {
            return;
        }
        jdbc.execute(ddl);
    }

    private boolean mysql() {
        DataSource dataSource = jdbc.getDataSource();
        String url = "";
        if (dataSource instanceof HikariDataSource) {
            url = ((HikariDataSource) dataSource).getJdbcUrl();
        } else if (dataSource instanceof DriverManagerDataSource) {
            url = ((DriverManagerDataSource) dataSource).getUrl();
        }
        return url != null && url.startsWith("jdbc:mysql:");
    }

    private int count(String table) {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return rows == null ? 0 : rows;
    }

    private int countTables(String table) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ? AND UPPER(TABLE_SCHEMA) IN (UPPER(DATABASE()), 'PUBLIC')",
                Integer.class, table.toUpperCase());
        return existing == null ? 0 : existing;
    }

    private int countColumns(String table, String column) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ? AND UPPER(TABLE_SCHEMA) IN (UPPER(DATABASE()), 'PUBLIC')",
                Integer.class, table.toUpperCase(), column.toUpperCase());
        return existing == null ? 0 : existing;
    }

    private void widen(String table, String column) {
        widenTo(table, column, 32);
    }

    private void widenTo(String table, String column, int size) {
        if (countTables(table) == 0 || countColumns(table, column) == 0) return;
        Integer length = jdbc.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ? AND UPPER(TABLE_SCHEMA) IN (UPPER(DATABASE()), 'PUBLIC')",
                Integer.class, table.toUpperCase(), column.toUpperCase());
        if (length != null && length.intValue() < size) {
            jdbc.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " VARCHAR(" + size + ") NOT NULL");
        }
    }

    private Integer lockVersion(String sql) {
        try {
            return jdbc.queryForObject(sql, Integer.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void importLegacy() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:file:./data/np;MODE=MySQL;IFEXISTS=TRUE;ACCESS_MODE_DATA=r");
        source.setUsername("sa");
        source.setPassword("");
        JdbcTemplate from = new JdbcTemplate(source);
        try {
            transactions.execute(status -> {
                jdbc.update("SET FOREIGN_KEY_CHECKS = 0");
                copyStaffTables(from);
                copyErp(from);
                jdbc.update("SET FOREIGN_KEY_CHECKS = 1");
                return null;
            });
        } catch (RuntimeException ex) {
            jdbc.update("SET FOREIGN_KEY_CHECKS = 0");
            jdbc.update("DELETE FROM np_assignment");
            jdbc.update("DELETE FROM np_block");
            jdbc.update("DELETE FROM np_task");
            jdbc.update("DELETE FROM np_staff_skill");
            jdbc.update("DELETE FROM np_staff");
            jdbc.update("DELETE FROM np_dept");
            jdbc.update("DELETE FROM np_gap");
            jdbc.update("DELETE FROM np_state");
            jdbc.update("DELETE FROM np_erp_doc");
            jdbc.update("DELETE FROM np_erp_state");
            jdbc.update("SET FOREIGN_KEY_CHECKS = 1");
            throw new IllegalStateException("无法从原有数据库迁入", ex);
        }
    }

    private void copyStaffTables(JdbcTemplate from) {
        from.query("SELECT id, name FROM np_dept", rs -> {
            jdbc.update("INSERT INTO np_dept (id, name) VALUES (?, ?)", rs.getString("id"), rs.getString("name"));
        });
        from.query("SELECT id, name, dept_id, skill, max_minutes FROM np_staff", rs -> {
            jdbc.update("INSERT INTO np_staff (id, name, dept_id, skill, max_minutes) VALUES (?, ?, ?, ?, ?)",
                    rs.getString("id"), rs.getString("name"), rs.getString("dept_id"), rs.getString("skill"), rs.getInt("max_minutes"));
        });
        from.query("SELECT id, name, dept_id, start_ms, end_ms, start_text, end_text, headcount, skill, priority, status FROM np_task", rs -> {
            jdbc.update("INSERT INTO np_task (id, name, dept_id, start_ms, end_ms, start_text, end_text, headcount, skill, priority, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    rs.getString("id"), rs.getString("name"), rs.getString("dept_id"), rs.getLong("start_ms"), rs.getLong("end_ms"),
                    rs.getString("start_text"), rs.getString("end_text"), rs.getInt("headcount"), rs.getString("skill"), rs.getInt("priority"), rs.getString("status"));
        });
        from.query("SELECT id, staff_id, task_id, source, status FROM np_assignment", rs -> {
            jdbc.update("INSERT INTO np_assignment (id, staff_id, task_id, source, status) VALUES (?, ?, ?, ?, ?)",
                    rs.getString("id"), rs.getString("staff_id"), rs.getString("task_id"), rs.getString("source"), rs.getString("status"));
        });
        from.query("SELECT id, staff_id, start_ms, end_ms, start_text, end_text, reason FROM np_block", rs -> {
            jdbc.update("INSERT INTO np_block (id, staff_id, start_ms, end_ms, start_text, end_text, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    rs.getString("id"), rs.getString("staff_id"), rs.getLong("start_ms"), rs.getLong("end_ms"),
                    rs.getString("start_text"), rs.getString("end_text"), rs.getString("reason"));
        });
        from.query("SELECT pos, message FROM np_gap", rs -> {
            jdbc.update("INSERT INTO np_gap (pos, message) VALUES (?, ?)", rs.getInt("pos"), rs.getString("message"));
        });
        from.query("SELECT next_seq, admin_notice, staff_notice, variance, current_staff_id FROM np_state", rs -> {
            jdbc.update("INSERT INTO np_state (id, next_seq, admin_notice, staff_notice, variance, current_staff_id, version) VALUES (1, ?, ?, ?, ?, ?, 0)",
                    rs.getInt("next_seq"), rs.getString("admin_notice"), rs.getString("staff_notice"), rs.getDouble("variance"), rs.getString("current_staff_id"));
        });
    }

    private void copyErp(JdbcTemplate from) {
        Integer tables = from.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'NP_ERP_DOC'",
                Integer.class);
        if (tables == null || tables == 0) {
            return;
        }
        from.query("SELECT id, doc_type, sort_no, payload FROM np_erp_doc", rs -> {
            jdbc.update("INSERT INTO np_erp_doc (id, doc_type, sort_no, payload) VALUES (?, ?, ?, ?)",
                    rs.getString("id"), rs.getString("doc_type"), rs.getInt("sort_no"), rs.getString("payload"));
        });
        from.query("SELECT next_seq, notice FROM np_erp_state", rs -> {
            jdbc.update("INSERT INTO np_erp_state (id, next_seq, notice, version) VALUES (1, ?, ?, 0)",
                    rs.getInt("next_seq"), rs.getString("notice"));
        });
    }

    private static String preferOwn(TaskItem task) {
        return task != null && "是".equals(task.preferOwn) ? "是" : "否";
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
