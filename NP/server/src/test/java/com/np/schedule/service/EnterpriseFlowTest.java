package com.np.schedule.service;

import com.np.schedule.model.ErpBoard;
import com.np.schedule.model.StaffMember;
import com.np.schedule.store.ErpDatabase;
import com.np.schedule.store.ScheduleDatabase;
import com.np.schedule.store.ScheduleStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseFlowTest {

    @Test
    void employeesShareStaffAndAgreedLeaveBecomesBlock() {
        ScheduleDatabase scheduleDb = ScheduleDatabase.memory();
        ScheduleStore scheduleStore = new ScheduleStore(scheduleDb);
        ScheduleService schedule = new ScheduleService(scheduleStore);
        ErpService erp = new ErpService(ErpDatabase.memory(), schedule, null);

        boolean linked = false;
        for (Map<String, Object> row : erp.board().tables.get("employees")) {
            if ("李四".equals(String.valueOf(row.get("name")))) {
                assertEquals("s2", String.valueOf(row.get("staffId")));
                linked = true;
            }
        }
        assertTrue(linked);
        assertEquals("s5", staffId(schedule, "孙七"));
        assertEquals(32 * 60, minutes(schedule, "孙七"));

        boolean blocked = false;
        scheduleStore.reload();
        for (com.np.schedule.model.BlockItem block : scheduleStore.blocks) {
            if ("Llv1".equals(block.id) && "s2".equals(block.staffId) && "请假".equals(block.reason)) {
                blocked = true;
            }
        }
        assertTrue(blocked);

        Map<String, Object> clash = new LinkedHashMap<String, Object>();
        clash.put("name", "李四");
        clash.put("start", "2026-03-02 09:00");
        clash.put("end", "2026-03-02 10:00");
        clash.put("days", 1);
        ErpBoard created = erp.create("leaves", clash);
        String leaveId = String.valueOf(created.tables.get("leaves").get(0).get("id"));
        ErpBoard rejected = erp.act("leaves", leaveId, "agree");
        assertTrue(rejected.notice.startsWith("时间冲突，不能同意请假"));
        assertEquals("待审批", String.valueOf(find(rejected, "leaves", leaveId).get("status")));

        Map<String, Object> free = new LinkedHashMap<String, Object>();
        free.put("name", "李四");
        free.put("start", "2026-04-01 08:00");
        free.put("end", "2026-04-01 12:00");
        free.put("days", 1);
        ErpBoard again = erp.create("leaves", free);
        assertEquals("已提交审批", again.notice);
        String freeId = String.valueOf(again.tables.get("leaves").get(0).get("id"));
        String approvalId = "";
        for (Map<String, Object> item : again.tables.get("approvals")) {
            if ("leaves".equals(String.valueOf(item.get("source"))) && freeId.equals(String.valueOf(item.get("sourceId")))) {
                approvalId = String.valueOf(item.get("id"));
            }
        }
        assertTrue(approvalId.length() > 0);
        ErpBoard agreed = erp.act("approvals", approvalId, "agree");
        assertEquals("已同意请假", agreed.notice);
        scheduleStore.reload();
        boolean placed = false;
        for (com.np.schedule.model.BlockItem block : scheduleStore.blocks) {
            if (("L" + freeId).equals(block.id)) placed = true;
        }
        assertTrue(placed);

        ErpBoard undone = erp.act("leaves", freeId, "undo");
        assertEquals("已撤回审批", undone.notice);
        scheduleStore.reload();
        for (com.np.schedule.model.BlockItem block : scheduleStore.blocks) {
            assertTrue(!("L" + freeId).equals(block.id));
        }

        Map<String, Object> hire = new LinkedHashMap<String, Object>();
        hire.put("name", "新人甲");
        hire.put("phone", "13800001111");
        hire.put("dept", "西区门店");
        hire.put("skill", "夜班");
        assertEquals("已保存员工", erp.create("employees", hire).notice);
        assertEquals("west", deptOf(schedule, "新人甲"));
        assertEquals("夜班", skillOf(schedule, "新人甲"));
    }

    @Test
    void agreedLeaveKeepsExistingBlock() {
        ScheduleDatabase scheduleDb = ScheduleDatabase.memory();
        ScheduleStore scheduleStore = new ScheduleStore(scheduleDb);
        ScheduleService schedule = new ScheduleService(scheduleStore);
        com.np.schedule.model.BlockItem block = new com.np.schedule.model.BlockItem();
        block.id = "b40";
        block.staffId = "s2";
        block.startText = "2026-03-04 08:00";
        block.endText = "2026-03-04 12:00";
        com.np.schedule.time.TimeRules.Range range = com.np.schedule.time.TimeRules.parseRange(block.startText, block.endText);
        block.start = range.start.ms;
        block.end = range.end.ms;
        block.reason = "请假";
        scheduleStore.blocks.add(block);
        scheduleStore.flush();

        ErpService erp = new ErpService(ErpDatabase.memory(), schedule, null);
        Map<String, Object> leave = null;
        for (Map<String, Object> row : erp.board().tables.get("leaves")) {
            if ("lv1".equals(String.valueOf(row.get("id")))) leave = row;
        }
        assertEquals("已同意", String.valueOf(leave.get("status")));
        assertEquals("b40", String.valueOf(leave.get("blockId")));
        scheduleStore.reload();
        int same = 0;
        for (com.np.schedule.model.BlockItem item : scheduleStore.blocks) {
            if ("s2".equals(item.staffId) && "请假".equals(item.reason)) same += 1;
        }
        assertEquals(1, same);
    }

    private static Map<String, Object> find(ErpBoard board, String table, String id) {
        for (Map<String, Object> row : board.tables.get(table)) {
            if (id.equals(String.valueOf(row.get("id")))) return row;
        }
        return null;
    }

    private static String staffId(ScheduleService schedule, String name) {
        for (StaffMember person : schedule.listStaff()) {
            if (name.equals(person.name)) return person.id;
        }
        return "";
    }

    private static int minutes(ScheduleService schedule, String name) {
        for (StaffMember person : schedule.listStaff()) {
            if (name.equals(person.name)) return person.maxMinutes;
        }
        return -1;
    }

    private static String deptOf(ScheduleService schedule, String name) {
        for (StaffMember person : schedule.listStaff()) {
            if (name.equals(person.name)) return person.deptId;
        }
        return "";
    }

    private static String skillOf(ScheduleService schedule, String name) {
        com.np.schedule.model.AdminBoard board = schedule.board();
        for (StaffMember person : board.staff) {
            if (!name.equals(person.name) || person.skill == null) continue;
            StringBuilder text = new StringBuilder();
            for (String id : person.skill.split("[,，]")) {
                String label = id.trim();
                if (label.isEmpty()) continue;
                if (board.skills != null) {
                    for (java.util.Map<String, String> row : board.skills) {
                        if (label.equals(row.get("id"))) label = row.get("name");
                    }
                }
                if (text.length() > 0) text.append('、');
                text.append(label);
            }
            return text.toString();
        }
        return "";
    }
}
