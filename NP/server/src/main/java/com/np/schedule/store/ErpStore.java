package com.np.schedule.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ErpStore {

    public static final String[] TABLES = new String[] {
            "customers", "contracts", "salesOrders", "deliveries",
            "suppliers", "supplierCategories", "purchaseContracts", "purchaseOrders", "receipts",
            "materials", "units", "categories", "stock", "ioBills", "transfers", "counts",
            "boms", "jobs", "checks",
            "receivables", "payables", "payMethods", "collects", "payments", "expenses",
            "employees", "shifts", "leaveTypes", "attendance", "leaves", "payroll", "expenseTypes",
            "notices", "assets", "depts", "warehouses", "roles", "approvals"
    };

    public int seq;
    public int version;
    public String notice = "";
    public String orgName = "企业管理系统";
    public String orgSites = "东区门店 / 西区门店";
    public Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<String, List<Map<String, Object>>>();
}
