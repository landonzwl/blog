package com.np.schedule.store;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 内置业务对象目录。已有编码不会被覆盖，企业追加的字段会保留。
 */
final class EntityCatalog {

    private static final String[] ENTITIES = new String[] {
            "customers|客户档案|销售管理|CU|启用|已保存客户",
            "contracts|销售合同|销售管理||草稿|已保存合同",
            "salesOrders|销售订单|销售管理|SO|草稿|已保存销售订单",
            "deliveries|发货单|销售管理|DN|待发货|已保存发货单",
            "suppliers|供应商|采购管理|SU|启用|已保存供应商",
            "supplierCategories|供应商类别|采购管理||启用|已保存类别",
            "purchaseContracts|采购合同|采购管理|PC|草稿|已保存采购合同",
            "purchaseOrders|采购订单|采购管理|PO|草稿|已保存采购订单",
            "receipts|收货单|采购管理|RC|待收货|已保存收货单",
            "materials|物料|库存管理|M|启用|已保存物料",
            "units|单位|库存管理||启用|已保存单位",
            "categories|分类|库存管理||启用|已保存分类",
            "stock|即时库存|库存管理||正常|已保存库存",
            "ioBills|出入库|库存管理|IO|草稿|已保存出入库单",
            "transfers|调拨|库存管理|TR|待调拨|已保存调拨单",
            "counts|盘点|库存管理|CK|盘点中|已保存盘点单",
            "boms|物料清单|生产管理||启用|已保存物料清单",
            "jobs|生产工单|生产管理|MO|生产中|已保存生产工单",
            "checks|质检单|生产管理|QC|待检验|已保存质检单",
            "receivables|应收账款|财务管理|YS|待收款|已保存应收",
            "payables|应付账款|财务管理|YF|待付款|已保存应付",
            "payMethods|收付方式|财务管理||启用|已保存收付方式",
            "collects|收款单|财务管理|SK|待核销|已保存收款单",
            "payments|付款单|财务管理|FK|待核销|已保存付款单",
            "expenses|费用报销|财务管理|EX|待审批|已保存报销单",
            "expenseTypes|报销类型|财务管理||启用|已保存类型",
            "employees|员工档案|人力资源|E|在职|已保存员工",
            "shifts|班次|人力资源||启用|已保存班次",
            "leaveTypes|请假类型|人力资源||启用|已保存请假类型",
            "attendance|考勤记录|人力资源||正常|已保存考勤",
            "leaves|请假申请|人力资源||待审批|已保存请假",
            "payroll|工资表|人力资源||待发放|已保存工资",
            "notices|公告|协同办公||已发布|已发布公告",
            "assets|固定资产|基础设置|FA|在用|已保存资产",
            "depts|部门|基础设置||启用|已保存部门",
            "warehouses|仓库|基础设置||启用|已保存仓库",
            "roles|角色权限|基础设置||启用|已保存角色",
            "approvals|审批中心|协同办公||待审批|已保存审批"
    };

