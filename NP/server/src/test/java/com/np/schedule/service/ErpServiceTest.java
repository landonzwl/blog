package com.np.schedule.service;

import com.np.schedule.model.ErpBoard;
import com.np.schedule.store.ErpDatabase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErpServiceTest {

    @Test
    void departmentParentRejectsCyclesAndFollowsRename() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("name", "华南区");
        ErpBoard created = service.create("depts", root);
        assertEquals("已保存部门", created.notice);
        String rootId = idOf(created, "depts", "华南区");

        Map<String, Object> child = new LinkedHashMap<String, Object>();
        child.put("name", "深圳店");
        child.put("parent", "华南区");
        ErpBoard nested = service.create("depts", child);
        assertEquals("已保存部门", nested.notice);
        String childId = idOf(nested, "depts", "深圳店");

        Map<String, Object> self = new LinkedHashMap<String, Object>();
        self.put("name", "华南区");
        self.put("parent", "华南区");
        assertEquals("不能把本部门设为上级", service.update("depts", rootId, self).notice);

        Map<String, Object> cycle = new LinkedHashMap<String, Object>();
        cycle.put("name", "华南区");
        cycle.put("parent", "深圳店");
        assertEquals("上级不能是本部门的下级", service.update("depts", rootId, cycle).notice);

        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        missing.put("name", "华南区");
        missing.put("parent", "不存在的部门");
        assertEquals("上级部门不存在", service.update("depts", rootId, missing).notice);

        Map<String, Object> rename = new LinkedHashMap<String, Object>();
        rename.put("name", "华南大区");
        ErpBoard renamed = service.update("depts", rootId, rename);
        assertEquals("已保存部门", renamed.notice);
        assertEquals(rootId, String.valueOf(row(renamed, "depts", childId).get("parent")));
        assertEquals("华南大区", String.valueOf(row(renamed, "depts", rootId).get("name")));

        Map<String, Object> person = new LinkedHashMap<String, Object>();
        person.put("name", "华南员工");
        person.put("phone", "13800002222");
        person.put("dept", "华南大区");
        assertEquals("已保存员工", service.create("employees", person).notice);
        Map<String, Object> forced = new LinkedHashMap<String, Object>();
        forced.put("name", "华南大区");
        forced.put("people", 99);
        ErpBoard counted = service.update("depts", rootId, forced);
        assertEquals("已保存部门", counted.notice);
        assertEquals(Integer.valueOf(1), row(counted, "depts", rootId).get("people"));
        assertEquals(Integer.valueOf(0), row(counted, "depts", childId).get("people"));
    }

    @Test
    void skillAndNoticeDeptKeepSeveralValues() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        Map<String, Object> person = new LinkedHashMap<String, Object>();
        person.put("name", "多技能");
        person.put("phone", "13800001111");
        person.put("skill", java.util.Arrays.asList("白班", "夜班"));
        assertEquals("已保存员工", service.create("employees", person).notice);
        Map<String, Object> notice = new LinkedHashMap<String, Object>();
        notice.put("title", "两店通知");
        notice.put("date", "2026-10-12");
        notice.put("dept", java.util.Arrays.asList("总部", "东区门店"));
        assertEquals("已发布公告", service.create("notices", notice).notice);
        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        missing.put("title", "无此部门");
        missing.put("date", "2026-10-12");
        missing.put("dept", java.util.Arrays.asList("不存在的店"));
        assertEquals("部门不存在", service.create("notices", missing).notice);

        ErpBoard loaded = new ErpService(database).board();
        Object skill = named(loaded, "employees", "多技能").get("skill");
        String day = "";
        String night = "";
        for (java.util.Map<String, String> row : loaded.skills) {
            if ("白班".equals(row.get("name"))) day = row.get("id");
            if ("夜班".equals(row.get("name"))) night = row.get("id");
        }
        assertEquals(day + "," + night, String.valueOf(skill));
        assertEquals("sk-day,sk-night", String.valueOf(skill));
        Object dept = named(loaded, "notices", "两店通知").get("dept");
        assertTrue(dept instanceof java.util.List);
        assertEquals(java.util.Arrays.asList(idOf(loaded, "depts", "总部"), idOf(loaded, "depts", "东区门店")), dept);
    }

    @Test
    void employeePostStoresAnIdAndDeleteClearsTheReference() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        Map<String, Object> person = new LinkedHashMap<String, Object>();
        person.put("name", "岗位甲");
        person.put("phone", "13800002222");
        person.put("post", "店员");
        assertEquals("已保存员工", service.create("employees", person).notice);

        ErpBoard board = service.board();
        String id = "";
        for (java.util.Map<String, String> row : board.posts) {
            if ("店员".equals(row.get("name"))) id = row.get("id");
        }
        assertEquals(id, String.valueOf(named(board, "employees", "岗位甲").get("post")));
        assertTrue(!id.isEmpty());

        java.util.Map<String, Object> created = service.createPost("收银岗");
        assertEquals("收银岗", created.get("name"));
        java.util.Map<String, Object> preview = service.deletePost(id, false);
        assertEquals(Boolean.FALSE, preview.get("deleted"));
        assertTrue(String.valueOf(preview.get("callers")).contains("员工档案 · 岗位甲"));
        java.util.Map<String, Object> removed = service.deletePost(id, true);
        assertEquals(Boolean.TRUE, removed.get("deleted"));
        assertEquals("", String.valueOf(named(service.board(), "employees", "岗位甲").get("post")));
        boolean still = false;
        for (java.util.Map<String, String> row : service.board().posts) {
            if ("店员".equals(row.get("name"))) still = true;
        }
        assertTrue(!still);
        java.util.Map<String, Object> unused = service.deletePost(String.valueOf(created.get("id")), false);
        assertEquals(Boolean.TRUE, unused.get("deleted"));
    }

    @Test
    void documentsPersistAndShipUpdatesStock() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        ErpBoard board = service.board();
        assertEquals(3, board.tables.get("customers").size());
        assertEquals("草稿", status(board, "salesOrders", "so3"));

        ErpBoard audited = service.act("salesOrders", "so3", "audit");
        assertEquals("已审核销售订单", audited.notice);
        assertEquals("已审核", status(audited, "salesOrders", "so3"));

        ErpBoard pushed = service.act("salesOrders", "so1", "pushDelivery");
        assertEquals("已下推发货单", pushed.notice);
        String deliveryId = String.valueOf(pushed.tables.get("deliveries").get(0).get("id"));
        ErpBoard shipped = service.act("deliveries", deliveryId, "ship");
        assertEquals("已确认发货", shipped.notice);
        assertEquals(66, number(shipped, "stock", "st1", "qty"));
        assertEquals("已完成", status(shipped, "salesOrders", "so1"));

        ErpBoard blocked = service.act("jobs", "mo1", "finish");
        assertEquals("材料库存不足，不能完工", blocked.notice);

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "玄武超市");
        body.put("contact", "林晓");
        body.put("phone", "13800001008");
        ErpBoard created = service.create("customers", body);
        assertEquals("已保存客户", created.notice);
        assertEquals(4, created.tables.get("customers").size());

        ErpService again = new ErpService(database);
        ErpBoard loaded = again.board();
        assertEquals("已完成", status(loaded, "salesOrders", "so1"));
        assertEquals(4, loaded.tables.get("customers").size());
        assertTrue(loaded.notice.startsWith("已保存客户") || "已保存客户".equals(loaded.notice));
    }

    @Test
    void rollbackRestoresStockMoneyAndRejectsPartialWrites() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        int handles = number(service.board(), "stock", "st1", "qty");

        ErpBoard pushed = service.act("salesOrders", "so1", "pushDelivery");
        String deliveryId = String.valueOf(pushed.tables.get("deliveries").get(0).get("id"));
        service.act("deliveries", deliveryId, "ship");
        ErpBoard restored = service.act("deliveries", deliveryId, "unship");
        assertEquals("已撤回发货", restored.notice);
        assertEquals(handles, number(restored, "stock", "st1", "qty"));
        assertEquals("待发货", status(restored, "salesOrders", "so1"));
        assertEquals("待发货", status(restored, "deliveries", deliveryId));

        ErpBoard paid = service.act("deliveries", "dn2", "unship");
        assertEquals("已收款，不能撤回发货", paid.notice);
        assertEquals(8, number(paid, "stock", "st2", "qty"));

        service.act("salesOrders", "so3", "reject");
        Map<String, Object> order = new LinkedHashMap<String, Object>();
        order.put("customer", "鼓楼便利");
        order.put("material", "门锁");
        order.put("qty", 8);
        order.put("price", 10);
        order.put("date", "2026-03-05");
        ErpBoard edited = service.update("salesOrders", "so3", order);
        assertEquals("已保存并退回草稿", edited.notice);
        assertEquals("草稿", status(edited, "salesOrders", "so3"));
        assertEquals(8, number(edited, "salesOrders", "so3", "qty"));

        int locks = number(service.board(), "stock", "st2", "qty");
        ErpBoard received = service.act("receipts", "rc1", "receive");
        assertEquals("已确认收货", received.notice);
        ErpBoard unreceived = service.act("receipts", "rc1", "unreceive");
        assertEquals("已撤回收货", unreceived.notice);
        assertEquals(locks, number(unreceived, "stock", "st2", "qty"));
        assertEquals("待收货", status(unreceived, "purchaseOrders", purchaseId(unreceived)));

        int balance = number(service.board(), "receivables", "ar2", "balance");
        assertEquals("已核销收款", service.act("collects", "sk2", "settle").notice);
        ErpBoard unsettled = service.act("collects", "sk2", "unsettle");
        assertEquals("已撤回核销", unsettled.notice);
        assertEquals(balance, number(unsettled, "receivables", "ar2", "balance"));
        assertEquals("待核销", status(unsettled, "collects", "sk2"));

        ErpBoard denied = service.act("checks", "qc1", "fail");
        assertEquals("库存不足，不能隔离", denied.notice);
        assertEquals("待检验", status(denied, "checks", "qc1"));
        assertEquals(8, number(denied, "stock", "st2", "qty"));

        Map<String, Object> check = new LinkedHashMap<String, Object>();
        check.put("type", "来料检验");
        check.put("material", "门锁");
        check.put("qty", 2);
        check.put("date", "2026-03-05");
        ErpBoard made = service.create("checks", check);
        String checkId = String.valueOf(made.tables.get("checks").get(0).get("id"));
        ErpBoard failedCheck = service.act("checks", checkId, "fail");
        assertEquals("已判定不合格，不合格数量已隔离", failedCheck.notice);
        assertEquals(6, number(failedCheck, "stock", "st2", "qty"));
        assertEquals(2, number(failedCheck, "stock", "st2", "hold"));
        assertEquals("隔离", status(failedCheck, "stock", "st2"));
        ErpBoard undone = service.act("checks", checkId, "undo");
        assertEquals("已撤回检验", undone.notice);
        assertEquals("待检验", status(undone, "checks", checkId));
        assertEquals(8, number(undone, "stock", "st2", "qty"));
        assertEquals(0, number(undone, "stock", "st2", "hold"));
        assertEquals("预警", status(undone, "stock", "st2"));

        Map<String, Object> outbound = new LinkedHashMap<String, Object>();
        outbound.put("type", "其他出库");
        outbound.put("warehouse", "东区仓");
        outbound.put("material", "门把手");
        outbound.put("qty", 1000);
        outbound.put("date", "2026-03-05");
        ErpBoard draft = service.create("ioBills", outbound);
        String ioId = String.valueOf(draft.tables.get("ioBills").get(0).get("id"));
        ErpBoard blocked = service.act("ioBills", ioId, "post");
        assertEquals("库存不足，不能出库", blocked.notice);
        assertEquals(handles, number(blocked, "stock", "st1", "qty"));
        assertEquals(4, blocked.tables.get("stock").size());

        Map<String, Object> count = new LinkedHashMap<String, Object>();
        count.put("warehouse", "东区仓");
        count.put("material", "门把手");
        count.put("date", "2026-03-05");
        count.put("actual", handles - 1);
        ErpBoard submitted = service.update("counts", "ck2", count);
        assertEquals("已提交审批", submitted.notice);
        assertEquals("待审批", status(submitted, "counts", "ck2"));
        assertEquals(handles, number(submitted, "stock", "st1", "qty"));
        assertEquals("差异请在审批中心处理", service.act("counts", "ck2", "agree").notice);
        String approvalId = openApproval(submitted, "ck2");
        ErpBoard counted = service.act("approvals", approvalId, "agree");
        assertEquals("已完成盘点", counted.notice);
        assertEquals("已同意", status(counted, "approvals", approvalId));
        assertEquals("已落地", String.valueOf(row(counted, "approvals", approvalId).get("applied")));
        assertEquals(handles - 1, number(counted, "stock", "st1", "qty"));
        ErpBoard countBack = service.act("counts", "ck2", "undo");
        assertEquals("已撤回盘点", countBack.notice);
        assertEquals(handles, number(countBack, "stock", "st1", "qty"));

        Map<String, Object> rejectedCount = new LinkedHashMap<String, Object>();
        rejectedCount.put("warehouse", "东区仓");
        rejectedCount.put("material", "门把手");
        rejectedCount.put("date", "2026-03-05");
        rejectedCount.put("actual", handles - 2);
        ErpBoard queued = service.create("counts", rejectedCount);
        String rejectedId = String.valueOf(queued.tables.get("counts").get(0).get("id"));
        String rejectApproval = openApproval(queued, rejectedId);
        ErpBoard rejected = service.act("approvals", rejectApproval, "reject");
        assertEquals("已驳回盘点", rejected.notice);
        assertEquals("已驳回", status(rejected, "counts", rejectedId));
        assertEquals("已驳回", status(rejected, "approvals", rejectApproval));
        assertEquals("已落地", String.valueOf(row(rejected, "approvals", rejectApproval).get("applied")));
        assertEquals(handles, number(rejected, "stock", "st1", "qty"));
    }

    private static String purchaseId(ErpBoard board) {
        String token = String.valueOf(row(board, "receipts", "rc1").get("order"));
        for (Map<String, Object> item : board.tables.get("purchaseOrders")) {
            if (token.equals(item.get("id")) || token.equals(item.get("code"))) {
                return String.valueOf(item.get("id"));
            }
        }
        throw new AssertionError(token);
    }

    @Test
    void contractCodeQueryReturnsOnlyThatContractsOrders() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        ErpBoard board = service.board();
        String contractId = "";
        String contractCode = "";
        for (Map<String, Object> item : board.tables.get("contracts")) {
            if ("CT20260115001".equals(item.get("code"))) {
                contractId = String.valueOf(item.get("id"));
                contractCode = String.valueOf(item.get("code"));
            }
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("contract", contractCode);
        body.put("customer", Arrays.asList("江宁物业", "栖霞学校"));
        body.put("material", Arrays.asList("门把手", "门锁"));
        body.put("qty", 2);
        body.put("price", 10);
        body.put("date", "2026-03-06");
        ErpBoard created = service.create("salesOrders", body);
        assertEquals("已保存销售订单", created.notice);
        String orderId = "";
        for (Map<String, Object> item : created.tables.get("salesOrders")) {
            if (contractId.equals(String.valueOf(item.get("contract")))) orderId = String.valueOf(item.get("id"));
        }
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("contract", contractCode);
        query.put("columns", columns);
        List<Map<String, Object>> rows = rowsOf(service.query("salesOrders", query));
        assertEquals(1, rows.size());
        assertEquals(orderId, rows.get(0).get("id"));
        boolean linked = false;
        for (Map<String, String> item : service.options("contracts")) {
            if (contractId.equals(item.get("id")) && contractCode.equals(item.get("label"))) linked = true;
        }
        assertTrue(linked);
        Object customers = rows.get(0).get("customer");
        assertTrue(customers instanceof List);
        assertEquals(2, ((List<?>) customers).size());
    }

    @Test
    void purchaseContractLinksOrderAndReceiptById() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        service.create("suppliers", supplier("浦口五金", "13800002001"));
        service.create("suppliers", supplier("江北建材", "13800002002"));
        Map<String, Object> contract = new LinkedHashMap<String, Object>();
        contract.put("supplier", Arrays.asList("浦口五金", "江北建材"));
        contract.put("amount", 5000);
        contract.put("start", "2026-03-01");
        contract.put("end", "2026-12-31");
        ErpBoard saved = service.create("purchaseContracts", contract);
        assertEquals("已保存采购合同", saved.notice);
        Map<String, Object> stored = saved.tables.get("purchaseContracts").get(0);
        String contractId = String.valueOf(stored.get("id"));
        String contractCode = String.valueOf(stored.get("code"));
        assertEquals("执行中", status(service.act("purchaseContracts", contractId, "audit"), "purchaseContracts", contractId));

        Map<String, Object> order = new LinkedHashMap<String, Object>();
        order.put("contract", contractCode);
        order.put("supplier", Arrays.asList("浦口五金", "江北建材"));
        order.put("material", Arrays.asList("门把手", "门锁"));
        order.put("qty", 2);
        order.put("price", 10);
        order.put("date", "2026-03-06");
        ErpBoard created = service.create("purchaseOrders", order);
        assertEquals("已保存采购订单", created.notice);
        String orderId = "";
        for (Map<String, Object> item : created.tables.get("purchaseOrders")) {
            if (contractId.equals(String.valueOf(item.get("contract")))) orderId = String.valueOf(item.get("id"));
        }
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("contract", contractCode);
        query.put("columns", columns);
        List<Map<String, Object>> rows = rowsOf(service.query("purchaseOrders", query));
        assertEquals(1, rows.size());
        assertEquals(orderId, rows.get(0).get("id"));
        assertEquals(2, ((List<?>) rows.get(0).get("supplier")).size());
        boolean labeled = false;
        for (Map<String, String> item : service.options("purchaseContracts")) {
            if (contractId.equals(item.get("id")) && contractCode.equals(item.get("label"))) labeled = true;
        }
        assertTrue(labeled);

        assertEquals("已审核", status(service.act("purchaseOrders", orderId, "audit"), "purchaseOrders", orderId));
        ErpBoard pushed = service.act("purchaseOrders", orderId, "pushReceipt");
        assertEquals("已下推收货单", pushed.notice);
        Object suppliers = pushed.tables.get("receipts").get(0).get("supplier");
        assertTrue(suppliers instanceof List);
        assertEquals(2, ((List<?>) suppliers).size());
        columns.clear();
        columns.put("order", orderId);
        List<Map<String, Object>> receipts = rowsOf(service.query("receipts", query));
        assertEquals(1, receipts.size());
        columns.clear();
        columns.put("supplier", "浦口五金");
        assertEquals(1, rowsOf(service.query("purchaseOrders", query)).size());
    }

    @Test
    void unusedCustomerCanBeDeleted() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "待删客户");
        body.put("contact", "甲");
        body.put("phone", "13800003333");
        ErpBoard created = service.create("customers", body);
        assertEquals("已保存客户", created.notice);
        String id = idOf(created, "customers", "待删客户");
        assertEquals("已删除客户", service.act("customers", id, "remove").notice);

        created = service.create("customers", body);
        id = idOf(created, "customers", "待删客户");
        Map<String, Object> contract = new LinkedHashMap<String, Object>();
        contract.put("customer", id);
        contract.put("amount", 100);
        contract.put("start", "2026-01-01");
        contract.put("end", "2026-12-31");
        assertEquals("已保存合同", service.create("contracts", contract).notice);
        assertEquals("该客户已被销售合同使用，不能删除", service.act("customers", id, "remove").notice);
    }

    @Test
    void supplierCategoryAndExpenseTypeComeFromArchive() {
        ErpService service = new ErpService(ErpDatabase.memory());
        String hardware = optionId(service.options("supplierCategories"), "五金");
        String travel = optionId(service.options("expenseTypes"), "交通");
        assertFalse(hardware.isEmpty());
        assertFalse(travel.isEmpty());
        ErpBoard board = service.board();
        assertTrue(String.valueOf(named(board, "suppliers", "宁通五金").get("category")).contains(hardware));
        boolean linked = false;
        for (Map<String, Object> row : board.tables.get("expenses")) {
            if (String.valueOf(row.get("type")).contains(travel)) linked = true;
        }
        assertTrue(linked);
    }

    private static String optionId(java.util.List<Map<String, String>> options, String label) {
        for (Map<String, String> item : options) {
            if (label.equals(item.get("label"))) return item.get("id");
        }
        return "";
    }

    @Test
    void quickOptionSavesArchiveByNameAndKeepsDocumentRules() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        Map<String, String> created = service.quickOption("units", "箱");
        assertTrue(created.get("notice").startsWith("已"));
        assertEquals("箱", created.get("label"));
        Map<String, String> blocked = service.quickOption("customers", "下拉新客户");
        assertEquals("", blocked.get("id"));
        assertEquals("请到对应档案新建", blocked.get("notice"));
        Map<String, String> denied = service.quickOption("salesOrders", "临时订单");
        assertEquals("", denied.get("id"));
        assertEquals("请到对应档案新建", denied.get("notice"));
    }

    @Test
    void inventoryMovesEveryLineAndKeepsBookQuantity() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        ErpBoard board = service.board();
        int handles = number(board, "stock", "st1", "qty");
        int locks = number(board, "stock", "st2", "qty");
        Map<String, Object> handle = row(board, "stock", "st1");

        Map<String, Object> stockEdit = new LinkedHashMap<String, Object>();
        stockEdit.put("material", handle.get("material"));
        stockEdit.put("warehouse", handle.get("warehouse"));
        stockEdit.put("qty", 1);
        stockEdit.put("hold", 9);
        stockEdit.put("safety", 100);
        ErpBoard kept = service.update("stock", "st1", stockEdit);
        assertEquals("已保存库存", kept.notice);
        assertEquals(handles, number(kept, "stock", "st1", "qty"));
        assertEquals(null, row(kept, "stock", "st1").get("hold"));
        assertEquals(100, number(kept, "stock", "st1", "safety"));
        assertEquals("预警", status(kept, "stock", "st1"));

        Map<String, Object> inbound = new LinkedHashMap<String, Object>();
        inbound.put("type", "其他入库");
        inbound.put("date", "2026-03-06");
        inbound.put("qty", 5);
        inbound.put("warehouse", "东区仓");
        inbound.put("material", Arrays.asList("门把手", "门锁"));
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        lines.add(stockMove("门把手", "东区仓", 2));
        lines.add(stockMove("门锁", "东区仓", 3));
        inbound.put("lines", lines);
        ErpBoard drafted = service.create("ioBills", inbound);
        String ioId = String.valueOf(drafted.tables.get("ioBills").get(0).get("id"));
        ErpBoard posted = service.act("ioBills", ioId, "post");
        assertEquals("已过账", posted.notice);
        assertEquals(handles + 2, number(posted, "stock", "st1", "qty"));
        assertEquals(locks + 3, number(posted, "stock", "st2", "qty"));
        ErpBoard reversed = service.act("ioBills", ioId, "unpost");
        assertEquals("已撤回过账", reversed.notice);
        assertEquals(handles, number(reversed, "stock", "st1", "qty"));
        assertEquals(locks, number(reversed, "stock", "st2", "qty"));

        Map<String, Object> check = new LinkedHashMap<String, Object>();
        check.put("type", "来料检验");
        check.put("material", "门锁");
        check.put("qty", 2);
        check.put("date", "2026-03-06");
        String checkId = String.valueOf(service.create("checks", check).tables.get("checks").get(0).get("id"));
        ErpBoard held = service.act("checks", checkId, "fail");
        assertEquals(6, number(held, "stock", "st2", "qty"));
        assertEquals(2, number(held, "stock", "st2", "hold"));
        Map<String, Object> outbound = new LinkedHashMap<String, Object>();
        outbound.put("type", "其他出库");
        outbound.put("warehouse", "东区仓");
        outbound.put("material", "门锁");
        outbound.put("qty", 5);
        outbound.put("date", "2026-03-06");
        String outId = String.valueOf(service.create("ioBills", outbound).tables.get("ioBills").get(0).get("id"));
        ErpBoard blocked = service.act("ioBills", outId, "post");
        assertEquals("库存不足，不能出库", blocked.notice);
        assertEquals(6, number(blocked, "stock", "st2", "qty"));
        service.act("checks", checkId, "undo");

        Map<String, Object> delivery = new LinkedHashMap<String, Object>();
        delivery.put("customer", "江宁物业");
        delivery.put("material", Arrays.asList("门把手", "门锁"));
        delivery.put("qty", 1);
        delivery.put("warehouse", "东区仓");
        delivery.put("date", "2026-03-06");
        delivery.put("order", "so-none");
        String deliveryId = String.valueOf(service.create("deliveries", delivery).tables.get("deliveries").get(0).get("id"));
        ErpBoard shipped = service.act("deliveries", deliveryId, "ship");
        assertEquals("已确认发货", shipped.notice);
        assertEquals(handles - 1, number(shipped, "stock", "st1", "qty"));
        assertEquals(locks - 1, number(shipped, "stock", "st2", "qty"));
        String deliveryCode = String.valueOf(row(shipped, "deliveries", deliveryId).get("code"));
        assertEquals(2, sourced(shipped, deliveryCode, "销售出库"));
        ErpBoard unshipped = service.act("deliveries", deliveryId, "unship");
        assertEquals("已撤回发货", unshipped.notice);
        assertEquals(handles, number(unshipped, "stock", "st1", "qty"));
        assertEquals(locks, number(unshipped, "stock", "st2", "qty"));
        assertEquals(0, sourced(unshipped, deliveryCode, "销售出库"));

        Map<String, Object> count = new LinkedHashMap<String, Object>();
        count.put("warehouse", "西区仓");
        count.put("material", "清洁剂");
        count.put("date", "2026-03-06");
        String countId = String.valueOf(service.create("counts", count).tables.get("counts").get(0).get("id"));
        Map<String, Object> cleanerOut = new LinkedHashMap<String, Object>();
        cleanerOut.put("type", "其他出库");
        cleanerOut.put("warehouse", "西区仓");
        cleanerOut.put("material", "清洁剂");
        cleanerOut.put("qty", 1);
        cleanerOut.put("date", "2026-03-06");
        String cleanerId = String.valueOf(service.create("ioBills", cleanerOut).tables.get("ioBills").get(0).get("id"));
        ErpBoard frozen = service.act("ioBills", cleanerId, "post");
        assertEquals("盘点中，不能变动库存", frozen.notice);
        assertEquals(40, number(frozen, "stock", "st3", "qty"));

        int beforeMove = number(service.board(), "stock", "st1", "qty");
        ErpBoard moved = service.act("transfers", "tr1", "move");
        assertEquals("已确认调拨", moved.notice);
        assertEquals(beforeMove - 10, number(moved, "stock", "st1", "qty"));
        String transferCode = String.valueOf(row(moved, "transfers", "tr1").get("code"));
        assertEquals(1, sourced(moved, transferCode, "调拨出库"));
        assertEquals(1, sourced(moved, transferCode, "调拨入库"));
        ErpBoard movedBack = service.act("transfers", "tr1", "unmove");
        assertEquals("已撤回调拨", movedBack.notice);
        assertEquals(beforeMove, number(movedBack, "stock", "st1", "qty"));
        assertEquals(0, sourced(movedBack, transferCode, "调拨出库"));
        assertEquals(0, sourced(movedBack, transferCode, "调拨入库"));

        Map<String, Object> actual = new LinkedHashMap<String, Object>();
        actual.put("material", handle.get("material"));
        actual.put("warehouse", handle.get("warehouse"));
        actual.put("actual", handles - 1);
        Map<String, Object> counted = new LinkedHashMap<String, Object>();
        counted.put("warehouse", handle.get("warehouse"));
        counted.put("material", handle.get("material"));
        counted.put("date", "2026-03-06");
        counted.put("lines", Arrays.asList(actual));
        ErpBoard submittedCount = service.create("counts", counted);
        assertEquals("已提交审批", submittedCount.notice);
        assertEquals(handles, number(submittedCount, "stock", "st1", "qty"));
        String actualId = String.valueOf(submittedCount.tables.get("counts").get(0).get("id"));
        ErpBoard finished = service.act("approvals", openApproval(submittedCount, actualId), "agree");
        assertEquals("已完成盘点", finished.notice);
        assertEquals(handles - 1, number(finished, "stock", "st1", "qty"));
        String countCode = String.valueOf(row(finished, "counts", actualId).get("code"));
        assertEquals(1, sourced(finished, countCode, "盘亏"));
        ErpBoard countBack = service.act("counts", actualId, "undo");
        assertEquals("已撤回盘点", countBack.notice);
        assertEquals(handles, number(countBack, "stock", "st1", "qty"));
        assertEquals(0, sourced(countBack, countCode, "盘亏"));
    }

    @Test
    void otherModulesLinkDocumentsById() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        Map<String, Object> job = new LinkedHashMap<String, Object>();
        job.put("product", "门把手");
        job.put("qty", 1);
        job.put("owner", Arrays.asList("陈晨"));
        job.put("start", "2026-03-06");
        ErpBoard created = service.create("jobs", job);
        assertEquals("已保存生产工单", created.notice);
        String jobId = String.valueOf(created.tables.get("jobs").get(0).get("id"));
        String jobCode = String.valueOf(created.tables.get("jobs").get(0).get("code"));
        ErpBoard finishedJob = service.act("jobs", jobId, "finish");
        assertEquals("已完工并下推质检", finishedJob.notice);
        assertTrue(String.valueOf(row(finishedJob, "jobs", jobId).get("finished")).length() >= 16);
        assertEquals("该工单已有质检单", service.act("jobs", jobId, "pushCheck").notice);
        ErpBoard pushed = finishedJob;
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("job", jobId);
        query.put("columns", columns);
        assertEquals(1, rowsOf(service.query("checks", query)).size());
        String ownerId = idOf(finishedJob, "employees", "陈晨");
        Object checkOwner = rowsOf(service.query("checks", query)).get(0).get("owner");
        assertTrue(String.valueOf(checkOwner).contains(ownerId));
        String label = "";
        for (Map<String, String> option : service.options("jobs")) {
            if (jobId.equals(option.get("id"))) label = option.get("label");
        }
        assertEquals(jobCode, label);
        assertEquals("已撤回下推", service.act("jobs", jobId, "undoPush").notice);
        assertEquals(0, rowsOf(service.query("checks", query)).size());
        assertEquals("只有生产中的工单可以删除", service.act("jobs", jobId, "remove").notice);
        assertEquals("已撤回完工", service.act("jobs", jobId, "undo").notice);
        int jobsBefore = service.board().tables.get("jobs").size();
        assertEquals("已删除", service.act("jobs", jobId, "remove").notice);
        assertEquals(jobsBefore - 1, service.board().tables.get("jobs").size());

        String staffId = idOf(service.board(), "employees", "李四");
        int before = rowsOf(service.query("attendance", queryOf("name", staffId))).size();
        Map<String, Object> attendance = new LinkedHashMap<String, Object>();
        attendance.put("name", Arrays.asList("李四"));
        attendance.put("date", "2026-03-06");
        assertEquals("已保存考勤", service.create("attendance", attendance).notice);
        assertEquals(before + 1, rowsOf(service.query("attendance", queryOf("name", staffId))).size());
    }

    @Test
    void materialBalanceAndBomStayOnePair() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        ErpBoard board = service.board();
        int stockCount = board.tables.get("stock").size();
        int handles = number(board, "stock", "st1", "qty");
        int safety = number(board, "stock", "st1", "safety");
        Map<String, Object> again = new LinkedHashMap<String, Object>();
        again.put("material", Arrays.asList("门把手"));
        again.put("warehouse", Arrays.asList("东区仓"));
        again.put("safety", 1);
        ErpBoard skipped = service.create("stock", again);
        assertEquals("该物料在该仓库已有库存，请直接修改", skipped.notice);
        assertEquals(stockCount, skipped.tables.get("stock").size());
        assertEquals(handles, number(skipped, "stock", "st1", "qty"));
        assertEquals(safety, number(skipped, "stock", "st1", "safety"));

        String cleaner = idOf(board, "materials", "清洁剂");
        String kit = idOf(board, "materials", "门锁套装");
        String east = idOf(board, "warehouses", "东区仓");
        String cleanerCode = String.valueOf(row(board, "materials", cleaner).get("code"));
        Map<String, Object> several = new LinkedHashMap<String, Object>();
        several.put("material", Arrays.asList("清洁剂", "门锁套装"));
        several.put("warehouse", "东区仓");
        several.put("safety", 3);
        ErpBoard opened = service.create("stock", several);
        assertEquals("已保存库存，重复的物料仓库已跳过", opened.notice);
        assertEquals(stockCount + 1, opened.tables.get("stock").size());
        Map<String, Object> created = null;
        for (Map<String, Object> item : opened.tables.get("stock")) {
            if (cleaner.equals(item.get("material")) && east.equals(item.get("warehouse"))) created = item;
        }
        assertTrue(created != null);
        assertFalse(created.get("material") instanceof List);
        assertEquals(cleanerCode, created.get("code"));
        assertEquals(0, number(opened, "stock", String.valueOf(created.get("id")), "qty"));
        assertEquals(number(board, "stock", "st4", "qty"), number(opened, "stock", "st4", "qty"));

        int bomCount = board.tables.get("boms").size();
        Map<String, Object> bom = new LinkedHashMap<String, Object>();
        bom.put("product", Arrays.asList("门把手", "门锁套装"));
        Map<String, Object> cleanerLine = new LinkedHashMap<String, Object>();
        cleanerLine.put("material", "清洁剂");
        cleanerLine.put("qty", 2);
        bom.put("lines", Arrays.asList(cleanerLine));
        bom.put("version", "V9");
        ErpBoard lines = service.create("boms", bom);
        assertEquals("已保存物料清单", lines.notice);
        assertEquals(bomCount + 2, lines.tables.get("boms").size());
        int pairs = 0;
        for (Map<String, Object> item : lines.tables.get("boms")) {
            if (!"清洁剂".equals(labelOfMaterial(lines, item.get("material")))) continue;
            assertFalse(item.get("product") instanceof List);
            assertFalse(item.get("material") instanceof List);
            assertEquals(cleaner, item.get("material"));
            assertEquals(2, number(lines, "boms", String.valueOf(item.get("id")), "qty"));
            assertTrue(String.valueOf(item.get("id")).length() > 0);
            pairs++;
        }
        assertEquals(2, pairs);
        Map<String, Object> perQty = new LinkedHashMap<String, Object>();
        perQty.put("product", "清洁剂");
        Map<String, Object> one = new LinkedHashMap<String, Object>();
        one.put("material", "门把手");
        one.put("qty", 1);
        Map<String, Object> three = new LinkedHashMap<String, Object>();
        three.put("material", "门锁套装");
        three.put("qty", 3);
        perQty.put("lines", Arrays.asList(one, three));
        perQty.put("version", "V2");
        ErpBoard split = service.create("boms", perQty);
        assertEquals("已保存物料清单", split.notice);
        assertEquals(1, number(split, "boms", bomId(split, cleaner, idOf(board, "materials", "门把手")), "qty"));
        assertEquals(3, number(split, "boms", bomId(split, cleaner, kit), "qty"));
        Map<String, Object> same = new LinkedHashMap<String, Object>();
        same.put("product", "门把手");
        Map<String, Object> sameLine = new LinkedHashMap<String, Object>();
        sameLine.put("material", "门把手");
        sameLine.put("qty", 1);
        same.put("lines", Arrays.asList(sameLine));
        same.put("version", "V1");
        assertEquals("产品和材料不能相同", service.create("boms", same).notice);
        assertTrue(kit != null && !kit.isEmpty());

        ErpBoard fresh = service.board();
        String unitId = idOf(fresh, "units", "个");
        String categoryId = idOf(fresh, "categories", "五金");
        Map<String, Object> handleMaster = null;
        for (Map<String, Object> item : fresh.tables.get("materials")) {
            if ("门把手".equals(item.get("name"))) handleMaster = item;
        }
        assertEquals(unitId, String.valueOf(handleMaster.get("unit")));
        assertEquals(categoryId, String.valueOf(handleMaster.get("category")));
        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        missing.put("from", "西区仓");
        missing.put("material", "门锁");
        missing.put("to", "西区仓");
        missing.put("qty", 1);
        missing.put("date", "2026-03-06");
        assertEquals("该仓库没有这个物料，不能调拨", service.create("transfers", missing).notice);
        Map<String, Object> transfer = new LinkedHashMap<String, Object>();
        transfer.put("from", "东区仓");
        transfer.put("material", "门把手");
        transfer.put("to", "西区仓");
        transfer.put("qty", 1);
        transfer.put("date", "2026-03-06");
        ErpBoard locked = service.create("transfers", transfer);
        assertEquals("已保存调拨单", locked.notice);
        assertEquals(String.valueOf(row(fresh, "materials", idOf(fresh, "materials", "门把手")).get("code")), locked.tables.get("transfers").get(0).get("materialCode"));
        assertFalse(locked.tables.get("transfers").get(0).get("material") instanceof List);
        Map<String, Object> matched = new LinkedHashMap<String, Object>();
        matched.put("warehouse", "东区仓");
        matched.put("material", "门把手");
        matched.put("actual", number(fresh, "stock", "st1", "qty"));
        matched.put("date", "2026-03-06");
        ErpBoard even = service.create("counts", matched);
        assertEquals("已完成盘点", even.notice);
        assertEquals(number(fresh, "stock", "st1", "qty"), number(even, "stock", "st1", "qty"));

        ErpBoard listed = service.board();
        String dropId = bomId(listed, idOf(listed, "materials", "清洁剂"), idOf(listed, "materials", "门把手"));
        int listedCount = listed.tables.get("boms").size();
        ErpBoard dropped = service.act("boms", dropId, "remove");
        assertEquals("已删除", dropped.notice);
        assertEquals(listedCount - 1, dropped.tables.get("boms").size());
    }

    @Test
    void financeRefreshesAmountAndHoldsAbnormalSettlement() {
        ErpDatabase database = ErpDatabase.memory();
        ErpService service = new ErpService(database);
        assertFalse(service.options("payMethods").isEmpty());

        Map<String, Object> orderBody = new LinkedHashMap<String, Object>();
        orderBody.put("customer", "江宁物业");
        orderBody.put("contract", "CT20260115001");
        orderBody.put("material", "门把手");
        orderBody.put("qty", 2);
        orderBody.put("price", 10);
        orderBody.put("date", "2026-03-06");
        ErpBoard ordered = service.create("salesOrders", orderBody);
        assertEquals("已保存销售订单", ordered.notice);
        String orderId = String.valueOf(ordered.tables.get("salesOrders").get(0).get("id"));
        Map<String, Object> order = row(ordered, "salesOrders", orderId);

        Map<String, Object> ledger = new LinkedHashMap<String, Object>();
        ledger.put("customer", "栖霞学校");
        ledger.put("source", orderId);
        ledger.put("amount", 20);
        ledger.put("received", 9);
        ledger.put("balance", 1);
        ledger.put("due", "2026-04-01");
        ErpBoard saved = service.create("receivables", ledger);
        assertEquals("已保存应收", saved.notice);
        String billId = String.valueOf(saved.tables.get("receivables").get(0).get("id"));
        assertEquals(20, number(saved, "receivables", billId, "amount"));
        assertEquals(0, number(saved, "receivables", billId, "received"));
        assertEquals(20, number(saved, "receivables", billId, "balance"));
        assertTrue(String.valueOf(row(saved, "receivables", billId).get("code")).startsWith("YS"));
        int bills = saved.tables.get("receivables").size();

        Map<String, Object> changed = new LinkedHashMap<String, Object>(ledger);
        changed.put("amount", 30);
        ErpBoard deniedAmount = service.update("receivables", billId, changed);
        assertEquals("应收金额与订单不一致", deniedAmount.notice);
        assertEquals(20, number(deniedAmount, "receivables", billId, "amount"));

        changed.put("insist", Boolean.TRUE);
        assertEquals("请填写情况说明", service.update("receivables", billId, changed).notice);

        changed.put("explain", "验收改金额");
        ErpBoard queuedAmount = service.update("receivables", billId, changed);
        assertEquals("已提交审批", queuedAmount.notice);
        assertEquals(20, number(queuedAmount, "receivables", billId, "amount"));
        ErpBoard applied = service.act("approvals", pendingFinance(queuedAmount, "receivables"), "agree");
        assertEquals("已调整应收金额", applied.notice);
        assertEquals(30, number(applied, "receivables", billId, "amount"));
        assertEquals(30, number(applied, "receivables", billId, "balance"));

        Map<String, Object> odd = new LinkedHashMap<String, Object>();
        odd.put("customer", "栖霞学校");
        odd.put("source", orderId);
        odd.put("amount", 1);
        odd.put("due", "2026-04-01");
        ErpBoard held = service.create("receivables", odd);
        assertEquals("应收金额与订单不一致，且该订单已有应收账款", held.notice);
        assertEquals(bills, held.tables.get("receivables").size());

        Map<String, Object> collect = new LinkedHashMap<String, Object>();
        collect.put("bill", billId);
        collect.put("customer", order.get("customer"));
        collect.put("contract", order.get("contract"));
        collect.put("amount", 10);
        collect.put("method", "银行转账");
        collect.put("date", "2026-03-06");
        ErpBoard posted = service.create("collects", collect);
        assertEquals("已保存收款单", posted.notice);
        int count = posted.tables.get("collects").size();
        assertTrue(String.valueOf(posted.tables.get("collects").get(0).get("code")).startsWith("SK"));

        collect.put("customer", "栖霞学校");
        ErpBoard denied = service.create("collects", collect);
        assertEquals("客户或合同与订单不符，且该应收账款已有收款单", denied.notice);
        assertEquals(count, denied.tables.get("collects").size());

        collect.put("insist", Boolean.TRUE);
        assertEquals("请填写情况说明", service.create("collects", collect).notice);
        assertEquals(count, service.board().tables.get("collects").size());

        collect.put("explain", "验收重复收款");
        ErpBoard queued = service.create("collects", collect);
        assertEquals("已提交审批", queued.notice);
        assertEquals(count, queued.tables.get("collects").size());
        String approvalId = pendingFinance(queued, "collects");
        assertTrue(String.valueOf(row(queued, "approvals", approvalId).get("body")).contains("情况说明"));
        ErpBoard agreed = service.act("approvals", approvalId, "agree");
        assertEquals("已建立收款单", agreed.notice);
        assertEquals("已落地", String.valueOf(row(agreed, "approvals", approvalId).get("applied")));
        assertEquals(count + 1, agreed.tables.get("collects").size());
        assertTrue(String.valueOf(agreed.tables.get("collects").get(0).get("code")).startsWith("SK"));

        collect.put("customer", order.get("customer"));
        collect.remove("insist");
        assertEquals("该应收账款已有收款单", service.create("collects", collect).notice);
        collect.put("insist", Boolean.TRUE);
        collect.put("explain", "验收再次收款");
        ErpBoard review = service.create("collects", collect);
        assertEquals("已提交审批", review.notice);
        ErpBoard rejected = service.act("approvals", pendingFinance(review, "collects"), "reject");
        assertEquals("已驳回收款", rejected.notice);
        assertEquals(count + 1, rejected.tables.get("collects").size());

        Map<String, Object> another = new LinkedHashMap<String, Object>();
        another.put("customer", order.get("customer"));
        another.put("source", orderId);
        another.put("amount", 20);
        another.put("due", "2026-04-02");
        assertEquals("该订单已有应收账款", service.create("receivables", another).notice);
        another.put("insist", Boolean.TRUE);
        another.put("explain", "验收第二张应收");
        ErpBoard queuedBill = service.create("receivables", another);
        assertEquals("已提交审批", queuedBill.notice);
        assertEquals(bills, queuedBill.tables.get("receivables").size());
        ErpBoard second = service.act("approvals", pendingFinance(queuedBill, "receivables"), "agree");
        assertEquals("已建立应收账款", second.notice);
        String otherId = String.valueOf(second.tables.get("receivables").get(0).get("id"));
        assertTrue(String.valueOf(row(second, "receivables", otherId).get("code")).startsWith("YS"));
        Map<String, Object> otherCollect = new LinkedHashMap<String, Object>();
        otherCollect.put("bill", otherId);
        otherCollect.put("customer", order.get("customer"));
        otherCollect.put("contract", order.get("contract"));
        otherCollect.put("amount", 10);
        otherCollect.put("method", "银行转账");
        otherCollect.put("date", "2026-03-07");
        assertEquals("已保存收款单", service.create("collects", otherCollect).notice);
    }

    @Test
    void payrollAddsExtraToBaseAndShiftsLoadFromArchive() {
        ErpService service = new ErpService(ErpDatabase.memory());
        assertFalse(service.options("shifts").isEmpty());
        assertFalse(service.options("leaveTypes").isEmpty());
        Map<String, Object> person = named(service.board(), "employees", "李四");
        Map<String, Object> edit = new LinkedHashMap<String, Object>();
        edit.put("name", "李四");
        edit.put("phone", person.get("phone"));
        edit.put("base", 8000);
        String personId = String.valueOf(person.get("id"));
        ErpBoard edited = service.update("employees", personId, edit);
        assertEquals("已保存员工", edited.notice);
        assertEquals(8000, number(edited, "employees", personId, "base"));
        Map<String, Object> pay = new LinkedHashMap<String, Object>();
        pay.put("name", Arrays.asList("李四"));
        pay.put("month", "2099-01");
        pay.put("base", 8000);
        pay.put("attend", 22);
        pay.put("extra", -100);
        ErpBoard saved = service.create("payroll", pay);
        assertEquals("已保存工资", saved.notice);
        boolean found = false;
        for (Map<String, Object> row : saved.tables.get("payroll")) {
            if (!"2099-01".equals(String.valueOf(row.get("month")))) continue;
            assertEquals(7900, ((Number) row.get("amount")).intValue());
            found = true;
        }
        assertTrue(found);
    }

    private static String pendingFinance(ErpBoard board, String source) {
        for (Map<String, Object> item : board.tables.get("approvals")) {
            if (source.equals(String.valueOf(item.get("source"))) && "待审批".equals(String.valueOf(item.get("status")))) {
                return String.valueOf(item.get("id"));
            }
        }
        throw new AssertionError(source);
    }

    private static String labelOfMaterial(ErpBoard board, Object id) {
        if (id instanceof List) return "";
        for (Map<String, Object> item : board.tables.get("materials")) {
            if (String.valueOf(id).equals(item.get("id"))) return String.valueOf(item.get("name"));
        }
        return "";
    }

    private static Map<String, Object> queryOf(String field, String value) {
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put(field, value);
        query.put("columns", columns);
        return query;
    }

    private static Map<String, Object> stockMove(String material, String warehouse, int qty) {
        Map<String, Object> line = new LinkedHashMap<String, Object>();
        line.put("material", material);
        line.put("warehouse", warehouse);
        line.put("qty", qty);
        return line;
    }

    private static int sourced(ErpBoard board, String source, String type) {
        int count = 0;
        for (Map<String, Object> row : board.tables.get("ioBills")) {
            if (source.equals(String.valueOf(row.get("source"))) && type.equals(String.valueOf(row.get("type")))) count++;
        }
        return count;
    }

    private static Map<String, Object> supplier(String name, String phone) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", name);
        row.put("contact", "周宁");
        row.put("phone", phone);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(Map<String, Object> data) {
        return (List<Map<String, Object>>) data.get("rows");
    }

    private static String openApproval(ErpBoard board, String sourceId) {
        for (Map<String, Object> item : board.tables.get("approvals")) {
            if (sourceId.equals(String.valueOf(item.get("sourceId"))) && "待审批".equals(item.get("status"))) {
                return String.valueOf(item.get("id"));
            }
        }
        throw new AssertionError(sourceId);
    }

    private static String status(ErpBoard board, String table, String id) {
        return String.valueOf(row(board, table, id).get("status"));
    }

    private static String bomId(ErpBoard board, String product, String material) {
        for (Map<String, Object> item : board.tables.get("boms")) {
            if (product.equals(String.valueOf(item.get("product"))) && material.equals(String.valueOf(item.get("material")))) {
                return String.valueOf(item.get("id"));
            }
        }
        throw new AssertionError(product + " " + material);
    }

    private static int number(ErpBoard board, String table, String id, String key) {
        return ((Number) row(board, table, id).get(key)).intValue();
    }

    private static Map<String, Object> named(ErpBoard board, String table, String name) {
        for (Map<String, Object> item : board.tables.get(table)) {
            if (name.equals(item.get("name")) || name.equals(item.get("title"))) return item;
        }
        throw new AssertionError(name);
    }

    private static String idOf(ErpBoard board, String table, String name) {
        for (Map<String, Object> item : board.tables.get(table)) {
            if (name.equals(item.get("name"))) return String.valueOf(item.get("id"));
        }
        throw new AssertionError(name);
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
