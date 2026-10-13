package com.np.schedule.service;

import com.np.schedule.model.ErpBoard;
import com.np.schedule.store.ErpDatabase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceFlowTest {

    @Test
    void customEntityIsStoredAsCellsAndLeavesOtherDocumentsUntouched() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        String before = database.storedPayload("so1");
        Map<String, Object> definition = new LinkedHashMap<String, Object>();
        definition.put("code", "visits");
        definition.put("title", "客户拜访");
        definition.put("module", "销售管理");
        definition.put("prefix", "VS");
        definition.put("status", "草稿");
        definition.put("saved", "已保存拜访");
        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        fields.add(field("customer", "客户", "string", true));
        fields.add(field("qty", "人数", "number", true));
        definition.put("fields", fields);
        assertNull(service.defineEntity(definition));

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("customer", "玄武超市");
        body.put("qty", 2);
        ErpBoard created = service.create("visits", body);
        assertEquals("已保存拜访", created.notice);
        String id = String.valueOf(created.tables.get("visits").get(0).get("id"));
        assertEquals("玄武超市", database.textCell(id, "customer"));
        assertEquals(before, database.storedPayload("so1"));
        assertTrue(database.knownEntity("visits"));

        Map<String, Object> action = new LinkedHashMap<String, Object>();
        action.put("key", "close");
        action.put("label", "完成");
        action.put("when", "草稿");
        action.put("target", "已完成");
        assertNull(service.addAction("visits", action));
        ErpBoard closed = service.act("visits", id, "close");
        assertEquals("已完成", closed.notice);
        assertEquals("已完成", String.valueOf(database.readRecord(id).get("status")));
        assertEquals(before, database.storedPayload("so1"));
        assertNull(service.removeEntity("visits"));
        assertEquals(before, database.storedPayload("so1"));
    }

    @Test
    void noticeKeepsLongBodyRemarkAndFile() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 20; i++) body.append("门店周末照常营业，请各班组按时到岗。");
        Map<String, Object> notice = new LinkedHashMap<String, Object>();
        notice.put("title", "周末营业");
        notice.put("body", body.toString());
        notice.put("dept", "东区门店");
        notice.put("date", "2026-10-12");
        notice.put("remark", "张贴在休息室");
        ErpBoard created = service.create("notices", notice);
        assertEquals("已发布公告", created.notice);
        String id = String.valueOf(created.tables.get("notices").get(0).get("id"));
        assertEquals(body.toString(), database.textCell(id, "body"));
        assertEquals("张贴在休息室", database.textCell(id, "remark"));
        String fileId = database.storeFile(id, "poster.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, database.listFiles(id).size());
        assertEquals("hello", new String((byte[]) database.readFile(fileId).get("content"), StandardCharsets.UTF_8));
        assertNull(service.copyEntity("notices", "siteNotes", "门店通知"));
        assertTrue(database.knownEntity("siteNotes"));
        database.deleteFile(fileId);
        assertEquals(0, database.listFiles(id).size());
    }

    @Test
    void emptyFieldCanBeRemovedAndFilledFieldStays() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        Map<String, Object> definition = new LinkedHashMap<String, Object>();
        definition.put("code", "visits");
        definition.put("title", "客户拜访");
        definition.put("status", "草稿");
        definition.put("saved", "已保存拜访");
        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        fields.add(field("customer", "客户", "string", true));
        definition.put("fields", fields);
        assertNull(service.defineEntity(definition));
        assertNull(service.addField("visits", field("note", "说明", "string", false)));
        assertNull(service.removeField("visits", "note"));
        assertNull(service.addField("visits", field("note", "说明", "string", false)));

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("customer", "玄武超市");
        body.put("note", "已到店");
        ErpBoard created = service.create("visits", body);
        assertEquals("已保存拜访", created.notice);
        String id = String.valueOf(created.tables.get("visits").get(0).get("id"));
        assertEquals("该字段已有数据，不能删除", service.removeField("visits", "note"));
        assertEquals("已到店", database.textCell(id, "note"));
        assertEquals("编号和状态不能删除", service.removeField("visits", "status"));
        assertNull(service.removeEntity("visits"));
    }

    private static Map<String, Object> field(String key, String label, String type, boolean required) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("key", key);
        field.put("label", label);
        field.put("type", type);
        field.put("required", required);
        return field;
    }
}