    private static final String[] FIELDS = new String[] {
            "customers|name|客户名称|string|1", "customers|contact|联系人|string|1", "customers|phone|电话|string|1",
            "customers|level|等级|string|0", "customers|owner|负责人|string|0", "customers|status|状态|string|0",
            "contracts|customer|客户|string|1", "contracts|amount|金额|number|1", "contracts|start|开始日期|date|1",
            "contracts|end|结束日期|date|1", "contracts|owner|负责人|string|0", "contracts|status|状态|string|0",
            "salesOrders|customer|客户|string|1", "salesOrders|material|物料|string|1", "salesOrders|qty|数量|number|1",
            "salesOrders|price|单价|number|1", "salesOrders|amount|金额|number|0", "salesOrders|date|日期|date|1",
            "salesOrders|contract|销售合同|string|0", "salesOrders|owner|负责人|string|0", "salesOrders|status|状态|string|0",
            "deliveries|order|销售订单|string|0", "deliveries|customer|客户|string|0", "deliveries|material|物料|string|0",
            "deliveries|qty|数量|number|0", "deliveries|warehouse|仓库|string|0", "deliveries|date|日期|date|0", "deliveries|status|状态|string|0",
            "suppliers|name|供应商名称|string|1", "suppliers|contact|联系人|string|1", "suppliers|phone|电话|string|1",
            "suppliers|category|类别|string|0", "suppliers|owner|采购员|string|0", "suppliers|status|状态|string|0",
            "supplierCategories|name|类别|string|1", "supplierCategories|status|状态|string|0",
            "purchaseContracts|supplier|供应商|string|1", "purchaseContracts|amount|金额|number|1",
            "purchaseContracts|start|开始日期|date|1", "purchaseContracts|end|结束日期|date|1",
            "purchaseContracts|owner|采购员|string|0", "purchaseContracts|status|状态|string|0",
            "purchaseOrders|contract|采购合同|string|0",
            "purchaseOrders|supplier|供应商|string|1", "purchaseOrders|material|物料|string|1", "purchaseOrders|qty|数量|number|1",
            "purchaseOrders|price|单价|number|1", "purchaseOrders|amount|金额|number|0", "purchaseOrders|date|日期|date|1",
            "purchaseOrders|buyer|采购员|string|0", "purchaseOrders|status|状态|string|0",
            "receipts|order|采购订单|string|0", "receipts|supplier|供应商|string|0", "receipts|material|物料|string|0",
            "receipts|qty|数量|number|0", "receipts|warehouse|仓库|string|0", "receipts|date|日期|date|0", "receipts|status|状态|string|0",
            "materials|name|物料名称|string|1", "materials|spec|规格|string|1", "materials|unit|单位|string|0",
            "materials|category|分类|string|0", "materials|status|状态|string|0",
            "units|name|单位|string|1", "units|status|状态|string|0",
            "categories|name|分类|string|1", "categories|status|状态|string|0",
            "stock|material|物料|string|0", "stock|warehouse|仓库|string|0", "stock|qty|现存数量|number|0",
            "stock|hold|隔离数量|number|0", "stock|safety|安全库存|number|0", "stock|status|状态|string|0",
            "ioBills|type|类型|string|0", "ioBills|warehouse|仓库|string|1", "ioBills|material|物料|string|1",
            "ioBills|qty|数量|number|1", "ioBills|date|日期|date|1", "ioBills|status|状态|string|0",
            "transfers|material|物料|string|1", "transfers|materialCode|物料编码|string|0", "transfers|qty|数量|number|1", "transfers|from|调出仓库|string|1",
            "transfers|to|调入仓库|string|1", "transfers|date|日期|date|1", "transfers|status|状态|string|0",
            "counts|warehouse|仓库|string|1", "counts|material|物料|string|1", "counts|book|应有数量|number|0",
            "counts|actual|实际数量|number|0", "counts|date|日期|date|1",
            "counts|diff|差异|number|0", "counts|status|状态|string|0",
            "boms|product|产品|string|1", "boms|material|材料|string|1", "boms|qty|数量|number|1", "boms|version|版本|string|1", "boms|status|状态|string|0",
            "jobs|product|产品|string|1", "jobs|qty|数量|number|1", "jobs|owner|负责人|string|0", "jobs|workshop|车间|string|0", "jobs|start|开工日期|date|1", "jobs|finished|完工时间|string|0", "jobs|warehouse|仓库|string|0", "jobs|status|状态|string|0",
            "checks|job|生产工单|string|0", "checks|type|类型|string|0", "checks|material|物料|string|1", "checks|qty|数量|number|1", "checks|owner|负责人|string|0", "checks|date|日期|date|1", "checks|result|结果|string|0", "checks|status|状态|string|0",
            "receivables|customer|客户|string|0", "receivables|source|来源单据|string|0", "receivables|amount|金额|number|0", "receivables|received|已收|number|0",
            "receivables|balance|余额|number|0", "receivables|due|到期日|date|0", "receivables|status|状态|string|0",
            "payables|supplier|供应商|string|0", "payables|source|来源单据|string|0", "payables|amount|金额|number|0", "payables|paid|已付|number|0",
            "payables|balance|余额|number|0", "payables|due|到期日|date|0", "payables|status|状态|string|0",
            "payMethods|name|方式|string|1", "payMethods|status|状态|string|0",
            "collects|bill|应收账款|string|1", "collects|contract|销售合同|string|0", "collects|customer|客户|string|1", "collects|amount|金额|number|1", "collects|method|方式|string|0", "collects|date|日期|date|1", "collects|status|状态|string|0",
            "payments|bill|应付账款|string|1", "payments|contract|采购合同|string|0", "payments|supplier|供应商|string|1", "payments|amount|金额|number|1", "payments|method|方式|string|0", "payments|date|日期|date|1", "payments|status|状态|string|0",
            "expenses|type|类型|string|0", "expenses|applicant|申请人|string|0", "expenses|amount|金额|number|1",
            "expenses|date|日期|date|1", "expenses|status|状态|string|0",
            "expenseTypes|name|类型|string|1", "expenseTypes|status|状态|string|0",
            "employees|name|姓名|string|1", "employees|phone|电话|string|1", "employees|dept|部门|string|0",
            "employees|post|岗位|string|0", "employees|skill|技能|string|0", "employees|base|基本工资|number|0", "employees|status|状态|string|0",
            "shifts|name|班次|string|1", "shifts|status|状态|string|0",
            "leaveTypes|name|类型|string|1", "leaveTypes|status|状态|string|0",
            "attendance|name|姓名|string|0", "attendance|dept|部门|string|0", "attendance|date|日期|date|1",
            "attendance|shift|班次|string|0", "attendance|clockIn|上班|string|0", "attendance|clockOut|下班|string|0", "attendance|status|状态|string|0",
            "leaves|name|姓名|string|0", "leaves|type|假别|string|0", "leaves|start|开始|datetime|1",
            "leaves|end|结束|datetime|1", "leaves|days|天数|number|1", "leaves|status|状态|string|0",
            "payroll|name|姓名|string|0", "payroll|dept|部门|string|0", "payroll|month|月份|string|1", "payroll|base|基本工资|number|1",
            "payroll|attend|出勤天数|number|1", "payroll|extra|其他工资|number|0", "payroll|amount|实际发放|number|0", "payroll|status|状态|string|0",
            "notices|title|标题|string|1", "notices|body|正文|text|0", "notices|dept|部门|string|0", "notices|date|日期|date|1", "notices|status|状态|string|0",
            "assets|name|名称|string|1", "assets|dept|部门|string|0", "assets|keeper|保管人|string|0", "assets|date|启用日期|date|1", "assets|status|状态|string|0",
            "depts|name|部门|string|1", "depts|parent|上级|string|0", "depts|manager|负责人|string|0", "depts|people|人数|number|0", "depts|status|状态|string|0",
            "warehouses|name|仓库|string|1", "warehouses|dept|部门|string|0", "warehouses|keeper|保管人|string|0", "warehouses|status|状态|string|0",
            "roles|name|角色|string|0", "roles|note|说明|string|0",
            "approvals|type|板块|string|0", "approvals|title|标题|string|1", "approvals|applicant|申请人|string|0",
            "approvals|date|日期|date|0", "approvals|status|状态|string|0", "approvals|source|板块编码|string|1", "approvals|sourceId|来源单据|string|0",
            "approvals|body|申请说明|text|0", "approvals|payload|变更内容|text|0", "approvals|opinion|审批意见|text|0",
            "approvals|decided|审批时间|string|0", "approvals|applied|落地状态|string|0"
    };

