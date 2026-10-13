package com.np.schedule.service;

import com.np.schedule.model.CustomRequest;
import com.np.schedule.model.ErpBoard;
import com.np.schedule.store.ErpDatabase;
import com.np.schedule.store.ScheduleDatabase;
import com.np.schedule.store.ScheduleStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowWriteTest {

    @Test
    void changingOneDocumentLeavesTheOthersUntouched() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        String before = database.storedPayload("so1");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "只改这一家");
        body.put("contact", "甲");
        body.put("phone", "13800001999");
        ErpBoard created = service.create("customers", body);
        assertEquals(before, database.storedPayload("so1"));
        assertEquals("seed", String.valueOf(row(created, "salesOrders", "so1").get("origin")));
        String id = String.valueOf(created.tables.get("customers").get(0).get("id"));
        assertEquals("business", String.valueOf(row(created, "customers", id).get("origin")));
        for (Map<String, Object> item : created.tables.get("approvals")) {
            assertEquals("leaves", item.get("source"));
            assertEquals("待审批", item.get("status"));
        }
        Map<String, Object> approval = new LinkedHashMap<String, Object>();
        approval.put("source", "customers");
        approval.put("sourceId", id);
        approval.put("title", "改客户资料");
        approval.put("body", "联系人改为甲");
        approval.put("payload", "{\"contact\":\"甲\"}");
        ErpBoard saved = service.create("approvals", approval);
        Map<String, Object> ticket = saved.tables.get("approvals").get(0);
        String approvalId = String.valueOf(ticket.get("id"));
        assertEquals("客户档案", ticket.get("type"));
        assertEquals(id, ticket.get("sourceId"));
        assertEquals("待审批", ticket.get("status"));
        assertEquals("未落地", ticket.get("applied"));
        assertEquals("", ticket.get("decided"));
        ErpBoard agreed = service.act("approvals", approvalId, "agree");
        Map<String, Object> decided = row(agreed, "approvals", approvalId);
        assertEquals("已同意审批", agreed.notice);
        assertEquals("已同意", decided.get("status"));
        assertEquals("未落地", decided.get("applied"));
        assertTrue(String.valueOf(decided.get("decided")).length() >= 16);
        assertEquals("只改这一家", row(agreed, "customers", id).get("name"));
        assertEquals(before, database.storedPayload("so1"));
        ErpBoard loaded = new ErpService(database).board();
        assertEquals("已同意", row(loaded, "approvals", approvalId).get("status"));
        assertEquals("未落地", row(loaded, "approvals", approvalId).get("applied"));
        assertEquals("seed", database.originOf("so1"));
        assertEquals("business", database.originOf(id));
    }

    @Test
    void changingOneTaskLeavesTheOthersUntouched() {
        ScheduleDatabase database = ScheduleDatabase.memory();
        ScheduleService service = new ScheduleService(new ScheduleStore(database));
        String before = database.storedTaskName("t2");
        CustomRequest request = new CustomRequest();
        request.name = "只改这一条";
        request.deptId = "east";
        request.startText = "2026-03-02 09:00";
        request.endText = "2026-03-02 11:00";
        request.headcount = 1d;
        request.skill = "白班";
        request.priority = 2d;
        assertEquals("任务已更新", service.updateTask("t1", request).notice);
        assertEquals(before, database.storedTaskName("t2"));
        assertEquals("只改这一条", database.storedTaskName("t1"));
        assertTrue(before.length() > 0);
    }

    private static Map<String, Object> row(ErpBoard board, String table, String id) {
        for (Map<String, Object> item : board.tables.get(table)) {
            if (id.equals(item.get("id"))) {
                return item;
            }
        }
        throw new AssertionError(id);
    }
}