    private static final String[] ACTIONS = new String[] {
            "salesOrders|audit|审核|草稿|", "salesOrders|reject|驳回|草稿|", "salesOrders|pushDelivery|下推发货|已审核|",
            "purchaseOrders|audit|审核|草稿|", "purchaseOrders|reject|驳回|草稿|", "purchaseOrders|pushReceipt|下推收货|已审核|",
            "deliveries|ship|确认发货|待发货|", "deliveries|unship|撤回发货|已发货|",
            "receipts|receive|确认收货|待收货|", "receipts|unreceive|撤回收货|已收货|",
            "checks|pass|合格|待检验|", "checks|fail|不合格|待检验|", "checks|undo|撤回检验|已完成|",
            "leaves|agree|同意|待审批|", "leaves|reject|驳回|待审批|",
            "expenses|agree|同意|待审批|", "expenses|reject|驳回|待审批|",
            "approvals|agree|同意|待审批|", "approvals|reject|驳回|待审批|"
    };

    private EntityCatalog() {
    }

    private static void renameBomField(JdbcTemplate jdbc, String from, String to, String label) {
        Integer target = jdbc.queryForObject("SELECT COUNT(*) FROM np_meta_field WHERE entity_code = 'boms' AND field_key = ?", Integer.class, to);
        Integer source = jdbc.queryForObject("SELECT COUNT(*) FROM np_meta_field WHERE entity_code = 'boms' AND field_key = ?", Integer.class, from);
        if (source != null && source.intValue() > 0 && (target == null || target.intValue() == 0)) {
            jdbc.update("UPDATE np_meta_field SET field_key = ?, label = ? WHERE entity_code = 'boms' AND field_key = ?", to, label, from);
        } else if (target != null && target.intValue() > 0) {
            jdbc.update("UPDATE np_meta_field SET label = ? WHERE entity_code = 'boms' AND field_key = ?", label, to);
        }
        List<String> ids = jdbc.queryForList("SELECT id FROM np_record WHERE entity_code = 'boms'", String.class);
        for (String id : ids) {
            Integer hasNew = jdbc.queryForObject("SELECT COUNT(*) FROM np_cell WHERE record_id = ? AND field_key = ?", Integer.class, id, to);
            Integer hasOld = jdbc.queryForObject("SELECT COUNT(*) FROM np_cell WHERE record_id = ? AND field_key = ?", Integer.class, id, from);
            if (hasOld != null && hasOld.intValue() > 0 && (hasNew == null || hasNew.intValue() == 0)) {
                jdbc.update("UPDATE np_cell SET field_key = ? WHERE record_id = ? AND field_key = ?", to, id, from);
            }
        }
    }

    private static boolean retired(JdbcTemplate jdbc, String code, String key) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM np_meta_retired WHERE entity_code = ? AND field_key = ?", Integer.class, code, key);
        return count != null && count.intValue() > 0;
    }

    static void renameBomFields(JdbcTemplate jdbc) {
        renameBomField(jdbc, "parent", "product", "产品");
        renameBomField(jdbc, "child", "material", "材料");
        jdbc.update("UPDATE np_meta_field SET label = '数量' WHERE entity_code = 'boms' AND field_key = 'qty'");
    }

    static void ensure(JdbcTemplate jdbc) {
        renameBomFields(jdbc);
        int sort = 0;
        for (String line : ENTITIES) {
            String[] part = line.split("\\|", -1);
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM np_meta_entity WHERE code = ?", Integer.class, part[0]);
            if (exists != null && exists.intValue() > 0) continue;
            jdbc.update("INSERT INTO np_meta_entity (code, title, module_name, code_prefix, create_status, saved_text, builtin) VALUES (?, ?, ?, ?, ?, ?, 1)",
                    part[0], part[1], part[2], part[3], part[4], part[5]);
        }
        for (String line : FIELDS) {
            String[] part = line.split("\\|", -1);
            sort++;
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM np_meta_field WHERE entity_code = ? AND field_key = ?", Integer.class, part[0], part[1]);
            if (exists != null && exists.intValue() > 0) continue;
            if (retired(jdbc, part[0], part[1])) continue;
            jdbc.update("INSERT INTO np_meta_field (entity_code, field_key, label, data_type, required_flag, sort_no, options_text) VALUES (?, ?, ?, ?, ?, ?, '')",
                    part[0], part[1], part[2], part[3], Integer.valueOf(part[4]), sort);
        }
        jdbc.update("UPDATE np_meta_field SET label = '板块' WHERE entity_code = 'approvals' AND field_key = 'type'");
        jdbc.update("UPDATE np_meta_field SET required_flag = 1, label = '标题' WHERE entity_code = 'approvals' AND field_key = 'title'");
        jdbc.update("UPDATE np_meta_field SET required_flag = 1, label = '板块编码' WHERE entity_code = 'approvals' AND field_key = 'source'");
        jdbc.update("UPDATE np_meta_field SET label = '来源单据' WHERE entity_code = 'approvals' AND field_key = 'sourceId'");
        jdbc.update("UPDATE np_meta_field SET label = '实际发放' WHERE entity_code = 'payroll' AND field_key = 'amount'");
        for (String line : ACTIONS) {
            String[] part = line.split("\\|", -1);
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM np_meta_action WHERE entity_code = ? AND action_key = ?", Integer.class, part[0], part[1]);
            if (exists != null && exists.intValue() > 0) continue;
            jdbc.update("INSERT INTO np_meta_action (entity_code, action_key, label, when_status, target_status) VALUES (?, ?, ?, ?, ?)",
                    part[0], part[1], part[2], part[3], part[4]);
        }
        for (String line : ENTITIES) {
            String code = line.split("\\|", -1)[0];
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM np_meta_field WHERE entity_code = ? AND field_key = 'remark'", Integer.class, code);
            if (exists != null && exists.intValue() > 0) continue;
            if (retired(jdbc, code, "remark")) continue;
            jdbc.update("INSERT INTO np_meta_field (entity_code, field_key, label, data_type, required_flag, sort_no, options_text) VALUES (?, 'remark', '备注', 'text', 0, 900, '')", code);
        }
        Integer org = jdbc.queryForObject("SELECT COUNT(*) FROM np_org", Integer.class);
        if (org == null || org == 0) {
            jdbc.update("INSERT INTO np_org (id, name, sites) VALUES (1, ?, ?)", "企业管理系统", "东区门店 / 西区门店");
        }
    }
}
