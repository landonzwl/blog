package com.np.schedule.service;

import com.np.schedule.common.BusinessException;
import com.np.schedule.common.DataConflict;
import com.np.schedule.config.ActorContext;
import com.np.schedule.model.ErpBoard;
import com.np.schedule.model.StaffMember;
import com.np.schedule.store.ErpDatabase;
import com.np.schedule.store.RefCatalog;
import com.np.schedule.store.ScheduleDatabase;
import com.np.schedule.store.ErpStore;
import com.np.schedule.store.MetaSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ErpService {

    private static final Set<String> NUMBERS = new HashSet<String>(Arrays.asList(
            "qty", "price", "amount", "days", "base", "attend", "extra", "people", "diff", "safety", "received", "paid", "balance"));

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ErpDatabase database;
    private final ScheduleService schedule;
    private final AuthService auth;
    private final ErpStore store = new ErpStore();
    private boolean failed;
    private Map<String, Object> pendingEmployee;
    private String pendingLeaveId = "";
    private String pendingLeaveStaff = "";
    private String pendingLeaveName = "";
    private String pendingLeaveStart = "";
    private String pendingLeaveEnd = "";
    private boolean pendingPlace;
    private boolean pendingClear;
    private Map<String, String> stockBefore;

    public ErpService(ErpDatabase database) {
        this(database, null, null);
    }

    @Autowired
    public ErpService(ErpDatabase database, ScheduleService schedule, AuthService auth) {
        this.database = database;
        this.schedule = schedule;
        this.auth = auth;
        database.prepare(store);
        boolean dirty = repairMaterialBooks();
        if (fillLedgerCodes()) dirty = true;
        if (linkSettlementBills()) dirty = true;
        if (dirty) database.save(store);
        linkPeople();
    }

    public synchronized ErpBoard board() {
        database.loadInto(store);
        rememberStock();
        boolean dirty = dropProjectedApprovals();
        if (clearPlaceholderParents()) dirty = true;
        if (fillLedgerCodes()) dirty = true;
        if (linkSettlementBills()) dirty = true;
        if (dirty) persist();
        mirrorDepartments();
        return snapshot();
    }

    public synchronized Map<String, Object> query(String table, Map<String, Object> body) {
        database.loadInto(store);
        Map<String, String> columns = columnFilters(body);
        List<Map<String, Object>> rows = store.tables.get(table);
        List<Map<String, Object>> hits = new ArrayList<Map<String, Object>>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> view = row;
                if ("depts".equals(table)) {
                    view = new LinkedHashMap<String, Object>(row);
                    applyDeptPeople(view);
                }
                if (rowMatches(table, view, columns)) hits.add(view);
            }
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", hits);
        return data;
    }

    public synchronized List<Map<String, String>> options(String table) {
        database.loadInto(store);
        List<Map<String, Object>> rows = store.tables.get(table);
        List<Map<String, String>> items = new ArrayList<Map<String, String>>();
        if (rows == null) return items;
        for (Map<String, Object> row : rows) {
            String id = text(row, "id");
            if (id.isEmpty()) continue;
            Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("id", id);
            item.put("label", labelOf(table, id));
            String status = text(row, "status");
            if (!status.isEmpty()) item.put("status", status);
            items.add(item);
        }
        return items;
    }

    public synchronized Map<String, String> quickOption(String table, String label) {
        String name = label == null ? "" : label.trim();
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("id", "");
        result.put("label", "");
        if (name.isEmpty()) {
            result.put("notice", "请填写名称");
            return result;
        }
        if (!inlineVocab(table)) {
            result.put("notice", "请到对应档案新建");
            return result;
        }
        Spec spec = specOf(table);
        if (spec == null) {
            result.put("notice", "当前资料不能新建");
            return result;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        boolean named = false;
        for (String[] field : spec.required) {
            if (refTarget(table, field[0]) != null) {
                result.put("notice", "请先选择已有" + field[1]);
                return result;
            }
            if (numeric(table, field[0])) body.put(field[0], Integer.valueOf(1));
            else if (dateKey(field[0])) body.put(field[0], today());
            else if (!named) {
                body.put(field[0], name);
                named = true;
            } else body.put(field[0], "待补充");
        }
        if (!named) body.put("name", name);
        ErpBoard board = create(table, body);
        result.put("notice", board.notice == null ? "" : board.notice);
        if (board.notice == null || !board.notice.startsWith("已")) return result;
        List<Map<String, Object>> rows = store.tables.get(table);
        if (rows == null || rows.isEmpty()) return result;
        String id = text(rows.get(0), "id");
        result.put("id", id);
        result.put("label", labelOf(table, id));
        return result;
    }

    private static boolean inlineVocab(String table) {
        return "units".equals(table) || "categories".equals(table) || "shifts".equals(table)
                || "leaveTypes".equals(table) || "payMethods".equals(table)
                || "supplierCategories".equals(table) || "expenseTypes".equals(table);
    }

    private String refTarget(String table, String field) {
        for (String[] ref : RefCatalog.FIELDS) {
            if (ref[0].equals(table) && ref[1].equals(field)) return ref[2];
        }
        return null;
    }

    private static boolean dateKey(String key) {
        return "date".equals(key) || "start".equals(key) || "end".equals(key) || "month".equals(key) || "due".equals(key);
    }

    private Object postIds(Object value) {
        List<String> ids = new ArrayList<String>();
        for (String token : textItems(value, 64)) {
            String id = database.postId(token);
            if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
        }
        if (ids.isEmpty()) return "";
        if (ids.size() == 1) return ids.get(0);
        return ids;
    }

    public synchronized ErpBoard create(String table, Map<String, Object> body) {
        database.loadInto(store);
        rememberStock();
        failed = false;
        resetSideEffects();
        Spec spec = specOf(table);
        if (spec == null) {
            store.notice = "当前资料不能新建";
            return finish();
        }
        Map<String, Object> incoming = body == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(body);
        boolean insist = insisted(incoming.remove("insist"));
        String explain = one(incoming.remove("explain"));
        if ("stock".equals(table)) {
            incoming.remove("qty");
            incoming.remove("hold");
            incoming.remove("status");
        }
        if ("receivables".equals(table) || "payables".equals(table)) {
            incoming.remove("balance");
            incoming.remove("received");
            incoming.remove("paid");
        }
        for (String[] field : spec.required) {
            Object value = incoming.get(field[0]);
            if (numeric(table, field[0])) {
                if (!(numValue(value) > 0)) {
                    store.notice = "请填写" + field[1];
                    return finish();
                }
            } else if (blank(value)) {
                store.notice = "请填写" + field[1];
                return finish();
            }
        }
        if ("stock".equals(table)) return openStock(incoming);
        if ("boms".equals(table)) return openBoms(incoming);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", nextId());
        if (spec.defaults != null) {
            row.putAll(spec.defaults);
        }
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            if ("id".equals(entry.getKey()) || "code".equals(entry.getKey()) || "origin".equals(entry.getKey())) {
                continue;
            }
            row.put(entry.getKey(), typed(table, entry.getKey(), entry.getValue()));
        }
        if (spec.prefix != null) {
            row.put("code", nextCode(spec.prefix));
        }
        if (text(row, "status").isEmpty()) {
            row.put("status", spec.status);
        }
        if (spec.derive) {
            putNum(row, "amount", num(row, "qty") * num(row, "price"));
        }
        if (spec.derivePay) applyPay(row);
        if ("depts".equals(table)) {
            row.remove("people");
            String parentError = deptParentError(row, "");
            if (parentError != null) {
                deny(parentError);
                return finish();
            }
        }
        if ("notices".equals(table)) {
            String deptError = noticeDeptError(row);
            if (deptError != null) {
                deny(deptError);
                return finish();
            }
        }
        if ("employees".equals(table)) {
            row.put("skill", employeeSkills(row.get("skill")));
            row.put("post", postIds(row.get("post")));
        }
        bindRefs(table, row);
        if ("stock".equals(table)) {
            if (!row.containsKey("qty")) putNum(row, "qty", 0);
            if (!row.containsKey("hold")) putNum(row, "hold", 0);
            touchStock(row);
        }
        if ("transfers".equals(table)) {
            String error = lockTransfer(row);
            if (error != null) {
                deny(error);
                return finish();
            }
        }
        boolean countSettled = false;
        if ("counts".equals(table)) {
            int settled = prepareCount(row);
            if (settled < 0) return finish();
            countSettled = settled > 0;
        }
        if ("approvals".equals(table)) {
            String error = prepareApproval(row, true);
            if (error != null) {
                deny(error);
                return finish();
            }
        }
        if ("receivables".equals(table) || "payables".equals(table)) {
            applyLedger(table, row);
            if (failed) return finish();
            String ledger = ledgerGate(table, row, true, insist, explain);
            if ("APPROVE".equals(ledger)) {
                String error = openLedgerApproval(table, row, "", num(row, "amount"), explain);
                if (error != null) deny(error);
                return finish();
            }
            if (ledger != null) {
                deny(ledger);
                return finish();
            }
            if (text(row, "code").isEmpty()) row.put("code", nextCode("receivables".equals(table) ? "YS" : "YF"));
        }
        if ("collects".equals(table) || "payments".equals(table)) {
            String gate = settlementGate(table, row, insist, explain);
            if ("APPROVE".equals(gate)) {
                String error = openSettlementApproval(table, row);
                if (error != null) deny(error);
                return finish();
            }
            if (gate != null) {
                deny(gate);
                return finish();
            }
        }
        store.tables.get(table).add(0, row);
        if ("counts".equals(table)) {
            String error = openCountApproval(row);
            if (error != null) {
                deny(error);
                return finish();
            }
        }
        if ("leaves".equals(table)) {
            String queued = queueLeave(row);
            if (queued != null && !"NEW".equals(queued)) {
                deny(queued);
                return finish();
            }
            store.notice = "已提交审批";
        }
        if (!countSettled && !"leaves".equals(table)) store.notice = spec.saved;
        if ("employees".equals(table)) {
            pendingEmployee = row;
        }
        return finish();
    }

    public synchronized ErpBoard update(String table, String id, Map<String, Object> body) {
        database.loadInto(store);
        rememberStock();
        failed = false;
        resetSideEffects();
        Spec spec = specOf(table);
        if (spec == null) {
            deny("当前资料不能修改");
            return finish();
        }
        Map<String, Object> row = byId(table, id);
        if (row == null) {
            deny("单据不存在");
            return finish();
        }
        if (!canEdit(table, text(row, "status"))) {
            deny("当前状态不能修改");
            return finish();
        }
        Map<String, Object> incoming = body == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(body);
        boolean insist = insisted(incoming.remove("insist"));
        String explain = one(incoming.remove("explain"));
        double keptAmount = num(row, "amount");
        if ("stock".equals(table)) {
            incoming.remove("qty");
            incoming.remove("hold");
            incoming.remove("status");
        }
        if ("receivables".equals(table) || "payables".equals(table)) {
            incoming.remove("balance");
            incoming.remove("received");
            incoming.remove("paid");
        }
        for (String[] field : spec.required) {
            Object value = incoming.get(field[0]);
            if (numeric(table, field[0])) {
                if (!(numValue(value) > 0)) {
                    deny("请填写" + field[1]);
                    return finish();
                }
            } else if (blank(value)) {
                deny("请填写" + field[1]);
                return finish();
            }
        }
        String previous = text(row, "status");
        String previousName = text(row, "name");
        if (spec.defaults != null) {
            for (Map.Entry<String, String> entry : spec.defaults.entrySet()) {
                if (text(row, entry.getKey()).isEmpty()) {
                    row.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            if ("id".equals(entry.getKey()) || "code".equals(entry.getKey()) || "status".equals(entry.getKey()) || "origin".equals(entry.getKey())) {
                continue;
            }
            row.put(entry.getKey(), typed(table, entry.getKey(), entry.getValue()));
        }
        if ("assets".equals(table) && incoming.get("status") != null) {
            row.put("status", clean("status", incoming.get("status")));
        }
        if (spec.derive) {
            putNum(row, "amount", num(row, "qty") * num(row, "price"));
        }
        if (spec.derivePay) applyPay(row);
        if ("depts".equals(table)) {
            row.remove("people");
            String parentError = deptParentError(row, previousName);
            if (parentError != null) {
                deny(parentError);
                return finish();
            }
        }
        if ("notices".equals(table)) {
            String deptError = noticeDeptError(row);
            if (deptError != null) {
                deny(deptError);
                return finish();
            }
        }
        if ("employees".equals(table)) {
            row.put("skill", employeeSkills(row.get("skill")));
            row.put("post", postIds(row.get("post")));
        }
        bindRefs(table, row);
        if ("receivables".equals(table) || "payables".equals(table)) {
            applyLedger(table, row);
            if (failed) return finish();
            String ledger = ledgerGate(table, row, false, insist, explain);
            if ("APPROVE".equals(ledger) && !sameQty(num(row, "amount"), keptAmount)) {
                double requested = num(row, "amount");
                String paidKey = "receivables".equals(table) ? "received" : "paid";
                if (num(row, paidKey) - requested > 0.0001d) {
                    deny("receivables".equals(table) ? "已收不能大于应收" : "已付不能大于应付");
                    return finish();
                }
                putNum(row, "amount", keptAmount);
                applyLedger(table, row);
                if (failed) return finish();
                String error = openLedgerApproval(table, row, text(row, "id"), requested, explain);
                if (error != null) deny(error);
                return finish();
            }
            if (ledger != null && !"APPROVE".equals(ledger)) {
                deny(ledger);
                return finish();
            }
            if (text(row, "code").isEmpty()) row.put("code", nextCode("receivables".equals(table) ? "YS" : "YF"));
        }
        if ("collects".equals(table) || "payments".equals(table)) {
            String gate = settlementGate(table, row, false, "");
            if (gate != null) {
                deny(gate);
                return finish();
            }
        }
        if ("stock".equals(table)) {
            String error = pinStoredPair(row, "material", "materials", "warehouse", "warehouses",
                    "即时库存一行只能对应一个物料和一个仓库", "该物料在该仓库已有库存，请直接修改");
            if (error != null) {
                deny(error);
                return finish();
            }
            String code = materialCode(text(row, "material"));
            if (!code.isEmpty()) row.put("code", code);
            touchStock(row);
        }
        if ("boms".equals(table)) {
            String error = pinStoredPair(row, "product", "materials", "material", "materials",
                    "物料清单一行只能对应一个产品和一个材料", "该产品的这个材料已有清单");
            if (error != null) {
                deny(error);
                return finish();
            }
            if (text(row, "product").equals(text(row, "material"))) {
                deny("产品和材料不能相同");
                return finish();
            }
        }
        if ("transfers".equals(table)) {
            String error = lockTransfer(row);
            if (error != null) {
                deny(error);
                return finish();
            }
        }
        boolean countSettled = false;
        if ("counts".equals(table)) {
            int settled = prepareCount(row);
            if (settled < 0) return finish();
            countSettled = settled > 0;
            String error = openCountApproval(row);
            if (error != null) {
                deny(error);
                return finish();
            }
        }
        if ("approvals".equals(table)) {
            String error = prepareApproval(row, false);
            if (error != null) {
                deny(error);
                return finish();
            }
        }
        if ("已驳回".equals(previous) && ("salesOrders".equals(table) || "purchaseOrders".equals(table))) {
            row.put("status", "草稿");
            ok("已保存并退回草稿");
        } else if ("已驳回".equals(previous) && "leaves".equals(table)) {
            row.put("status", "待审批");
            String queued = queueLeave(row);
            if (queued != null && !"NEW".equals(queued)) {
                deny(queued);
                return finish();
            }
            ok("已提交审批");
        } else if ("已驳回".equals(previous) && "expenses".equals(table)) {
            row.put("status", "待审批");
            ok("已保存并退回待审批");
        } else if (!countSettled) {
            ok(spec.saved);
        }
        if ("employees".equals(table)) {
            pendingEmployee = row;
        }
        return finish();
    }

    public synchronized ErpBoard act(String table, String id, String action) {
        database.loadInto(store);
        rememberStock();
        failed = false;
        resetSideEffects();
        Map<String, Object> row = byId(table, id);
        if (row == null) {
            store.notice = "单据不存在";
            return finish();
        }
        dispatch(table, action, row);
        return finish();
    }

    private void dispatch(String table, String action, Map<String, Object> row) {
        if ("remove".equals(action)) {
            if ("customers".equals(table)) {
                String used = customerUsed(row);
                if (used != null) {
                    deny(used);
                    return;
                }
                store.tables.get(table).remove(row);
                ok("已删除客户");
                return;
            }
            if (database.customEntity(table)) {
                store.tables.get(table).remove(row);
                ok("已删除");
                return;
            }
            if ("boms".equals(table)) {
                store.tables.get(table).remove(row);
                ok("已删除");
                return;
            }
            if ("jobs".equals(table)) {
                if (!"生产中".equals(text(row, "status"))) {
                    deny("只有生产中的工单可以删除");
                    return;
                }
                List<Map<String, Object>> checks = store.tables.get("checks");
                if (checks != null) {
                    for (Map<String, Object> item : checks) {
                        if (holds(item.get("job"), text(row, "id"), text(row, "code"))) {
                            deny("已有质检单，不能删除");
                            return;
                        }
                    }
                }
                store.tables.get(table).remove(row);
                ok("已删除");
                return;
            }
            if (!("salesOrders".equals(table) || "purchaseOrders".equals(table))) {
                deny("当前单据不能删除");
                return;
            }
            if (!"草稿".equals(text(row, "status"))) {
                deny("只有草稿可以删除");
                return;
            }
            store.tables.get(table).remove(row);
            ok("已删除");
            return;
        }
        if ("salesOrders".equals(table) && "audit".equals(action)) {
            auditDraft(row, "已审核销售订单");
            return;
        }
        if ("salesOrders".equals(table) && "reject".equals(action)) {
            rejectDraft(row, "已驳回销售订单");
            return;
        }
        if ("salesOrders".equals(table) && "undoAudit".equals(action)) {
            undoAudit(row, "deliveries");
            return;
        }
        if ("salesOrders".equals(table) && "undoReject".equals(action)) {
            undoReject(row);
            return;
        }
        if ("salesOrders".equals(table) && "undoPush".equals(action)) {
            undoPush(row, "deliveries", "待发货");
            return;
        }
        if ("salesOrders".equals(table) && "pushDelivery".equals(action)) {
            pushDelivery(row);
            return;
        }
        if ("deliveries".equals(table) && "ship".equals(action)) {
            ship(row);
            return;
        }
        if ("deliveries".equals(table) && "unship".equals(action)) {
            unship(row);
            return;
        }
        if ("purchaseOrders".equals(table) && "audit".equals(action)) {
            auditDraft(row, "已审核采购订单");
            return;
        }
        if ("purchaseOrders".equals(table) && "reject".equals(action)) {
            rejectDraft(row, "已驳回采购订单");
            return;
        }
        if ("purchaseOrders".equals(table) && "undoAudit".equals(action)) {
            undoAudit(row, "receipts");
            return;
        }
        if ("purchaseOrders".equals(table) && "undoReject".equals(action)) {
            undoReject(row);
            return;
        }
        if ("purchaseOrders".equals(table) && "undoPush".equals(action)) {
            undoPush(row, "receipts", "待收货");
            return;
        }
        if ("purchaseOrders".equals(table) && "pushReceipt".equals(action)) {
            pushReceipt(row);
            return;
        }
        if ("receipts".equals(table) && "receive".equals(action)) {
            receive(row);
            return;
        }
        if ("receipts".equals(table) && "unreceive".equals(action)) {
            unreceive(row);
            return;
        }
        if ("transfers".equals(table) && "move".equals(action)) {
            move(row);
            return;
        }
        if ("transfers".equals(table) && "unmove".equals(action)) {
            unmove(row);
            return;
        }
        if ("ioBills".equals(table) && "post".equals(action)) {
            postBill(row);
            return;
        }
        if ("ioBills".equals(table) && "unpost".equals(action)) {
            unpost(row);
            return;
        }
        if ("checks".equals(table) && "pass".equals(action)) {
            if (!"待检验".equals(text(row, "status"))) {
                deny("只有待检验的单据可以判定");
                return;
            }
            row.put("result", "合格");
            row.put("status", "已完成");
            ok("已判定合格");
            return;
        }
        if ("checks".equals(table) && "fail".equals(action)) {
            if (!"待检验".equals(text(row, "status"))) {
                deny("只有待检验的单据可以判定");
                return;
            }
            Map<String, Object> stock = stockToHold(text(row, "material"), num(row, "qty"));
            if (stock == null) {
                deny("库存不足，不能隔离");
                return;
            }
            double qty = num(row, "qty");
            putNum(stock, "qty", num(stock, "qty") - qty);
            putNum(stock, "hold", num(stock, "hold") + qty);
            touchStock(stock);
            row.put("result", "不合格");
            row.put("status", "已完成");
            putNum(row, "heldQty", qty);
            row.put("heldStock", text(stock, "id"));
            ok("已判定不合格，不合格数量已隔离");
            return;
        }
        if ("checks".equals(table) && "undo".equals(action)) {
            if (!"已完成".equals(text(row, "status"))) {
                deny("只有已完成的质检可以撤回");
                return;
            }
            double held = num(row, "heldQty");
            if (held > 0) {
                Map<String, Object> stock = byId("stock", text(row, "heldStock"));
                if (stock == null || num(stock, "hold") < held) {
                    deny("隔离数量不足，不能撤回");
                    return;
                }
                putNum(stock, "hold", num(stock, "hold") - held);
                putNum(stock, "qty", num(stock, "qty") + held);
                touchStock(stock);
                row.remove("heldQty");
                row.remove("heldStock");
            }
            row.put("result", "待检验");
            row.put("status", "待检验");
            ok("已撤回检验");
            return;
        }
        if ("collects".equals(table) && "settle".equals(action)) {
            settle("receivables", "customer", row, "received", "待收款", "已核销收款");
            return;
        }
        if ("collects".equals(table) && "unsettle".equals(action)) {
            unsettle("receivables", "customer", row, "received", "待收款");
            return;
        }
        if ("payments".equals(table) && "settle".equals(action)) {
            settle("payables", "supplier", row, "paid", "待付款", "已核销付款");
            return;
        }
        if ("payments".equals(table) && "unsettle".equals(action)) {
            unsettle("payables", "supplier", row, "paid", "待付款");
            return;
        }
        if ("leaves".equals(table) && "agree".equals(action)) {
            setStatus(row, "待审批", "已同意", "已同意请假");
            if (!failed && schedule != null) {
                pendingPlace = true;
                pendingLeaveId = text(row, "id");
                String[] person = leaveStaff(row);
                pendingLeaveStaff = person[0];
                pendingLeaveName = person[1];
                pendingLeaveStart = text(row, "start");
                pendingLeaveEnd = text(row, "end");
            }
            return;
        }
        if ("leaves".equals(table) && "reject".equals(action)) {
            setStatus(row, "待审批", "已驳回", "已驳回请假");
            return;
        }
        if ("leaves".equals(table) && "undo".equals(action)) {
            boolean agreed = "已同意".equals(text(row, "status"));
            undoDecision(row, "已同意", "已驳回");
            if (!failed) {
                String queued = queueLeave(row);
                if (queued != null && !"NEW".equals(queued)) {
                    deny(queued);
                    return;
                }
                ok("已撤回审批");
            }
            if (!failed && agreed && schedule != null) {
                pendingClear = true;
                pendingLeaveId = text(row, "id");
            }
            return;
        }
        if ("expenses".equals(table) && "agree".equals(action)) {
            setStatus(row, "待审批", "已通过", "已通过报销");
            return;
        }
        if ("expenses".equals(table) && "reject".equals(action)) {
            setStatus(row, "待审批", "已驳回", "已驳回报销");
            return;
        }
        if ("expenses".equals(table) && "undo".equals(action)) {
            undoDecision(row, "已通过", "已驳回");
            return;
        }
        if ("payroll".equals(table) && "pay".equals(action)) {
            setStatus(row, "待发放", "已发放", "已发放工资");
            return;
        }
        if ("payroll".equals(table) && "unpay".equals(action)) {
            setStatus(row, "已发放", "待发放", "已撤回发放");
            return;
        }
        if ("contracts".equals(table) && "audit".equals(action)) {
            setStatus(row, "草稿", "执行中", "已审核合同");
            return;
        }
        if ("contracts".equals(table) && "undoAudit".equals(action)) {
            setStatus(row, "执行中", "草稿", "已撤回审核");
            return;
        }
        if ("purchaseContracts".equals(table) && "audit".equals(action)) {
            setStatus(row, "草稿", "执行中", "已审核合同");
            return;
        }
        if ("purchaseContracts".equals(table) && "undoAudit".equals(action)) {
            setStatus(row, "执行中", "草稿", "已撤回审核");
            return;
        }
        if ("approvals".equals(table) && ("agree".equals(action) || "reject".equals(action))) {
            decideApproval(row, "agree".equals(action));
            return;
        }
        if ("counts".equals(table) && "待审批".equals(text(row, "status"))
                && ("finish".equals(action) || "agree".equals(action) || "reject".equals(action))) {
            deny("差异请在审批中心处理");
            return;
        }
        if ("counts".equals(table) && ("finish".equals(action) || "agree".equals(action))) {
            finishCount(row);
            return;
        }
        if ("counts".equals(table) && "reject".equals(action)) {
            rejectCount(row);
            return;
        }
        if ("counts".equals(table) && "undo".equals(action)) {
            undoCount(row);
            return;
        }
        if ("jobs".equals(table) && "finish".equals(action)) {
            finishJob(row);
            return;
        }
        if ("jobs".equals(table) && "undo".equals(action)) {
            undoFinish(row);
            return;
        }
        if ("jobs".equals(table) && "pushCheck".equals(action)) {
            pushCheck(row);
            return;
        }
        if ("jobs".equals(table) && "undoPush".equals(action)) {
            undoPushCheck(row);
            return;
        }
        if (applyMetaAction(table, action, row)) return;
        deny("当前单据不能执行该操作");
    }

    private boolean applyMetaAction(String table, String action, Map<String, Object> row) {
        String target = database.actionTarget(table, action);
        if (target == null || target.isEmpty()) return false;
        String when = database.actionWhen(table, action);
        if (!when.isEmpty() && !when.equals(text(row, "status"))) {
            deny("当前状态不能执行该操作");
            return true;
        }
        row.put("status", target);
        ok("已" + database.actionLabel(table, action));
        return true;
    }

    public synchronized String defineEntity(Map<String, Object> body) {
        if (body == null) return "请填写业务对象";
        Object raw = body.get("fields");
        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof Map) {
                    Map<?, ?> source = (Map<?, ?>) item;
                    Map<String, Object> field = new LinkedHashMap<String, Object>();
                    for (Map.Entry<?, ?> entry : source.entrySet()) field.put(String.valueOf(entry.getKey()), entry.getValue());
                    fields.add(field);
                }
            }
        }
        String message = database.defineEntity(text(body, "code"), text(body, "title"), text(body, "module"), text(body, "prefix"), text(body, "status"), text(body, "saved"), fields);
        if (message == null) database.loadInto(store);
        return message;
    }

    public synchronized String addField(String code, Map<String, Object> field) {
        String message = database.addField(code, field);
        if (message == null) database.loadInto(store);
        return message;
    }

    public synchronized String removeField(String code, String key) {
        String message = database.removeField(code, key);
        if (message == null) database.loadInto(store);
        return message;
    }

    public synchronized String addAction(String code, Map<String, Object> body) {
        if (body == null) return "请填写动作";
        return database.addAction(code, text(body, "key"), text(body, "label"), text(body, "when"), text(body, "target"));
    }

    public synchronized String removeEntity(String code) {
        String message = database.removeEntity(code);
        if (message == null) database.loadInto(store);
        return message;
    }

    public synchronized String renameOrg(String name, String sites) {
        if (name == null || name.trim().isEmpty()) return "请填写企业名称";
        database.updateOrg(name.trim(), sites == null ? "" : sites.trim());
        database.loadInto(store);
        return null;
    }

    private void auditDraft(Map<String, Object> row, String message) {
        if (!"草稿".equals(text(row, "status"))) {
            deny("只有草稿可以审核");
            return;
        }
        row.put("status", "已审核");
        ok(message);
    }

    private void rejectDraft(Map<String, Object> row, String message) {
        if (!"草稿".equals(text(row, "status"))) {
            deny("只有草稿可以驳回");
            return;
        }
        row.put("status", "已驳回");
        ok(message);
    }

    private void undoAudit(Map<String, Object> row, String childTable) {
        if (!"已审核".equals(text(row, "status"))) {
            deny("只有已审核的订单可以撤回");
            return;
        }
        if (openDownstream(childTable, text(row, "id"), text(row, "code"))) {
            deny("已有下游单据，不能撤回审核");
            return;
        }
        row.put("status", "草稿");
        ok("已撤回审核");
    }

    private void undoReject(Map<String, Object> row) {
        if (!"已驳回".equals(text(row, "status"))) {
            deny("只有已驳回的订单可以撤回");
            return;
        }
        row.put("status", "草稿");
        ok("已撤回驳回");
    }

    private void pushCheck(Map<String, Object> row) {
        if (!"已完成".equals(text(row, "status"))) {
            deny("只有已完成的工单可以下推质检");
            return;
        }
        List<Map<String, Object>> checks = store.tables.get("checks");
        if (checks != null) {
            for (Map<String, Object> item : checks) {
                if (holds(item.get("job"), text(row, "id"), text(row, "code"))) {
                    deny("该工单已有质检单");
                    return;
                }
            }
        }
        Map<String, Object> check = new LinkedHashMap<String, Object>();
        check.put("id", nextId());
        check.put("code", nextCode("QC"));
        check.put("job", text(row, "id"));
        check.put("type", "完工检验");
        check.put("material", row.get("product"));
        check.put("qty", row.get("qty"));
        check.put("owner", row.get("owner"));
        check.put("date", today());
        check.put("result", "待检验");
        check.put("status", "待检验");
        bindRefs("checks", check);
        store.tables.get("checks").add(0, check);
        ok("已下推质检单");
    }

    private void undoPushCheck(Map<String, Object> row) {
        if (!"已完成".equals(text(row, "status"))) {
            deny("只有已完成的工单可以撤回下推");
            return;
        }
        Map<String, Object> child = null;
        for (Map<String, Object> item : store.tables.get("checks")) {
            if (holds(item.get("job"), text(row, "id"), text(row, "code")) && "待检验".equals(text(item, "status"))) {
                child = item;
                break;
            }
        }
        if (child == null) {
            deny("没有可撤回的下游单据");
            return;
        }
        store.tables.get("checks").remove(child);
        ok("已撤回下推");
    }

    private void undoPush(Map<String, Object> row, String childTable, String childStatus) {
        if (!childStatus.equals(text(row, "status"))) {
            deny("只有" + childStatus + "的订单可以撤回下推");
            return;
        }
        Map<String, Object> child = null;
        for (Map<String, Object> item : store.tables.get(childTable)) {
            if (holds(item.get("order"), text(row, "id"), text(row, "code")) && childStatus.equals(text(item, "status"))) {
                child = item;
                break;
            }
        }
        if (child == null) {
            deny("没有可撤回的下游单据");
            return;
        }
        store.tables.get(childTable).remove(child);
        row.put("status", "已审核");
        ok("已撤回下推");
    }

    private void undoDecision(Map<String, Object> row, String agreed, String rejected) {
        String status = text(row, "status");
        if (!agreed.equals(status) && !rejected.equals(status)) {
            deny("只有已审批的单据可以撤回");
            return;
        }
        row.put("status", "待审批");
        ok("已撤回审批");
    }

    private void pushDelivery(Map<String, Object> row) {
        if (!"已审核".equals(text(row, "status"))) {
            deny("只有已审核的订单可以下推发货");
            return;
        }
        for (Map<String, Object> item : store.tables.get("deliveries")) {
            if (holds(item.get("order"), text(row, "id"), text(row, "code")) && !"已关闭".equals(text(item, "status"))) {
                deny("该订单已有发货单");
                return;
            }
        }
        Map<String, Object> located = find("stock", "material", text(row, "material"));
        Map<String, Object> delivery = new LinkedHashMap<String, Object>();
        delivery.put("id", nextId());
        delivery.put("code", nextCode("DN"));
        delivery.put("order", text(row, "id"));
        delivery.put("customer", row.get("customer"));
        delivery.put("material", row.get("material"));
        delivery.put("qty", row.get("qty"));
        delivery.put("warehouse", located == null ? warehouseFor(text(row, "material")) : text(located, "warehouse"));
        delivery.put("date", today());
        delivery.put("status", "待发货");
        bindRefs("deliveries", delivery);
        fillMovementLines(delivery);
        store.tables.get("deliveries").add(0, delivery);
        row.put("status", "待发货");
        ok("已下推发货单");
    }

    private void ship(Map<String, Object> row) {
        if (!"待发货".equals(text(row, "status"))) {
            deny("只有待发货的单据可以发货");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        String blocked = blockOutbound(lines, "warehouse", row, "库存不足，不能发货");
        if (blocked != null) {
            deny(blocked);
            return;
        }
        changeQty(lines, -1, "销售出库", text(row, "code"), text(row, "date"));
        row.put("status", "已发货");
        Map<String, Object> order = orderOf("salesOrders", text(row, "order"));
        if (order != null) {
            order.put("status", "已完成");
        }
        if (find("receivables", "source", text(row, "order")) == null) {
            double amount = order == null ? 0 : num(order, "amount");
            Map<String, Object> bill = new LinkedHashMap<String, Object>();
            bill.put("id", nextId());
            bill.put("code", nextCode("YS"));
            bill.put("customer", text(row, "customer"));
            bill.put("source", text(row, "order"));
            bill.put("madeBy", text(row, "code"));
            putNum(bill, "amount", amount);
            putNum(bill, "received", 0);
            putNum(bill, "balance", amount);
            bill.put("due", dueDate());
            bill.put("status", "待收款");
            store.tables.get("receivables").add(0, bill);
        }
        ok("已确认发货");
    }

    private void unship(Map<String, Object> row) {
        if (!"已发货".equals(text(row, "status"))) {
            deny("只有已发货的单据可以撤回");
            return;
        }
        Map<String, Object> bill = find("receivables", "source", text(row, "order"));
        if (bill != null && num(bill, "received") > 0) {
            deny("已收款，不能撤回发货");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        String blocked = blockFrozen(lines, "warehouse", row);
        if (blocked != null) {
            deny(blocked);
            return;
        }
        changeQty(lines, 1, "", "", "");
        if (bill != null && text(row, "code").equals(text(bill, "madeBy"))) {
            store.tables.get("receivables").remove(bill);
        }
        removeGeneratedIo(text(row, "code"));
        row.put("status", "待发货");
        Map<String, Object> order = orderOf("salesOrders", text(row, "order"));
        if (order != null && "已完成".equals(text(order, "status"))) {
            order.put("status", "待发货");
        }
        ok("已撤回发货");
    }

    private void pushReceipt(Map<String, Object> row) {
        if (!"已审核".equals(text(row, "status"))) {
            deny("只有已审核的订单可以下推收货");
            return;
        }
        for (Map<String, Object> item : store.tables.get("receipts")) {
            if (holds(item.get("order"), text(row, "id"), text(row, "code"))) {
                deny("该订单已有收货单");
                return;
            }
        }
        Map<String, Object> receipt = new LinkedHashMap<String, Object>();
        receipt.put("id", nextId());
        receipt.put("code", nextCode("RC"));
        receipt.put("order", text(row, "id"));
        receipt.put("supplier", row.get("supplier"));
        receipt.put("material", row.get("material"));
        receipt.put("qty", row.get("qty"));
        receipt.put("warehouse", warehouseFor(text(row, "material")));
        bindRefs("receipts", receipt);
        fillMovementLines(receipt);
        receipt.put("date", today());
        receipt.put("status", "待收货");
        store.tables.get("receipts").add(0, receipt);
        row.put("status", "待收货");
        ok("已下推收货单");
    }

    private void receive(Map<String, Object> row) {
        if (!"待收货".equals(text(row, "status"))) {
            deny("只有待收货的单据可以入库");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        String blocked = blockFrozen(lines, "warehouse", row);
        if (blocked != null) {
            deny(blocked);
            return;
        }
        changeQty(lines, 1, "采购入库", text(row, "code"), text(row, "date"));
        row.put("status", "已收货");
        Map<String, Object> order = orderOf("purchaseOrders", text(row, "order"));
        if (order != null) {
            order.put("status", "已完成");
        }
        if (order != null && find("payables", "source", text(row, "order")) == null) {
            Map<String, Object> bill = new LinkedHashMap<String, Object>();
            bill.put("id", nextId());
            bill.put("code", nextCode("YF"));
            bill.put("supplier", text(row, "supplier"));
            bill.put("source", text(row, "order"));
            bill.put("madeBy", text(row, "code"));
            putNum(bill, "amount", num(order, "amount"));
            putNum(bill, "paid", 0);
            putNum(bill, "balance", num(order, "amount"));
            bill.put("due", dueDate());
            bill.put("status", "待付款");
            store.tables.get("payables").add(0, bill);
        }
        ok("已确认收货");
    }

    private void unreceive(Map<String, Object> row) {
        if (!"已收货".equals(text(row, "status"))) {
            deny("只有已收货的单据可以撤回");
            return;
        }
        Map<String, Object> bill = find("payables", "source", text(row, "order"));
        if (bill != null && num(bill, "paid") > 0) {
            deny("已付款，不能撤回收货");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        String blocked = blockOutbound(lines, "warehouse", row, "库存不足，不能撤回收货");
        if (blocked != null) {
            deny(blocked);
            return;
        }
        changeQty(lines, -1, "", "", "");
        if (bill != null && text(row, "code").equals(text(bill, "madeBy"))) {
            store.tables.get("payables").remove(bill);
        }
        removeGeneratedIo(text(row, "code"));
        row.put("status", "待收货");
        Map<String, Object> order = orderOf("purchaseOrders", text(row, "order"));
        if (order != null && "已完成".equals(text(order, "status"))) {
            order.put("status", "待收货");
        }
        ok("已撤回收货");
    }

    private void move(Map<String, Object> row) {
        if (!"待调拨".equals(text(row, "status"))) {
            deny("只有待调拨的单据可以确认");
            return;
        }
        String locked = lockTransfer(row);
        if (locked != null) {
            deny(locked);
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        String blocked = blockOutbound(lines, "from", row, "库存不足，不能调拨");
        if (blocked != null) {
            deny(blocked);
            return;
        }
        for (Map<String, Object> line : lines) {
            String frozen = frozenMessage(text(line, "material"), text(row, "to"), "");
            if (frozen != null) {
                deny(frozen);
                return;
            }
        }
        changeQty(lines, -1, "调拨出库", text(row, "code"), text(row, "date"));
        for (Map<String, Object> line : lines) {
            line.put("warehouse", text(row, "to"));
        }
        changeQty(lines, 1, "调拨入库", text(row, "code"), text(row, "date"));
        row.put("status", "已调拨");
        ok("已确认调拨");
    }

    private void unmove(Map<String, Object> row) {
        if (!"已调拨".equals(text(row, "status"))) {
            deny("只有已调拨的单据可以撤回");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        String blocked = blockOutbound(lines, "to", row, "库存不足，不能撤回调拨");
        if (blocked != null) {
            deny(blocked);
            return;
        }
        for (Map<String, Object> line : lines) {
            String frozen = frozenMessage(text(line, "material"), text(row, "from"), "");
            if (frozen != null) {
                deny(frozen);
                return;
            }
        }
        changeQty(lines, -1, "", "", "");
        for (Map<String, Object> line : lines) {
            line.put("warehouse", text(row, "from"));
        }
        changeQty(lines, 1, "", "", "");
        removeGeneratedIo(text(row, "code"));
        row.put("status", "待调拨");
        ok("已撤回调拨");
    }

    private void postBill(Map<String, Object> row) {
        if (!"草稿".equals(text(row, "status"))) {
            deny("只有草稿可以过账");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        boolean outbound = text(row, "type").contains("出库");
        if (lines.isEmpty()) {
            deny(outbound ? "库存不足，不能出库" : "请填写物料");
            return;
        }
        String blocked = outbound
                ? blockOutbound(lines, "warehouse", row, "库存不足，不能出库")
                : blockFrozen(lines, "warehouse", row);
        if (blocked != null) {
            deny(blocked);
            return;
        }
        changeQty(lines, outbound ? -1 : 1, "", "", "");
        row.put("status", "已完成");
        ok("已过账");
    }

    private void unpost(Map<String, Object> row) {
        if (!"已完成".equals(text(row, "status"))) {
            deny("只有已过账的单据可以撤回");
            return;
        }
        String type = text(row, "type");
        if (!text(row, "source").isEmpty() || !manualIo(type)) {
            deny("该流水由业务单据生成，请撤回原单据");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        boolean outbound = type.contains("出库");
        String blocked = outbound
                ? blockFrozen(lines, "warehouse", row)
                : blockOutbound(lines, "warehouse", row, "库存不足，不能撤回入库");
        if (blocked != null) {
            deny(blocked);
            return;
        }
        changeQty(lines, outbound ? 1 : -1, "", "", "");
        row.put("status", "草稿");
        ok("已撤回过账");
    }

    private boolean manualIo(String type) {
        return "入库".equals(type) || "出库".equals(type) || "其他入库".equals(type) || "其他出库".equals(type);
    }

    private String lockTransfer(Map<String, Object> row) {
        List<String> materials = refIds("materials", row.get("material"));
        List<String> froms = refIds("warehouses", row.get("from"));
        if (materials.size() != 1 || froms.size() != 1) return "请先选择调出仓库和物料";
        Map<String, Object> stock = stockOf(materials.get(0), froms.get(0));
        if (stock == null) return "该仓库没有这个物料，不能调拨";
        row.put("material", text(stock, "material"));
        row.put("from", text(stock, "warehouse"));
        String code = text(stock, "code");
        if (code.isEmpty()) code = materialCode(text(stock, "material"));
        row.put("materialCode", code);
        List<String> tos = refIds("warehouses", row.get("to"));
        if (tos.size() != 1) return "请填写调入仓库";
        if (tos.get(0).equals(text(row, "from"))) return "调入仓库不能与调出仓库相同";
        row.put("to", tos.get(0));
        return null;
    }

    private int prepareCount(Map<String, Object> row) {
        boolean filled = row.containsKey("actual") || hasActual(movementLines(row));
        if (!filled) return 0;
        List<String> materials = refIds("materials", row.get("material"));
        List<String> houses = refIds("warehouses", row.get("warehouse"));
        if (materials.size() != 1 || houses.size() != 1) {
            deny("盘点一次只能选择一个仓库和一个物料");
            return -1;
        }
        double actual = row.containsKey("actual") ? num(row, "actual") : num(movementLines(row).get(0), "actual");
        if (actual < 0) {
            deny("实际数量不能小于0");
            return -1;
        }
        row.put("material", materials.get(0));
        row.put("warehouse", houses.get(0));
        Map<String, Object> stock = stockOf(materials.get(0), houses.get(0));
        double book = stock == null ? 0 : num(stock, "qty");
        putNum(row, "book", book);
        putNum(row, "actual", actual);
        putNum(row, "diff", actual - book);
        if (sameQty(actual, book)) {
            row.put("status", "已完成");
            ok("已完成盘点");
        } else {
            row.put("status", "待审批");
            ok("已提交审批");
        }
        return 1;
    }

    private String openCountApproval(Map<String, Object> count) {
        if (!"待审批".equals(text(count, "status"))) return null;
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null) return "审批中心不可用";
        String id = text(count, "id");
        for (Map<String, Object> item : approvals) {
            if ("counts".equals(text(item, "source")) && id.equals(text(item, "sourceId")) && "待审批".equals(text(item, "status"))) {
                ok("已提交审批");
                return null;
            }
        }
        Map<String, Object> approval = new LinkedHashMap<String, Object>();
        approval.put("id", nextId());
        approval.put("code", nextCode("AP"));
        approval.put("source", "counts");
        approval.put("sourceId", id);
        String code = text(count, "code");
        approval.put("title", (code.isEmpty() ? "盘点" : code) + " 差异");
        approval.put("date", text(count, "date"));
        approval.put("body", "应有 " + trimNum(num(count, "book")) + "，实盘 " + trimNum(num(count, "actual")));
        approval.put("payload", "book=" + trimNum(num(count, "book")) + ";actual=" + trimNum(num(count, "actual")) + ";diff=" + trimNum(num(count, "diff")));
        String error = prepareApproval(approval, true);
        if (error != null) return error;
        approvals.add(0, approval);
        ok("已提交审批");
        return null;
    }

    private String queueLeave(Map<String, Object> leave) {
        if (leave == null || !"待审批".equals(text(leave, "status"))) return null;
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null) return "审批中心不可用";
        String id = text(leave, "id");
        for (Map<String, Object> item : approvals) {
            if ("leaves".equals(text(item, "source")) && id.equals(text(item, "sourceId")) && "待审批".equals(text(item, "status"))) {
                return null;
            }
        }
        String person = labelOf("employees", text(leave, "name"));
        String kind = labelOf("leaveTypes", text(leave, "type"));
        Map<String, Object> approval = new LinkedHashMap<String, Object>();
        approval.put("id", nextId());
        approval.put("code", nextCode("AP"));
        approval.put("source", "leaves");
        approval.put("sourceId", id);
        approval.put("title", (person.isEmpty() ? "请假" : person) + (kind.isEmpty() ? "" : " " + kind));
        String employeeId = refId("employees", text(leave, "name"));
        if (!employeeId.isEmpty()) {
            List<String> people = new ArrayList<String>();
            people.add(employeeId);
            approval.put("applicant", people);
        }
        approval.put("date", today());
        approval.put("body", text(leave, "start") + " 至 " + text(leave, "end") + "，" + trimNum(num(leave, "days")) + " 天");
        String error = prepareApproval(approval, true);
        if (error != null) return error;
        approvals.add(0, approval);
        return "NEW";
    }

    private static boolean sameQty(double left, double right) {
        return Math.abs(left - right) < 0.0001d;
    }

    private void finishCount(Map<String, Object> row) {
        if ("待审批".equals(text(row, "status"))) {
            List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
            Map<String, Object> line = new LinkedHashMap<String, Object>();
            line.put("material", text(row, "material"));
            line.put("warehouse", text(row, "warehouse"));
            line.put("actual", row.get("actual"));
            lines.add(line);
            applyCount(row, lines);
            return;
        }
        if (!"盘点中".equals(text(row, "status"))) {
            deny("只有待审批的差异可以确认");
            return;
        }
        List<Map<String, Object>> lines = movementLines(row);
        if (hasActual(lines)) {
            applyCount(row, lines);
            return;
        }
        String material = text(row, "material");
        if (!material.isEmpty()) {
            Map<String, Object> stock = bucket(material, text(row, "warehouse"));
            double next = num(stock, "qty") + num(row, "diff");
            if (next < 0) {
                deny("库存不足，不能完成盘点");
                return;
            }
            putNum(stock, "qty", next);
            touchStock(stock);
            putNum(row, "applied", num(row, "diff"));
            if (num(row, "diff") > 0) {
                postIo("盘盈", text(row, "warehouse"), material, num(row, "diff"), text(row, "code"), text(row, "date"));
            } else if (num(row, "diff") < 0) {
                postIo("盘亏", text(row, "warehouse"), material, -num(row, "diff"), text(row, "code"), text(row, "date"));
            }
        }
        row.put("status", "已完成");
        ok("已完成盘点");
    }

    private void rejectCount(Map<String, Object> row) {
        if (!"待审批".equals(text(row, "status"))) {
            deny("只有待审批的盘点可以驳回");
            return;
        }
        row.put("status", "已驳回");
        ok("已驳回盘点");
    }

    private void undoCount(Map<String, Object> row) {
        if (!"已完成".equals(text(row, "status"))) {
            deny("只有已完成的盘点可以撤回");
            return;
        }
        Object marks = row.get("appliedLines");
        if (marks instanceof List) {
            List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
            for (Object item : (List<?>) marks) {
                if (item instanceof Map) {
                    Map<?, ?> source = (Map<?, ?>) item;
                    Map<String, Object> line = new LinkedHashMap<String, Object>();
                    line.put("material", source.get("material"));
                    line.put("warehouse", source.get("warehouse"));
                    line.put("qty", source.get("diff"));
                    lines.add(line);
                }
            }
            for (Map<String, Object> line : lines) {
                Map<String, Object> stock = findPair("stock", "material", text(line, "material"), "warehouse", text(line, "warehouse"));
                double next = (stock == null ? 0 : num(stock, "qty")) - num(line, "qty");
                if (next < 0) {
                    deny("库存不足，不能撤回盘点");
                    return;
                }
            }
            for (Map<String, Object> line : lines) {
                Map<String, Object> stock = bucket(text(line, "material"), text(line, "warehouse"));
                putNum(stock, "qty", num(stock, "qty") - num(line, "qty"));
                touchStock(stock);
            }
            removeGeneratedIo(text(row, "code"));
            row.remove("appliedLines");
            row.remove("applied");
        } else if (!text(row, "material").isEmpty() && row.containsKey("applied")) {
            Map<String, Object> stock = findPair("stock", "material", text(row, "material"), "warehouse", text(row, "warehouse"));
            double next = (stock == null ? 0 : num(stock, "qty")) - num(row, "applied");
            if (next < 0) {
                deny("库存不足，不能撤回盘点");
                return;
            }
            if (stock == null) {
                stock = bucket(text(row, "material"), text(row, "warehouse"));
            }
            putNum(stock, "qty", next);
            touchStock(stock);
            removeGeneratedIo(text(row, "code"));
            row.remove("applied");
        }
        row.put("status", "盘点中");
        ok("已撤回盘点");
    }

    private void finishJob(Map<String, Object> row) {
        if (!"生产中".equals(text(row, "status"))) {
            deny("只有生产中可以完工");
            return;
        }
        List<Map<String, Object>> parts = new ArrayList<Map<String, Object>>();
        List<Double> needs = new ArrayList<Double>();
        for (Map<String, Object> line : store.tables.get("boms")) {
            if (!text(row, "product").equals(text(line, "product")) || !"启用".equals(text(line, "status"))) {
                continue;
            }
            String childHouse = warehouseFor(text(line, "material"));
            String frozen = frozenMessage(text(line, "material"), childHouse, "");
            if (frozen != null) {
                deny(frozen);
                return;
            }
            Map<String, Object> part = findPair("stock", "material", text(line, "material"), "warehouse", childHouse);
            double need = num(line, "qty") * num(row, "qty");
            if (part == null || available(part) < need) {
                deny("材料库存不足，不能完工");
                return;
            }
            parts.add(part);
            needs.add(need);
        }
        List<Map<String, Object>> consumed = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> part = parts.get(i);
            putNum(part, "qty", num(part, "qty") - needs.get(i));
            touchStock(part);
            postIo("生产领料", text(part, "warehouse"), text(part, "material"), needs.get(i), text(row, "code"), today());
            Map<String, Object> used = new LinkedHashMap<String, Object>();
            used.put("material", text(part, "material"));
            used.put("warehouse", text(part, "warehouse"));
            putNum(used, "qty", needs.get(i));
            consumed.add(used);
        }
        String house = warehouseFor(text(row, "product"));
        String productFrozen = frozenMessage(text(row, "product"), house, "");
        if (productFrozen != null) {
            deny(productFrozen);
            return;
        }
        row.put("warehouse", house);
        Map<String, Object> stock = bucket(text(row, "product"), house);
        putNum(stock, "qty", num(stock, "qty") + num(row, "qty"));
        touchStock(stock);
        postIo("完工入库", house, text(row, "product"), num(row, "qty"), text(row, "code"), today());
        row.put("consumed", consumed);
        row.put("finished", LocalDateTime.now(SHANGHAI).format(CLOCK));
        row.put("status", "已完成");
        pushCheck(row);
        if (!failed) ok("已完工并下推质检");
    }

    private void undoFinish(Map<String, Object> row) {
        if (!"已完成".equals(text(row, "status"))) {
            deny("只有已完成的工单可以撤回");
            return;
        }
        List<Map<String, Object>> checks = store.tables.get("checks");
        if (checks != null) {
            for (Map<String, Object> item : checks) {
                if (holds(item.get("job"), text(row, "id"), text(row, "code"))) {
                    deny("已有质检单，请先撤回下推");
                    return;
                }
            }
        }
        String house = text(row, "warehouse").isEmpty() ? warehouseFor(text(row, "product")) : text(row, "warehouse");
        Map<String, Object> product = findPair("stock", "material", text(row, "product"), "warehouse", house);
        String productFrozen = frozenMessage(text(row, "product"), house, "");
        if (productFrozen != null) {
            deny(productFrozen);
            return;
        }
        if (product == null || available(product) < num(row, "qty")) {
            deny("成品库存不足，不能撤回完工");
            return;
        }
        putNum(product, "qty", num(product, "qty") - num(row, "qty"));
        touchStock(product);
        for (Map<String, Object> used : readConsumed(row)) {
            String partHouse = text(used, "warehouse").isEmpty() ? warehouseFor(text(used, "material")) : text(used, "warehouse");
            Map<String, Object> part = bucket(text(used, "material"), partHouse);
            putNum(part, "qty", num(part, "qty") + num(used, "qty"));
            touchStock(part);
        }
        removeGeneratedIo(text(row, "code"));
        row.remove("consumed");
        row.put("finished", "");
        row.put("status", "生产中");
        ok("已撤回完工");
    }

    private void setStatus(Map<String, Object> row, String from, String to, String message) {
        if (!from.equals(text(row, "status"))) {
            deny("只有" + from + "可以继续处理");
            return;
        }
        row.put("status", to);
        ok(message);
    }

    private void settle(String table, String partyKey, Map<String, Object> row, String paidKey, String openStatus, String message) {
        if (!"待核销".equals(text(row, "status"))) {
            deny("只有待核销的单据可以核销");
            return;
        }
        Map<String, Object> bill = null;
        for (Map<String, Object> item : store.tables.get(table)) {
            if (text(row, partyKey).equals(text(item, partyKey)) && openStatus.equals(text(item, "status")) && num(item, "balance") > 0) {
                bill = item;
                break;
            }
        }
        if (bill == null) {
            deny("没有可核销的往来款项");
            return;
        }
        double pay = Math.min(num(row, "amount"), num(bill, "balance"));
        putNum(bill, paidKey, num(bill, paidKey) + pay);
        putNum(bill, "balance", num(bill, "amount") - num(bill, paidKey));
        bill.put("status", num(bill, "balance") <= 0 ? "已结清" : openStatus);
        row.put("settledBill", text(bill, "id"));
        putNum(row, "settledAmount", pay);
        row.put("status", "已核销");
        ok(message);
    }

    private void unsettle(String table, String partyKey, Map<String, Object> row, String paidKey, String openStatus) {
        if (!"已核销".equals(text(row, "status"))) {
            deny("只有已核销的单据可以撤回");
            return;
        }
        double amount = row.containsKey("settledAmount") ? num(row, "settledAmount") : num(row, "amount");
        Map<String, Object> bill = byId(table, text(row, "settledBill"));
        if (bill == null) {
            for (Map<String, Object> item : store.tables.get(table)) {
                if (text(row, partyKey).equals(text(item, partyKey)) && num(item, paidKey) >= amount) {
                    bill = item;
                    break;
                }
            }
        }
        if (bill == null || num(bill, paidKey) < amount) {
            deny("没有可撤回的往来款项");
            return;
        }
        putNum(bill, paidKey, num(bill, paidKey) - amount);
        putNum(bill, "balance", num(bill, "amount") - num(bill, paidKey));
        bill.put("status", num(bill, "balance") <= 0 ? "已结清" : openStatus);
        row.remove("settledBill");
        row.remove("settledAmount");
        row.put("status", "待核销");
        ok("已撤回核销");
    }

    private void fillMovementLines(Map<String, Object> doc) {
        if (doc.get("lines") instanceof List && !((List<?>) doc.get("lines")).isEmpty()) return;
        List<String> materials = textItems(doc.get("material"), 64);
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        List<String> houses = new ArrayList<String>();
        for (String material : materials) {
            Map<String, Object> located = find("stock", "material", material);
            String house = located == null ? warehouseFor(material) : text(located, "warehouse");
            Map<String, Object> line = new LinkedHashMap<String, Object>();
            line.put("material", material);
            line.put("warehouse", house);
            line.put("qty", doc.get("qty"));
            lines.add(line);
            if (!house.isEmpty() && !houses.contains(house)) houses.add(house);
        }
        if (lines.isEmpty()) return;
        doc.put("lines", lines);
        if (houses.size() == 1) doc.put("warehouse", houses.get(0));
        else if (!houses.isEmpty()) doc.put("warehouse", houses);
    }

    private List<Map<String, Object>> movementLines(Map<String, Object> row) {
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        Object raw = row.get("lines");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> source = (Map<?, ?>) item;
                Object qty = source.get("qty") == null ? row.get("qty") : source.get("qty");
                Map<String, Object> line = stockLine(source.get("material"), source.get("warehouse"), qty);
                if (source.containsKey("actual")) line.put("actual", source.get("actual"));
                if (!text(line, "material").isEmpty()) lines.add(line);
            }
        }
        if (!lines.isEmpty()) return lines;
        List<String> materials = textItems(row.get("material"), 64);
        List<String> houses = textItems(row.get("warehouse"), 64);
        for (int i = 0; i < materials.size(); i++) {
            String house = houses.isEmpty() ? "" : houses.get(Math.min(i, houses.size() - 1));
            Map<String, Object> line = stockLine(materials.get(i), house, row.get("qty"));
            if (!text(line, "material").isEmpty()) lines.add(line);
        }
        return lines;
    }

    private Map<String, Object> stockLine(Object material, Object warehouse, Object qty) {
        Map<String, Object> line = new LinkedHashMap<String, Object>();
        line.put("material", stockRef("materials", material));
        line.put("warehouse", stockRef("warehouses", warehouse));
        line.put("qty", qty);
        return line;
    }

    private String stockRef(String table, Object token) {
        String text = token == null ? "" : String.valueOf(token).trim();
        String id = refId(table, text);
        return id.isEmpty() ? text : id;
    }

    private String blockOutbound(List<Map<String, Object>> lines, String houseKey, Map<String, Object> row, String shortage) {
        if (lines.isEmpty()) return shortage;
        Map<String, Double> need = new LinkedHashMap<String, Double>();
        for (Map<String, Object> line : lines) {
            String material = text(line, "material");
            String house = text(line, "warehouse");
            if (house.isEmpty()) house = text(row, houseKey);
            line.put("warehouse", house);
            if (material.isEmpty() || house.isEmpty() || num(line, "qty") < 0) return shortage;
            String frozen = frozenMessage(material, house, "");
            if (frozen != null) return frozen;
            String key = material + "\n" + house;
            Double current = need.get(key);
            need.put(key, (current == null ? 0d : current) + num(line, "qty"));
        }
        for (Map.Entry<String, Double> entry : need.entrySet()) {
            int split = entry.getKey().indexOf('\n');
            String material = entry.getKey().substring(0, split);
            String house = entry.getKey().substring(split + 1);
            Map<String, Object> stock = findPair("stock", "material", material, "warehouse", house);
            if (stock == null || available(stock) < entry.getValue()) return shortage;
        }
        return null;
    }

    private String blockFrozen(List<Map<String, Object>> lines, String houseKey, Map<String, Object> row) {
        for (Map<String, Object> line : lines) {
            String house = text(line, "warehouse");
            if (house.isEmpty()) house = text(row, houseKey);
            line.put("warehouse", house);
            String frozen = frozenMessage(text(line, "material"), house, "");
            if (frozen != null) return frozen;
        }
        return null;
    }

    private String frozenMessage(String material, String warehouse, String exceptId) {
        if (material.isEmpty() || warehouse.isEmpty()) return null;
        List<Map<String, Object>> counts = store.tables.get("counts");
        if (counts == null) return null;
        for (Map<String, Object> count : counts) {
            String countStatus = text(count, "status");
            if (text(count, "id").equals(exceptId) || (!"盘点中".equals(countStatus) && !"待审批".equals(countStatus))) continue;
            for (Map<String, Object> line : movementLines(count)) {
                String house = text(line, "warehouse");
                if (house.isEmpty()) house = text(count, "warehouse");
                if (material.equals(text(line, "material")) && warehouse.equals(house)) return "盘点中，不能变动库存";
            }
        }
        return null;
    }

    private void changeQty(List<Map<String, Object>> lines, int sign, String ioType, String source, String date) {
        for (Map<String, Object> line : lines) {
            double qty = num(line, "qty");
            Map<String, Object> stock = sign < 0
                    ? findPair("stock", "material", text(line, "material"), "warehouse", text(line, "warehouse"))
                    : bucket(text(line, "material"), text(line, "warehouse"));
            if (stock == null) continue;
            putNum(stock, "qty", num(stock, "qty") + sign * qty);
            touchStock(stock);
            if (ioType != null && !ioType.isEmpty() && qty > 0) {
                postIo(ioType, text(line, "warehouse"), text(line, "material"), qty, source, date);
            }
        }
    }

    private double available(Map<String, Object> stock) {
        if (stock == null) return 0;
        return num(stock, "qty") - num(stock, "hold");
    }

    private boolean hasActual(List<Map<String, Object>> lines) {
        for (Map<String, Object> line : lines) {
            Object actual = line.get("actual");
            if (actual != null && !String.valueOf(actual).trim().isEmpty()) return true;
        }
        return false;
    }

    private void applyCount(Map<String, Object> row, List<Map<String, Object>> lines) {
        for (Map<String, Object> line : lines) {
            String house = text(line, "warehouse");
            if (house.isEmpty()) house = text(row, "warehouse");
            line.put("warehouse", house);
            if (text(line, "material").isEmpty() || house.isEmpty()) {
                deny("请填写物料");
                return;
            }
            if (num(line, "actual") < 0) {
                deny("库存不足，不能完成盘点");
                return;
            }
            String frozen = frozenMessage(text(line, "material"), house, text(row, "id"));
            if (frozen != null) {
                deny(frozen);
                return;
            }
        }
        List<Map<String, Object>> marks = new ArrayList<Map<String, Object>>();
        double sum = 0;
        for (Map<String, Object> line : lines) {
            Map<String, Object> stock = bucket(text(line, "material"), text(line, "warehouse"));
            double diff = num(line, "actual") - num(stock, "qty");
            putNum(stock, "qty", num(line, "actual"));
            touchStock(stock);
            Map<String, Object> mark = new LinkedHashMap<String, Object>();
            mark.put("material", text(line, "material"));
            mark.put("warehouse", text(line, "warehouse"));
            putNum(mark, "diff", diff);
            marks.add(mark);
            sum += diff;
            if (diff > 0) postIo("盘盈", text(line, "warehouse"), text(line, "material"), diff, text(row, "code"), text(row, "date"));
            else if (diff < 0) postIo("盘亏", text(line, "warehouse"), text(line, "material"), -diff, text(row, "code"), text(row, "date"));
        }
        row.put("appliedLines", marks);
        putNum(row, "diff", sum);
        row.put("status", "已完成");
        ok("已完成盘点");
    }

    private List<Map<String, Object>> copyLines(List<?> list) {
        List<Map<String, Object>> copy = new ArrayList<Map<String, Object>>();
        for (Object item : list) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> line = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                if (entry.getKey() != null) line.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            copy.add(line);
        }
        return copy;
    }

    private void postIo(String type, String warehouse, String material, double qty, String source, String date) {
        Map<String, Object> bill = new LinkedHashMap<String, Object>();
        bill.put("id", nextId());
        bill.put("code", nextCode("IO"));
        bill.put("type", type);
        bill.put("warehouse", warehouse);
        bill.put("material", material);
        putNum(bill, "qty", qty);
        bill.put("source", source);
        bill.put("date", date.isEmpty() ? today() : date);
        bill.put("status", "已完成");
        store.tables.get("ioBills").add(0, bill);
    }

    private Map<String, Object> bucket(String material, String warehouse) {
        String materialId = refId("materials", material);
        if (materialId.isEmpty()) materialId = material == null ? "" : material;
        String houseId = refId("warehouses", warehouse);
        if (houseId.isEmpty()) houseId = warehouse == null ? "" : warehouse;
        Map<String, Object> row = findPair("stock", "material", materialId, "warehouse", houseId);
        if (row != null) return row;
        row = new LinkedHashMap<String, Object>();
        row.put("id", nextId());
        String code = materialCode(materialId);
        if (!code.isEmpty()) row.put("code", code);
        row.put("material", materialId);
        row.put("warehouse", houseId);
        putNum(row, "qty", 0);
        putNum(row, "hold", 0);
        putNum(row, "safety", 10);
        row.put("status", "预警");
        store.tables.get("stock").add(row);
        return row;
    }

    private ErpBoard openStock(Map<String, Object> incoming) {
        List<String> materials = refIds("materials", incoming.get("material"));
        List<String> houses = refIds("warehouses", incoming.get("warehouse"));
        if (materials.isEmpty() || houses.isEmpty()) {
            deny("请填写物料");
            return finish();
        }
        double safety = incoming.containsKey("safety") ? numValue(incoming.get("safety")) : 0;
        int created = 0;
        int skipped = 0;
        for (String material : materials) {
            for (String house : houses) {
                if (findPair("stock", "material", material, "warehouse", house) != null) {
                    skipped++;
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", nextId());
                String code = materialCode(material);
                if (!code.isEmpty()) row.put("code", code);
                row.put("material", material);
                row.put("warehouse", house);
                putNum(row, "qty", 0);
                putNum(row, "hold", 0);
                putNum(row, "safety", safety);
                copyRemark(row, incoming);
                touchStock(row);
                store.tables.get("stock").add(0, row);
                created++;
            }
        }
        if (created == 0) {
            deny("该物料在该仓库已有库存，请直接修改");
            return finish();
        }
        else if (skipped > 0) ok("已保存库存，重复的物料仓库已跳过");
        else ok("已保存库存");
        return finish();
    }

    private ErpBoard openBoms(Map<String, Object> incoming) {
        List<String> products = refIds("materials", incoming.get("product"));
        if (products.isEmpty()) products = refIds("materials", incoming.get("parent"));
        List<Map<String, Object>> lines = bomLines(incoming);
        if (products.isEmpty()) {
            deny("请填写产品");
            return finish();
        }
        if (lines.isEmpty()) {
            deny("请填写材料");
            return finish();
        }
        String version = incoming.get("version") == null ? "" : String.valueOf(incoming.get("version")).trim();
        if (version.isEmpty()) version = "V1";
        int created = 0;
        int skipped = 0;
        int same = 0;
        for (String product : products) {
            for (Map<String, Object> line : lines) {
                String material = text(line, "material");
                if (product.equals(material)) {
                    same++;
                    continue;
                }
                if (bomOf(product, material) != null) {
                    skipped++;
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", nextId());
                row.put("product", product);
                row.put("material", material);
                putNum(row, "qty", num(line, "qty"));
                row.put("version", version);
                row.put("status", "启用");
                copyRemark(row, incoming);
                store.tables.get("boms").add(0, row);
                created++;
            }
        }
        if (created == 0 && same > 0 && skipped == 0) {
            deny("产品和材料不能相同");
            return finish();
        }
        if (created == 0) ok("该产品的这个材料已有清单");
        else if (skipped > 0 || same > 0) ok("已保存物料清单，重复的已跳过");
        else ok("已保存物料清单");
        return finish();
    }

    private List<Map<String, Object>> bomLines(Map<String, Object> incoming) {
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        Object raw = incoming.get("lines");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> source = (Map<?, ?>) item;
                double qty = numValue(source.get("qty"));
                if (!(qty > 0)) continue;
                for (String material : refIds("materials", source.get("material"))) {
                    Map<String, Object> line = new LinkedHashMap<String, Object>();
                    line.put("material", material);
                    putNum(line, "qty", qty);
                    lines.add(line);
                }
            }
        }
        if (!lines.isEmpty()) return lines;
        double qty = numValue(incoming.get("qty"));
        if (!(qty > 0)) qty = 1;
        List<String> materials = refIds("materials", incoming.get("material"));
        if (materials.isEmpty()) materials = refIds("materials", incoming.get("child"));
        for (String material : materials) {
            Map<String, Object> line = new LinkedHashMap<String, Object>();
            line.put("material", material);
            putNum(line, "qty", qty);
            lines.add(line);
        }
        return lines;
    }

    private boolean repairMaterialBooks() {
        boolean dirty = pinBook("stock", "material", "materials", "warehouse", "warehouses", false);
        dirty = pinBook("boms", "product", "materials", "material", "materials", true) || dirty;
        dirty = dropWeaker("stock", "material", "warehouse") || dirty;
        dirty = dropWeaker("boms", "product", "material") || dirty;
        return fillStockCodes() || dirty;
    }

    private boolean pinBook(String table, String key, String target, String key2, String target2, boolean keepQty) {
        List<Map<String, Object>> rows = store.tables.get(table);
        if (rows == null) return false;
        boolean dirty = false;
        List<Map<String, Object>> extra = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : new ArrayList<Map<String, Object>>(rows)) {
            List<String> left = refIds(target, row.get(key));
            List<String> right = refIds(target2, row.get(key2));
            if (left.isEmpty() || right.isEmpty()) continue;
            if (!left.get(0).equals(row.get(key)) || !right.get(0).equals(row.get(key2))) {
                row.put(key, left.get(0));
                row.put(key2, right.get(0));
                dirty = true;
            }
            for (int i = 0; i < left.size(); i++) {
                for (int j = 0; j < right.size(); j++) {
                    if (i == 0 && j == 0) continue;
                    if (left.get(i).equals(right.get(j))) continue;
                    if (pairTaken(rows, extra, key, left.get(i), key2, right.get(j))) continue;
                    Map<String, Object> copy = new LinkedHashMap<String, Object>(row);
                    copy.put("id", nextId());
                    copy.put(key, left.get(i));
                    copy.put(key2, right.get(j));
                    if (!keepQty) {
                        putNum(copy, "qty", 0);
                        putNum(copy, "hold", 0);
                        touchStock(copy);
                    }
                    if ("stock".equals(table)) {
                        String code = materialCode(left.get(i));
                        if (!code.isEmpty()) copy.put("code", code);
                    }
                    extra.add(copy);
                    dirty = true;
                }
            }
        }
        rows.addAll(extra);
        return dirty;
    }

    private boolean dropWeaker(String table, String key, String key2) {
        List<Map<String, Object>> rows = store.tables.get(table);
        if (rows == null) return false;
        Map<String, Map<String, Object>> best = new LinkedHashMap<String, Map<String, Object>>();
        List<Map<String, Object>> drop = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            String left = text(row, key);
            String right = text(row, key2);
            if (left.isEmpty() || right.isEmpty()) continue;
            String pair = left + "\u001f" + right;
            Map<String, Object> previous = best.get(pair);
            if (previous == null) {
                best.put(pair, row);
                continue;
            }
            if (num(row, "qty") > num(previous, "qty")) {
                drop.add(previous);
                best.put(pair, row);
            } else {
                drop.add(row);
            }
        }
        if (drop.isEmpty()) return false;
        rows.removeAll(drop);
        return true;
    }

    private boolean fillStockCodes() {
        List<Map<String, Object>> rows = store.tables.get("stock");
        if (rows == null) return false;
        boolean dirty = false;
        for (Map<String, Object> row : rows) {
            if (!text(row, "code").isEmpty()) continue;
            String code = materialCode(text(row, "material"));
            if (code.isEmpty()) continue;
            row.put("code", code);
            dirty = true;
        }
        return dirty;
    }

    private boolean pairTaken(List<Map<String, Object>> rows, List<Map<String, Object>> extra, String key, String left, String key2, String right) {
        return containsPair(rows, key, left, key2, right) || containsPair(extra, key, left, key2, right);
    }

    private boolean containsPair(List<Map<String, Object>> rows, String key, String left, String key2, String right) {
        for (Map<String, Object> row : rows) {
            if (left.equals(text(row, key)) && right.equals(text(row, key2))) return true;
        }
        return false;
    }

    private String pinStoredPair(Map<String, Object> row, String key, String target, String key2, String target2, String single, String duplicate) {
        List<String> left = refIds(target, row.get(key));
        List<String> right = refIds(target2, row.get(key2));
        if (left.size() != 1 || right.size() != 1) return single;
        if ("product".equals(key) && left.get(0).equals(right.get(0))) return "产品和材料不能相同";
        Map<String, Object> other = "material".equals(key)
                ? findPair("stock", "material", left.get(0), "warehouse", right.get(0))
                : bomOf(left.get(0), right.get(0));
        if (other != null && !text(row, "id").equals(text(other, "id"))) return duplicate;
        row.put(key, left.get(0));
        row.put(key2, right.get(0));
        return null;
    }

    private void copyRemark(Map<String, Object> row, Map<String, Object> incoming) {
        Object remark = incoming.get("remark");
        if (remark == null) return;
        String value = String.valueOf(remark).trim();
        if (!value.isEmpty()) row.put("remark", value);
    }

    private String materialCode(String materialId) {
        Map<String, Object> master = byId("materials", materialId);
        return master == null ? "" : text(master, "code");
    }

    private List<String> refIds(String table, Object value) {
        List<String> ids = new ArrayList<String>();
        for (String token : textItems(value, 64)) {
            String id = refId(table, token);
            if (id.isEmpty() || ids.contains(id)) continue;
            ids.add(id);
        }
        return ids;
    }

    private Map<String, Object> bomOf(String parent, String child) {
        List<Map<String, Object>> rows = store.tables.get("boms");
        if (rows == null) return null;
        for (Map<String, Object> row : rows) {
            if (holds(row.get("product"), parent) && holds(row.get("material"), child)) return row;
        }
        return null;
    }

    private Map<String, Object> stockOf(String material, String warehouse) {
        Map<String, Object> best = null;
        List<Map<String, Object>> rows = store.tables.get("stock");
        if (rows == null || material == null || material.isEmpty() || warehouse == null || warehouse.isEmpty()) return null;
        for (Map<String, Object> row : rows) {
            if (!holds(row.get("material"), material) || !holds(row.get("warehouse"), warehouse)) continue;
            if (best == null || num(row, "qty") > num(best, "qty")) best = row;
        }
        return best;
    }

    private String customerUsed(Map<String, Object> row) {
        String id = text(row, "id");
        String code = text(row, "code");
        String name = text(row, "name");
        String[][] refs = new String[][] {
                {"contracts", "customer", "销售合同"},
                {"salesOrders", "customer", "销售订单"},
                {"deliveries", "customer", "发货单"},
                {"receivables", "customer", "应收账款"},
                {"collects", "customer", "收款单"},
        };
        for (String[] ref : refs) {
            List<Map<String, Object>> rows = store.tables.get(ref[0]);
            if (rows == null) continue;
            for (Map<String, Object> item : rows) {
                Object value = item.get(ref[1]);
                if (holds(value, id, code) || (!name.isEmpty() && holds(value, name, ""))) {
                    return "该客户已被" + ref[2] + "使用，不能删除";
                }
            }
        }
        return null;
    }

    private boolean holds(Object value, String id) {
        for (String token : textItems(value, 64)) {
            if (id.equals(token)) return true;
        }
        return false;
    }

    private void touchStock(Map<String, Object> row) {
        if (num(row, "hold") > 0) {
            row.put("status", "隔离");
            return;
        }
        row.put("status", num(row, "qty") < num(row, "safety") ? "预警" : "正常");
    }

    private Map<String, Object> stockToHold(String material, double qty) {
        Map<String, Object> found = null;
        List<Map<String, Object>> rows = store.tables.get("stock");
        if (rows == null) {
            return null;
        }
        for (Map<String, Object> row : rows) {
            if (!material.equals(text(row, "material")) || num(row, "qty") < qty) {
                continue;
            }
            found = row;
            break;
        }
        return found;
    }

    private String warehouseFor(String material) {
        Map<String, Object> located = find("stock", "material", material);
        if (located != null && !text(located, "warehouse").isEmpty()) {
            return text(located, "warehouse");
        }
        String known = refId("warehouses", "东区仓");
        if (!known.isEmpty()) return known;
        List<Map<String, Object>> warehouses = store.tables.get("warehouses");
        if (warehouses != null) {
            for (Map<String, Object> warehouse : warehouses) {
                if (!text(warehouse, "id").isEmpty()) return text(warehouse, "id");
            }
        }
        return known;
    }

    private boolean clearPlaceholderParents() {
        List<Map<String, Object>> depts = store.tables.get("depts");
        if (depts == null) return false;
        boolean dirty = false;
        for (Map<String, Object> row : depts) {
            String parent = text(row, "parent");
            if (!parent.isEmpty() && blankParent(parent)) {
                row.put("parent", "");
                dirty = true;
            }
        }
        return dirty;
    }

    private static boolean blankParent(String parent) {
        return parent == null || parent.isEmpty() || "—".equals(parent) || "-".equals(parent) || "无".equals(parent) || "无上级".equals(parent);
    }

    private String deptParentError(Map<String, Object> row, String previousName) {
        String selfId = text(row, "id");
        List<String> parents = new ArrayList<String>();
        for (String token : textItems(row.get("parent"), 64)) {
            if (blankParent(token)) continue;
            String parentId = refId("depts", token);
            if (parentId.isEmpty()) return "上级部门不存在";
            if (parentId.equals(selfId) || parentId.equals(text(row, "name")) || (!previousName.isEmpty() && parentId.equals(previousName))) {
                return "不能把本部门设为上级";
            }
            if (deptDescendant(selfId, parentId)) return "上级不能是本部门的下级";
            String climb = climbParent(selfId, parentId, new HashSet<String>());
            if (climb != null) return climb;
            if (!parents.contains(parentId)) parents.add(parentId);
        }
        if (parents.isEmpty()) row.put("parent", "");
        else if (parents.size() == 1) row.put("parent", parents.get(0));
        else row.put("parent", parents);
        return null;
    }

    private String climbParent(String selfId, String cursor, Set<String> seen) {
        while (!cursor.isEmpty()) {
            if (!seen.add(cursor) || cursor.equals(selfId)) return "上级不能是本部门的下级";
            Map<String, Object> up = byId("depts", cursor);
            if (up == null) return "上级部门不存在";
            List<String> next = parentIds(up);
            if (next.isEmpty()) return null;
            for (int i = 1; i < next.size(); i++) {
                String error = climbParent(selfId, next.get(i), seen);
                if (error != null) return error;
            }
            cursor = next.get(0);
        }
        return null;
    }

    private List<String> parentIds(Map<String, Object> row) {
        List<String> ids = new ArrayList<String>();
        for (String token : textItems(row.get("parent"), 64)) {
            if (blankParent(token)) continue;
            String id = refId("depts", token);
            if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private boolean deptDescendant(String selfId, String targetId) {
        Set<String> ids = new HashSet<String>();
        ids.add(selfId);
        List<Map<String, Object>> depts = store.tables.get("depts");
        if (depts == null) return false;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map<String, Object> item : depts) {
                if (selfId.equals(text(item, "id"))) continue;
                boolean linked = false;
                for (String parentId : parentIds(item)) {
                    if (ids.contains(parentId)) linked = true;
                }
                if (linked && ids.add(text(item, "id"))) grew = true;
            }
        }
        return ids.contains(targetId);
    }

    private Map<String, Object> findDept(String name, String skipId) {
        List<Map<String, Object>> depts = store.tables.get("depts");
        if (depts == null) return null;
        for (Map<String, Object> item : depts) {
            if (skipId.equals(text(item, "id"))) continue;
            if (name.equals(text(item, "name"))) return item;
        }
        return null;
    }

    private Map<String, Object> byId(String table, String id) {
        List<Map<String, Object>> list = store.tables.get(table);
        if (list == null) {
            return null;
        }
        for (Map<String, Object> row : list) {
            if (id.equals(text(row, "id"))) {
                return row;
            }
        }
        return null;
    }

    private Map<String, Object> find(String table, String key, String value) {
        for (Map<String, Object> row : store.tables.get(table)) {
            if (value.equals(text(row, key))) {
                return row;
            }
        }
        return null;
    }

    private Map<String, Object> findPair(String table, String key, String value, String key2, String value2) {
        if ("stock".equals(table)) return stockOf(value, value2);
        List<Map<String, Object>> rows = store.tables.get(table);
        if (rows == null) return null;
        for (Map<String, Object> row : rows) {
            if (value.equals(text(row, key)) && value2.equals(text(row, key2))) {
                return row;
            }
        }
        return null;
    }

    private String nextId() {
        store.seq += 1;
        return "n" + store.seq;
    }

    private String nextCode(String prefix) {
        store.seq += 1;
        return String.format("%s20260305%03d", prefix, store.seq);
    }

    private void deny(String message) {
        store.notice = message;
        failed = true;
    }

    private void ok(String message) {
        store.notice = message;
    }

    private ErpBoard finish() {
        if (failed) {
            String notice = store.notice;
            resetSideEffects();
            database.loadInto(store);
            store.notice = notice;
            failed = false;
            persist();
            return snapshot();
        }
        try {
            persist();
        } catch (DataConflict ex) {
            resetSideEffects();
            database.loadInto(store);
            store.notice = ex.getMessage();
            return snapshot();
        }
        applySideEffects();
        return snapshot();
    }

    private void resetSideEffects() {
        pendingEmployee = null;
        pendingLeaveId = "";
        pendingLeaveStaff = "";
        pendingLeaveName = "";
        pendingLeaveStart = "";
        pendingLeaveEnd = "";
        pendingPlace = false;
        pendingClear = false;
    }

    private void applySideEffects() {
        if (pendingEmployee != null && schedule != null) {
            Map<String, Object> row = pendingEmployee;
            pendingEmployee = null;
            String staffId = schedule.upsertStaff(text(row, "staffId"), text(row, "name"), labelOf("depts", text(row, "dept")), employeeSkills(row.get("skill")), text(row, "status"));
            if (!staffId.isEmpty()) {
                row.put("staffId", staffId);
                if (auth != null) {
                    AuthService.Issued issued = auth.ensureAccount(staffId, text(row, "name"));
                    row.put("account", issued.username);
                    if (issued.created) {
                        store.notice = "已保存员工，登录账号 " + issued.username + "，初始口令 Np@" + staffId + "2026";
                    }
                }
                persist();
            }
        }
        if (pendingClear && schedule != null) {
            schedule.clearLeave(pendingLeaveId);
            pendingClear = false;
        }
        if (pendingPlace && schedule != null) {
            String leaveId = pendingLeaveId;
            String error = schedule.placeLeave(pendingLeaveStaff, pendingLeaveName, pendingLeaveStart, pendingLeaveEnd, leaveId);
            pendingPlace = false;
            Map<String, Object> row = byId("leaves", leaveId);
            if (row != null && error != null) {
                row.put("status", "待审批");
                row.remove("blockId");
                reopenLeaveApproval(leaveId, error);
                store.notice = error;
                persist();
            } else if (row != null) {
                row.put("blockId", "L" + leaveId);
                closeLeaveApproval(leaveId);
                persist();
            }
        }
    }

    private void mirrorDepartments() {
        if (schedule == null) {
            return;
        }
        List<Map<String, Object>> depts = store.tables.get("depts");
        if (depts == null) {
            return;
        }
        List<String> names = new ArrayList<String>();
        for (Map<String, Object> row : depts) {
            String status = text(row, "status");
            if (!status.isEmpty() && !"启用".equals(status)) {
                continue;
            }
            String name = text(row, "name");
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        schedule.ensureDeptNames(names);
    }

    private void linkPeople() {
        if (schedule == null) {
            return;
        }
        boolean dirty = false;
        List<Map<String, Object>> employees = store.tables.get("employees");
        if (employees == null) {
            return;
        }
        for (StaffMember person : schedule.listStaff()) {
            Map<String, Object> row = null;
            for (Map<String, Object> item : employees) {
                if (person.id.equals(text(item, "staffId")) || person.name.equals(text(item, "name"))) {
                    row = item;
                    break;
                }
            }
            if (row == null) {
                row = new LinkedHashMap<String, Object>();
                row.put("id", nextId());
                row.put("code", nextCode("E"));
                row.put("name", person.name);
                String deptName = schedule.deptLabel(person.deptId);
                String deptId = refId("depts", deptName);
                row.put("dept", deptId.isEmpty() ? deptName : deptId);
                row.put("post", database.postId("店员"));
                row.put("skill", person.skill);
                row.put("phone", "未填写");
                row.put("status", person.maxMinutes <= 0 ? "离职" : "在职");
                row.put("staffId", person.id);
                employees.add(row);
                dirty = true;
            } else if (!person.id.equals(text(row, "staffId"))) {
                row.put("staffId", person.id);
                dirty = true;
            }
            if (auth != null && text(row, "account").isEmpty()) {
                row.put("account", auth.ensureAccount(person.id, person.name).username);
                dirty = true;
            }
        }
        List<Map<String, Object>> leaves = store.tables.get("leaves");
        if (leaves != null) {
            for (Map<String, Object> leave : new ArrayList<Map<String, Object>>(leaves)) {
                if (!"已同意".equals(text(leave, "status")) || !text(leave, "blockId").isEmpty()) {
                    continue;
                }
                String[] person = leaveStaff(leave);
                String existing = schedule.existingLeaveBlock(person[0], person[1], text(leave, "start"), text(leave, "end"));
                if (existing != null) {
                    leave.put("blockId", existing);
                    dirty = true;
                    continue;
                }
                String error = schedule.placeLeave(person[0], person[1], text(leave, "start"), text(leave, "end"), text(leave, "id"));
                if (error == null) {
                    leave.put("blockId", "L" + text(leave, "id"));
                } else if (error.startsWith("时间冲突")) {
                    leave.put("status", "待审批");
                    store.notice = error;
                } else {
                    leave.put("blockId", "skip");
                    store.notice = error;
                }
                dirty = true;
            }
        }
        if (alignRoles()) {
            dirty = true;
        }
        List<Map<String, Object>> pendingLeaves = store.tables.get("leaves");
        if (pendingLeaves != null) {
            for (Map<String, Object> leave : pendingLeaves) {
                if ("NEW".equals(queueLeave(leave))) dirty = true;
            }
        }
        if (dirty) {
            persist();
        }
    }

    private void closeLeaveApproval(String leaveId) {
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null) return;
        for (Map<String, Object> item : approvals) {
            if (!"leaves".equals(text(item, "source")) || !leaveId.equals(text(item, "sourceId"))) continue;
            if (!"待审批".equals(text(item, "status")) && !"已同意".equals(text(item, "status"))) continue;
            if (!"已同意".equals(text(item, "status"))) {
                item.put("status", "已同意");
                item.put("decided", LocalDateTime.now(SHANGHAI).format(CLOCK));
            }
            item.put("applied", "已落地");
        }
    }

    private void reopenLeaveApproval(String leaveId, String error) {
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null) return;
        for (Map<String, Object> item : approvals) {
            if (!"leaves".equals(text(item, "source")) || !leaveId.equals(text(item, "sourceId"))) continue;
            if (!"已同意".equals(text(item, "status"))) continue;
            item.put("status", "待审批");
            item.put("applied", "未落地");
            item.put("decided", "");
            item.put("opinion", error);
        }
    }

    private boolean alignRoles() {
        List<Map<String, Object>> roles = store.tables.get("roles");
        if (roles == null || (!database.seedEnabled() && roles.isEmpty())) {
            return false;
        }
        if (roles.size() == 3 && "系统管理员".equals(text(roles.get(0), "name")) && "员工".equals(text(roles.get(1), "name"))) {
            return false;
        }
        List<Map<String, Object>> next = new ArrayList<Map<String, Object>>();
        next.add(roleRow("rl1", "系统管理员", "业务单据、库存、财务、人事、排班", "admin"));
        next.add(roleRow("rl2", "员工", "本人已发布任务、不可工作时段", "在岗人员账号"));
        next.add(roleRow("rl3", "访客", "已发布任务只读", "guest"));
        store.tables.put("roles", next);
        return true;
    }

    private static Map<String, Object> roleRow(String id, String name, String scope, String users) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("name", name);
        row.put("scope", scope);
        row.put("users", users);
        row.put("status", "启用");
        return row;
    }

    private static String today() {
        return LocalDate.now(SHANGHAI).toString();
    }

    private static String dueDate() {
        return LocalDate.now(SHANGHAI).plusDays(26).toString();
    }

    private boolean canEdit(String table, String status) {
        if ("salesOrders".equals(table) || "purchaseOrders".equals(table)) {
            return "草稿".equals(status) || "已驳回".equals(status);
        }
        if ("contracts".equals(table) || "purchaseContracts".equals(table)) {
            return "草稿".equals(status);
        }
        if ("ioBills".equals(table)) {
            return "草稿".equals(status);
        }
        if ("transfers".equals(table)) {
            return "待调拨".equals(status);
        }
        if ("counts".equals(table)) {
            return "盘点中".equals(status) || "已驳回".equals(status);
        }
        if ("jobs".equals(table)) {
            return "生产中".equals(status);
        }
        if ("checks".equals(table)) {
            return "待检验".equals(status);
        }
        if ("collects".equals(table) || "payments".equals(table)) {
            return "待核销".equals(status);
        }
        if ("leaves".equals(table) || "expenses".equals(table)) {
            return "待审批".equals(status) || "已驳回".equals(status);
        }
        if ("approvals".equals(table)) {
            return "待审批".equals(status);
        }
        if ("payroll".equals(table)) {
            return "待发放".equals(status);
        }
        return true;
    }

    private boolean openDownstream(String table, String orderId, String orderCode) {
        for (Map<String, Object> item : store.tables.get(table)) {
            if (holds(item.get("order"), orderId, orderCode) && !"已关闭".equals(text(item, "status"))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> orderOf(String table, String token) {
        Map<String, Object> row = byId(table, token);
        if (row != null) return row;
        return find(table, "code", token);
    }

    private void removeGeneratedIo(String source) {
        if (source.isEmpty()) return;
        List<Map<String, Object>> list = store.tables.get("ioBills");
        for (int i = list.size() - 1; i >= 0; i--) {
            if (source.equals(text(list.get(i), "source"))) {
                list.remove(i);
            }
        }
    }

    private List<Map<String, Object>> readConsumed(Map<String, Object> row) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Object raw = row.get("consumed");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof Map) {
                    Map<?, ?> source = (Map<?, ?>) item;
                    Map<String, Object> used = new LinkedHashMap<String, Object>();
                    Object material = source.get("material");
                    used.put("material", material == null ? "" : String.valueOf(material));
                    Object house = source.get("warehouse");
                    used.put("warehouse", house == null ? "" : String.valueOf(house));
                    putNum(used, "qty", numValue(source.get("qty")));
                    list.add(used);
                }
            }
            return list;
        }
        for (Map<String, Object> line : store.tables.get("boms")) {
            if (!text(row, "product").equals(text(line, "product")) || !"启用".equals(text(line, "status"))) {
                continue;
            }
            Map<String, Object> used = new LinkedHashMap<String, Object>();
            used.put("material", text(line, "material"));
            putNum(used, "qty", num(line, "qty") * num(row, "qty"));
            list.add(used);
        }
        return list;
    }

    private void persist() {
        database.save(store);
        writeStockAudit();
    }

    private String prepareApproval(Map<String, Object> row, boolean creating) {
        String source = text(row, "source");
        if (source.isEmpty()) return "请选择板块";
        if ("approvals".equals(source) || !database.knownEntity(source)) return "板块不存在";
        String title = database.entityTitle(source);
        if (title.isEmpty()) return "板块不存在";
        row.put("type", title);
        if (row.get("sourceId") instanceof List && ((List<?>) row.get("sourceId")).size() > 1) {
            return "一张审批只对应一张来源单据";
        }
        String sourceId = text(row, "sourceId");
        if (!sourceId.isEmpty()) {
            if (byId(source, sourceId) == null) return "来源单据不存在";
            row.put("sourceId", sourceId);
        } else {
            row.put("sourceId", "");
        }
        if (text(row, "date").isEmpty()) row.put("date", today());
        if (creating) {
            row.put("status", "待审批");
            row.put("applied", "未落地");
            row.put("decided", "");
            row.put("opinion", "");
        }
        return null;
    }

    private void decideApproval(Map<String, Object> row, boolean agreed) {
        if (!"待审批".equals(text(row, "status"))) {
            deny("只有待审批的单据可以审批");
            return;
        }
        row.put("status", agreed ? "已同意" : "已驳回");
        row.put("decided", LocalDateTime.now(SHANGHAI).format(CLOCK));
        if (routeApproval(row, agreed)) return;
        row.put("applied", "未落地");
        ok(agreed ? "已同意审批" : "已驳回审批");
    }

    private void applyLedger(String table, Map<String, Object> row) {
        boolean receivable = "receivables".equals(table);
        String paidKey = receivable ? "received" : "paid";
        if (!row.containsKey(paidKey)) putNum(row, paidKey, 0);
        double paid = num(row, paidKey);
        double amount = num(row, "amount");
        if (paid - amount > 0.0001d) {
            deny(receivable ? "已收不能大于应收" : "已付不能大于应付");
            return;
        }
        putNum(row, "balance", amount - paid);
    }

    private String ledgerGate(String table, Map<String, Object> row, boolean creating, boolean insist, String explain) {
        boolean receivable = "receivables".equals(table);
        Map<String, Object> order = orderOf(receivable ? "salesOrders" : "purchaseOrders", text(row, "source"));
        if (order == null) return null;
        boolean amountDiff = !sameQty(num(row, "amount"), num(order, "amount"));
        boolean duplicate = creating && otherLedger(table, text(row, "id"), text(order, "id"), text(order, "code"));
        boolean pending = creating && pendingLedger(table, text(order, "id"));
        if (!amountDiff && !duplicate && !pending) return null;
        String amountText = receivable ? "应收金额与订单不一致" : "应付金额与订单不一致";
        String againText = (duplicate ? (receivable ? "该订单已有应收账款" : "该订单已有应付账款")
                : (receivable ? "该订单的应收正在审批" : "该订单的应付正在审批"));
        String problem = !duplicate && !pending ? amountText : (amountDiff ? amountText + "，且" + againText : againText);
        if (!insist) return problem;
        if (clip(explain).isEmpty()) return "请填写情况说明";
        row.put("_reason", problem);
        row.put("_explain", clip(explain));
        return "APPROVE";
    }

    private boolean otherLedger(String table, String selfId, String orderId, String orderCode) {
        List<Map<String, Object>> rows = store.tables.get(table);
        if (rows == null) return false;
        for (Map<String, Object> item : rows) {
            if (!selfId.isEmpty() && selfId.equals(text(item, "id"))) continue;
            if (holds(item.get("source"), orderId, orderCode)) return true;
        }
        return false;
    }

    private boolean pendingLedger(String table, String orderId) {
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null || orderId.isEmpty()) return false;
        for (Map<String, Object> item : approvals) {
            if (!table.equals(text(item, "source")) || !"待审批".equals(text(item, "status"))) continue;
            Map<String, Object> payload = readPayload(text(item, "payload"));
            if (payload != null && "amount".equals(textValue(payload.get("kind"))) && orderId.equals(textValue(payload.get("source"))) && textValue(payload.get("id")).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean fillLedgerCodes() {
        boolean dirty = false;
        List<Map<String, Object>> receivables = store.tables.get("receivables");
        List<Map<String, Object>> payables = store.tables.get("payables");
        if (receivables != null) {
            for (Map<String, Object> row : receivables) {
                if (text(row, "code").isEmpty()) {
                    row.put("code", nextCode("YS"));
                    dirty = true;
                }
            }
        }
        if (payables != null) {
            for (Map<String, Object> row : payables) {
                if (text(row, "code").isEmpty()) {
                    row.put("code", nextCode("YF"));
                    dirty = true;
                }
            }
        }
        return dirty;
    }

    private boolean linkSettlementBills() {
        return linkOpenBills("collects", "receivables", "customer", "customers")
                | linkOpenBills("payments", "payables", "supplier", "suppliers");
    }

    private boolean linkOpenBills(String table, String billTable, String party, String partyTable) {
        List<Map<String, Object>> docs = store.tables.get(table);
        List<Map<String, Object>> bills = store.tables.get(billTable);
        if (docs == null || bills == null) return false;
        boolean dirty = false;
        for (Map<String, Object> doc : docs) {
            if (!text(doc, "bill").isEmpty()) continue;
            Map<String, Object> match = null;
            int hits = 0;
            for (Map<String, Object> bill : bills) {
                if (!sameRef(partyTable, doc.get(party), bill.get(party))) continue;
                if (!sameQty(num(doc, "amount"), num(bill, "amount"))) continue;
                hits++;
                match = bill;
            }
            if (hits == 1 && match != null) {
                doc.put("bill", text(match, "id"));
                dirty = true;
            }
        }
        return dirty;
    }

    private String settlementGate(String table, Map<String, Object> row, boolean insist, String explain) {
        boolean collect = "collects".equals(table);
        String billTable = collect ? "receivables" : "payables";
        List<String> bills = refIds(billTable, row.get("bill"));
        if (bills.size() > 1) return collect ? "一张收款单只对应一张应收账款" : "一张付款单只对应一张应付账款";
        if (bills.isEmpty()) return collect ? "请选择应收账款" : "请选择应付账款";
        row.put("bill", bills.get(0));
        String problem = settlementProblem(table, row, bills.get(0));
        if (problem == null) return null;
        if (!insist) return problem;
        if (clip(explain).isEmpty()) return "请填写情况说明";
        row.put("_reason", problem);
        row.put("_explain", clip(explain));
        return "APPROVE";
    }

    private String settlementProblem(String table, Map<String, Object> row, String billId) {
        boolean collect = "collects".equals(table);
        String billTable = collect ? "receivables" : "payables";
        String orderTable = collect ? "salesOrders" : "purchaseOrders";
        String partyKey = collect ? "customer" : "supplier";
        String partyTable = collect ? "customers" : "suppliers";
        String contractTable = collect ? "contracts" : "purchaseContracts";
        Map<String, Object> bill = byId(billTable, billId);
        Map<String, Object> order = bill == null ? null : orderOf(orderTable, text(bill, "source"));
        boolean mismatch = false;
        if (order != null) {
            if (!sameRef(partyTable, row.get(partyKey), order.get(partyKey))) mismatch = true;
            if (!sameRef(contractTable, row.get("contract"), order.get("contract"))) mismatch = true;
        } else if (bill != null && !sameRef(partyTable, row.get(partyKey), bill.get(partyKey))) {
            mismatch = true;
        }
        boolean duplicate = otherSettlement(table, text(row, "id"), billId);
        boolean pending = pendingSettlement(table, billId);
        if (!mismatch && !duplicate && !pending) return null;
        String partyText = collect ? "客户或合同与订单不符" : "供应商或合同与订单不符";
        String againText = duplicate
                ? (collect ? "该应收账款已有收款单" : "该应付账款已有付款单")
                : (collect ? "该应收账款的收款正在审批" : "该应付账款的付款正在审批");
        if (mismatch && (duplicate || pending)) return partyText + "，且" + againText;
        return (duplicate || pending) ? againText : partyText;
    }

    private boolean otherSettlement(String table, String selfId, String billId) {
        String billTable = "collects".equals(table) ? "receivables" : "payables";
        Map<String, Object> bill = byId(billTable, billId);
        String billCode = bill == null ? "" : text(bill, "code");
        List<Map<String, Object>> rows = store.tables.get(table);
        if (rows == null) return false;
        for (Map<String, Object> item : rows) {
            if (!selfId.isEmpty() && selfId.equals(text(item, "id"))) continue;
            if (coversBill(table, item, bill, billId, billCode)) return true;
        }
        return false;
    }

    private boolean coversBill(String table, Map<String, Object> item, Map<String, Object> bill, String billId, String billCode) {
        String billTable = "collects".equals(table) ? "receivables" : "payables";
        for (String token : textItems(item.get("bill"), 80)) {
            if (billId.equals(token) || (!billCode.isEmpty() && billCode.equals(token))) return true;
            if (billId.equals(refId(billTable, token))) return true;
        }
        if (!text(item, "bill").isEmpty() || bill == null) return false;
        String party = "collects".equals(table) ? "customer" : "supplier";
        String partyTable = "collects".equals(table) ? "customers" : "suppliers";
        return sameRef(partyTable, item.get(party), bill.get(party)) && sameQty(num(item, "amount"), num(bill, "amount"));
    }

    private boolean pendingSettlement(String table, String billId) {
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null || billId.isEmpty()) return false;
        for (Map<String, Object> item : approvals) {
            if (table.equals(text(item, "source")) && "待审批".equals(text(item, "status")) && billId.equals(payloadBill(text(item, "payload")))) {
                return true;
            }
        }
        return false;
    }

    private boolean sameRef(String table, Object left, Object right) {
        List<String> one = refIds(table, left);
        List<String> two = refIds(table, right);
        if (one.size() != two.size()) return false;
        for (String id : one) {
            if (!two.contains(id)) return false;
        }
        return true;
    }

    private String openSettlementApproval(String table, Map<String, Object> row) {
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null) return "审批中心不可用";
        String billId = text(row, "bill");
        for (Map<String, Object> item : approvals) {
            if (table.equals(text(item, "source")) && "待审批".equals(text(item, "status")) && billId.equals(payloadBill(text(item, "payload")))) {
                return "collects".equals(table) ? "该应收账款的收款正在审批" : "该应付账款的付款正在审批";
            }
        }
        Map<String, Object> approval = new LinkedHashMap<String, Object>();
        approval.put("id", nextId());
        approval.put("code", nextCode("AP"));
        approval.put("source", table);
        approval.put("sourceId", "");
        approval.put("title", ("collects".equals(table) ? "收款单" : "付款单") + "异常");
        approval.put("date", text(row, "date").isEmpty() ? today() : text(row, "date"));
        approval.put("body", text(row, "_reason") + "\n情况说明：" + text(row, "_explain"));
        String payload = settlementPayload(table, row);
        if (payload.isEmpty()) return "审批内容无法生成单据";
        approval.put("payload", payload);
        String error = prepareApproval(approval, true);
        if (error != null) return error;
        approvals.add(0, approval);
        ok("已提交审批");
        return null;
    }

    private String settlementPayload(String table, Map<String, Object> row) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("bill", text(row, "bill"));
        payload.put("collects".equals(table) ? "customer" : "supplier", row.get("collects".equals(table) ? "customer" : "supplier"));
        payload.put("contract", row.get("contract"));
        payload.put("amount", num(row, "amount"));
        payload.put("method", row.get("method"));
        payload.put("date", text(row, "date"));
        payload.put("explain", text(row, "_explain"));
        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (Exception ex) {
            return "";
        }
    }

    private String openLedgerApproval(String table, Map<String, Object> row, String existingId, double requested, String explain) {
        boolean receivable = "receivables".equals(table);
        String note = clip(explain);
        if (note.isEmpty()) return "请填写情况说明";
        List<Map<String, Object>> approvals = store.tables.get("approvals");
        if (approvals == null) return "审批中心不可用";
        for (Map<String, Object> item : approvals) {
            if (!table.equals(text(item, "source")) || !"待审批".equals(text(item, "status"))) continue;
            Map<String, Object> pending = readPayload(text(item, "payload"));
            if (pending == null || !"amount".equals(textValue(pending.get("kind")))) continue;
            String pendingId = textValue(pending.get("id"));
            boolean sameDoc = !existingId.isEmpty() && existingId.equals(pendingId);
            boolean sameOrder = existingId.isEmpty() && text(row, "source").equals(textValue(pending.get("source")));
            if (sameDoc || sameOrder) return receivable ? "该应收金额正在审批" : "该应付金额正在审批";
        }
        Map<String, Object> order = orderOf(receivable ? "salesOrders" : "purchaseOrders", text(row, "source"));
        Map<String, Object> approval = new LinkedHashMap<String, Object>();
        approval.put("id", nextId());
        approval.put("code", nextCode("AP"));
        approval.put("source", table);
        approval.put("sourceId", existingId);
        approval.put("title", existingId.isEmpty()
                ? (receivable ? "新建应收" : "新建应付")
                : (receivable ? "应收金额调整" : "应付金额调整"));
        approval.put("date", today());
        String reason = text(row, "_reason");
        if (reason.isEmpty()) reason = receivable ? "应收金额与订单不一致" : "应付金额与订单不一致";
        approval.put("body", reason
                + "\n订单金额：" + trimNum(order == null ? 0 : num(order, "amount"))
                + "\n填写金额：" + trimNum(requested)
                + "\n情况说明：" + note);
        String payload = ledgerPayload(table, row, existingId, requested, note);
        if (payload.isEmpty()) return "审批内容无法生成单据";
        approval.put("payload", payload);
        String error = prepareApproval(approval, true);
        if (error != null) return error;
        approvals.add(0, approval);
        ok("已提交审批");
        return null;
    }

    private String ledgerPayload(String table, Map<String, Object> row, String existingId, double requested, String explain) {
        boolean receivable = "receivables".equals(table);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("kind", "amount");
        payload.put("id", existingId);
        payload.put(receivable ? "customer" : "supplier", row.get(receivable ? "customer" : "supplier"));
        payload.put("source", text(row, "source"));
        payload.put("amount", requested);
        payload.put("due", text(row, "due"));
        payload.put("explain", explain);
        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean routeLedger(Map<String, Object> approval, boolean agreed) {
        boolean receivable = "receivables".equals(text(approval, "source"));
        String table = receivable ? "receivables" : "payables";
        if (!agreed) {
            approval.put("applied", "已落地");
            ok(receivable ? "已驳回应收金额" : "已驳回应付金额");
            return true;
        }
        Map<String, Object> payload = readPayload(text(approval, "payload"));
        if (payload == null || !"amount".equals(textValue(payload.get("kind")))) {
            deny("审批内容无法生成单据");
            return true;
        }
        double amount = numValue(payload.get("amount"));
        String id = textValue(payload.get("id"));
        if (!id.isEmpty()) {
            Map<String, Object> row = byId(table, id);
            if (row == null) {
                deny("来源单据不存在");
                return true;
            }
            String paidKey = receivable ? "received" : "paid";
            if (num(row, paidKey) - amount > 0.0001d) {
                deny(receivable ? "已收不能大于应收" : "已付不能大于应付");
                return true;
            }
            putNum(row, "amount", amount);
            putNum(row, "balance", amount - num(row, paidKey));
            if (num(row, "balance") <= 0.0001d && num(row, paidKey) > 0) row.put("status", "已结清");
            else if ("已结清".equals(text(row, "status"))) row.put("status", receivable ? "待收款" : "待付款");
            approval.put("applied", "已落地");
            ok(receivable ? "已调整应收金额" : "已调整应付金额");
            return true;
        }
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("id", nextId());
        doc.put("code", nextCode(receivable ? "YS" : "YF"));
        doc.put(receivable ? "customer" : "supplier", payload.get(receivable ? "customer" : "supplier"));
        doc.put("source", payload.get("source"));
        putNum(doc, "amount", amount);
        putNum(doc, receivable ? "received" : "paid", 0);
        putNum(doc, "balance", amount);
        doc.put("due", textValue(payload.get("due")));
        doc.put("status", receivable ? "待收款" : "待付款");
        bindRefs(table, doc);
        store.tables.get(table).add(0, doc);
        approval.put("sourceId", text(doc, "id"));
        approval.put("applied", "已落地");
        ok(receivable ? "已建立应收账款" : "已建立应付账款");
        return true;
    }

    private static String clip(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 800 ? text.substring(0, 800) : text;
    }

    private String payloadBill(String json) {
        Map<String, Object> payload = readPayload(json);
        if (payload == null) return "";
        Object bill = payload.get("bill");
        if (bill instanceof List && !((List<?>) bill).isEmpty()) return String.valueOf(((List<?>) bill).get(0));
        return bill == null ? "" : String.valueOf(bill);
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.trim().isEmpty() || !json.trim().startsWith("{")) return null;
        try {
            return new ObjectMapper().readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean routeSettlement(Map<String, Object> approval, boolean agreed) {
        boolean collect = "collects".equals(text(approval, "source"));
        if (!agreed) {
            approval.put("applied", "已落地");
            ok(collect ? "已驳回收款" : "已驳回付款");
            return true;
        }
        Map<String, Object> payload = readPayload(text(approval, "payload"));
        if (payload == null || textValue(payload.get("bill")).isEmpty()) {
            deny("审批内容无法生成单据");
            return true;
        }
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("id", nextId());
        doc.put("code", nextCode(collect ? "SK" : "FK"));
        doc.put("bill", payload.get("bill"));
        doc.put("contract", payload.get("contract"));
        doc.put(collect ? "customer" : "supplier", payload.get(collect ? "customer" : "supplier"));
        putNum(doc, "amount", numValue(payload.get("amount")));
        doc.put("method", payload.get("method"));
        doc.put("date", textValue(payload.get("date")).isEmpty() ? today() : textValue(payload.get("date")));
        doc.put("status", "待核销");
        bindRefs(collect ? "collects" : "payments", doc);
        store.tables.get(collect ? "collects" : "payments").add(0, doc);
        approval.put("sourceId", text(doc, "id"));
        approval.put("applied", "已落地");
        ok(collect ? "已建立收款单" : "已建立付款单");
        return true;
    }

    private static String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean insisted(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(String.valueOf(value == null ? "" : value));
    }

    private boolean routeApproval(Map<String, Object> approval, boolean agreed) {
        if ("collects".equals(text(approval, "source")) || "payments".equals(text(approval, "source"))) {
            if (!text(approval, "payload").contains("\"bill\"")) return false;
            return routeSettlement(approval, agreed);
        }
        if ("receivables".equals(text(approval, "source")) || "payables".equals(text(approval, "source"))) {
            if (!text(approval, "payload").contains("\"kind\":\"amount\"")) return false;
            return routeLedger(approval, agreed);
        }
        if ("leaves".equals(text(approval, "source"))) {
            return routeLeave(approval, agreed);
        }
        if (!"counts".equals(text(approval, "source"))) return false;
        Map<String, Object> count = byId("counts", text(approval, "sourceId"));
        if (count == null) {
            deny("来源单据不存在");
            return true;
        }
        if (agreed) finishCount(count);
        else rejectCount(count);
        if (!failed) approval.put("applied", "已落地");
        return true;
    }

    private boolean routeLeave(Map<String, Object> approval, boolean agreed) {
        Map<String, Object> leave = byId("leaves", text(approval, "sourceId"));
        if (leave == null) {
            deny("来源单据不存在");
            return true;
        }
        if (!"待审批".equals(text(leave, "status"))) {
            deny("只有待审批的请假可以审批");
            return true;
        }
        if (!agreed) {
            leave.put("status", "已驳回");
            approval.put("applied", "已落地");
            ok("已驳回请假");
            return true;
        }
        leave.put("status", "已同意");
        if (schedule != null) {
            pendingPlace = true;
            pendingLeaveId = text(leave, "id");
            String[] person = leaveStaff(leave);
            pendingLeaveStaff = person[0];
            pendingLeaveName = person[1];
            pendingLeaveStart = text(leave, "start");
            pendingLeaveEnd = text(leave, "end");
        }
        approval.put("applied", "已落地");
        ok("已同意请假");
        return true;
    }

    private boolean dropProjectedApprovals() {
        List<Map<String, Object>> rows = store.tables.get("approvals");
        if (rows == null) return false;
        boolean dirty = false;
        Iterator<Map<String, Object>> it = rows.iterator();
        while (it.hasNext()) {
            String id = text(it.next(), "id");
            if (id.indexOf('-') >= 0) {
                it.remove();
                dirty = true;
            }
        }
        return dirty;
    }

    private void rememberStock() {
        stockBefore = stockLines();
    }

    private Map<String, String> stockLines() {
        Map<String, String> lines = new LinkedHashMap<String, String>();
        List<Map<String, Object>> rows = store.tables.get("stock");
        if (rows == null) return lines;
        for (Map<String, Object> row : rows) {
            lines.put(text(row, "id"), text(row, "material") + "\u001f" + text(row, "warehouse") + "\u001f"
                    + trimNum(num(row, "qty")) + "\u001f" + trimNum(num(row, "hold")));
        }
        return lines;
    }

    private void writeStockAudit() {
        Map<String, String> after = stockLines();
        ActorContext.Actor actor = ActorContext.get();
        if (auth == null || actor == null || stockBefore == null) {
            stockBefore = after;
            return;
        }
        String notice = store.notice == null ? "" : store.notice;
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String previous = stockBefore.get(entry.getKey());
            if (previous == null) {
                auth.audit(actor.username, "库存", stockDetail("0", "0", entry.getValue(), notice));
            } else if (!previous.equals(entry.getValue())) {
                auth.audit(actor.username, "库存", stockDetail(part(previous, 2), part(previous, 3), entry.getValue(), notice));
            }
        }
        for (Map.Entry<String, String> entry : stockBefore.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                auth.audit(actor.username, "库存", stockDetail(part(entry.getValue(), 2), part(entry.getValue(), 3),
                        part(entry.getValue(), 0) + "\u001f" + part(entry.getValue(), 1) + "\u001f0\u001f0", notice));
            }
        }
        stockBefore = after;
    }

    private static String stockDetail(String oldQty, String oldHold, String now, String notice) {
        String detail = part(now, 0) + " " + part(now, 1) + " " + oldQty + "→" + part(now, 2);
        if (!oldHold.equals(part(now, 3))) {
            detail += "，隔离 " + oldHold + "→" + part(now, 3);
        }
        return detail + "（" + notice + "）";
    }

    private static String part(String line, int index) {
        String[] parts = line.split("\u001f", -1);
        return index < parts.length ? parts[index] : "";
    }

    private static String trimNum(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private void applyDeptPeople(Map<String, Object> dept) {
        String id = text(dept, "id");
        String name = text(dept, "name");
        int count = 0;
        List<Map<String, Object>> employees = store.tables.get("employees");
        if (employees != null) {
            for (Map<String, Object> person : employees) {
                String status = text(person, "status");
                if (!status.isEmpty() && !"在职".equals(status)) continue;
                if (holds(person.get("dept"), id) || (!name.isEmpty() && holds(person.get("dept"), name))) count++;
            }
        }
        putNum(dept, "people", count);
    }

    private ErpBoard snapshot() {
        ErpBoard board = new ErpBoard();
        board.notice = store.notice;
        board.orgName = store.orgName;
        board.orgSites = store.orgSites;
        for (Map.Entry<String, List<Map<String, Object>>> entry : store.tables.entrySet()) {
            List<Map<String, Object>> copy = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> row : entry.getValue()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>(row);
                Object id = row.get("id");
                if (id != null) {
                    item.put("origin", database.originOf(String.valueOf(id)));
                }
                if ("depts".equals(entry.getKey())) applyDeptPeople(item);
                copy.add(item);
            }
            board.tables.put(entry.getKey(), copy);
        }
        board.skills = database.skills();
        board.posts = database.posts();
        return board;
    }

    public synchronized Map<String, Object> createPost(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new BusinessException("请输入岗位名称");
        }
        if (value.length() > 32) {
            value = value.substring(0, 32);
        }
        String id = database.postId(value);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", id);
        data.put("name", postName(id));
        data.put("posts", database.posts());
        return data;
    }

    public synchronized Map<String, Object> deletePost(String id, boolean confirm) {
        database.loadInto(store);
        String postId = id == null ? "" : id.trim();
        String name = postName(postId);
        if (name.isEmpty()) {
            throw new BusinessException("岗位不存在");
        }
        List<String> callers = new ArrayList<String>();
        List<Map<String, Object>> employees = store.tables.get("employees");
        if (employees != null) {
            for (Map<String, Object> row : employees) {
                if (!postId.equals(text(row, "post"))) continue;
                String person = text(row, "name");
                callers.add("员工档案 · " + (person.isEmpty() ? "未命名" : person));
            }
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("name", name);
        if (!confirm && !callers.isEmpty()) {
            data.put("deleted", Boolean.FALSE);
            data.put("callers", callers);
            return data;
        }
        boolean dirty = false;
        if (employees != null) {
            for (Map<String, Object> row : employees) {
                if (!postId.equals(text(row, "post"))) continue;
                row.put("post", "");
                dirty = true;
            }
        }
        if (dirty) {
            database.save(store);
        }
        database.deletePost(postId);
        data.put("deleted", Boolean.TRUE);
        data.put("posts", database.posts());
        return data;
    }

    private String postName(String id) {
        if (id == null || id.isEmpty()) return "";
        for (Map<String, String> row : database.posts()) {
            if (id.equals(row.get("id"))) return row.get("name");
        }
        return "";
    }

    private static String text(Map<String, Object> row, String key) {
        return one(row == null ? null : row.get(key));
    }

    private static String one(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? "" : one(list.get(0));
        }
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean holds(Object value, String id, String code) {
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (holds(item, id, code)) return true;
            }
            return false;
        }
        String token = value == null ? "" : String.valueOf(value).trim();
        if (token.isEmpty()) return false;
        if (id != null && token.equals(id)) return true;
        return code != null && !code.isEmpty() && token.equals(code);
    }

    private static boolean blank(Object value) {
        if (value instanceof List) return ((List<?>) value).isEmpty();
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private static double num(Map<String, Object> row, String key) {
        return numValue(row.get(key));
    }

    private static double numValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void applyPay(Map<String, Object> row) {
        double earned = (double) Math.round(num(row, "base") * num(row, "attend") / 22d);
        putNum(row, "amount", earned + num(row, "extra"));
    }

    private static void putNum(Map<String, Object> row, String key, double value) {
        row.put(key, whole(value));
    }

    private static Object whole(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Integer.valueOf((int) value);
        }
        return Double.valueOf(value);
    }

    private Spec specOf(String table) {
        Spec spec = Spec.of(table);
        if (spec != null) return spec;
        MetaSpec meta = database.customSpec(table);
        if (meta == null) return null;
        return new Spec(meta.prefix, meta.status, meta.saved, false, false, meta.required, null);
    }

    private boolean numeric(String table, String key) {
        return NUMBERS.contains(key) || database.isNumber(table, key);
    }

    private String noticeDeptError(Map<String, Object> row) {
        for (String name : textItems(row.get("dept"), 64)) {
            if (refId("depts", name).isEmpty()) return "部门不存在";
        }
        return null;
    }

    private void bindRefs(String table, Map<String, Object> row) {
        for (String[] ref : RefCatalog.FIELDS) {
            if (!ref[0].equals(table) || !row.containsKey(ref[1])) continue;
            Object value = row.get(ref[1]);
            if (value instanceof List || RefCatalog.multi(table, ref[1])) {
                List<String> ids = new ArrayList<String>();
                for (String token : textItems(value, 64)) {
                    String id = refId(ref[2], token);
                    if (id.isEmpty() || ids.contains(id)) continue;
                    ids.add(id);
                }
                row.put(ref[1], ids);
                continue;
            }
            String token = text(row, ref[1]);
            if (token.isEmpty()) continue;
            String id = refId(ref[2], token);
            if (!id.isEmpty()) row.put(ref[1], id);
        }
    }

    private String refId(String table, String token) {
        String value = token == null ? "" : token.trim();
        if (value.isEmpty() || blankParent(value)) return "";
        if (byId(table, value) != null) return value;
        Map<String, Object> named = find(table, "name", value);
        if (named != null) return text(named, "id");
        Map<String, Object> coded = find(table, "code", value);
        if (coded != null) return text(coded, "id");
        return "";
    }

    private String[] leaveStaff(Map<String, Object> leave) {
        String token = text(leave, "name");
        Map<String, Object> employee = byId("employees", refId("employees", token));
        String staffId = text(leave, "staffId");
        if (staffId.isEmpty() && employee != null) staffId = text(employee, "staffId");
        String name = employee == null ? token : text(employee, "name");
        return new String[] { staffId, name.isEmpty() ? token : name };
    }

    private String labelOf(String table, String token) {
        if (token == null || token.trim().isEmpty()) return "";
        Map<String, Object> row = byId(table, token.trim());
        if (row == null) row = find(table, "name", token.trim());
        if (row == null) row = find(table, "code", token.trim());
        if (row == null) return token.trim();
        if (codeLabel(table) && !text(row, "code").isEmpty()) return text(row, "code");
        if ("receivables".equals(table)) return (labelOf("customers", text(row, "customer")) + " " + trimNum(num(row, "amount"))).trim();
        if ("payables".equals(table)) return (labelOf("suppliers", text(row, "supplier")) + " " + trimNum(num(row, "amount"))).trim();
        if (!text(row, "name").isEmpty()) return text(row, "name");
        if (!text(row, "code").isEmpty()) return text(row, "code");
        return token.trim();
    }

    private Map<String, String> columnFilters(Map<String, Object> body) {
        Map<String, String> columns = new LinkedHashMap<String, String>();
        if (body == null || !(body.get("columns") instanceof Map)) return columns;
        Map<?, ?> source = (Map<?, ?>) body.get("columns");
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String text = String.valueOf(entry.getValue()).trim();
            if (!text.isEmpty()) columns.put(String.valueOf(entry.getKey()), text);
        }
        return columns;
    }

    private boolean rowMatches(String table, Map<String, Object> row, Map<String, String> columns) {
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            if (!columnMatches(table, row, entry.getKey(), entry.getValue())) return false;
        }
        return true;
    }

    private boolean columnMatches(String table, Map<String, Object> row, String field, String query) {
        if ("id".equals(field)) return idListed(row, query);
        String target = refTarget(table, field);
        if (target != null) {
            if (linked(row.get(field), target, query)) return true;
            if (identifies(target, query)) return false;
        }
        String needle = query.toLowerCase();
        return columnText(table, field, row.get(field)).toLowerCase().contains(needle);
    }

    private static boolean codeLabel(String table) {
        return "contracts".equals(table) || "purchaseContracts".equals(table) || "salesOrders".equals(table)
                || "purchaseOrders".equals(table) || "jobs".equals(table) || "checks".equals(table)
                || "deliveries".equals(table) || "receipts".equals(table) || "collects".equals(table)
                || "payments".equals(table) || "receivables".equals(table) || "payables".equals(table)
                || "ioBills".equals(table) || "transfers".equals(table) || "counts".equals(table);
    }

    private boolean idListed(Map<String, Object> row, String query) {
        String code = text(row, "code");
        for (String token : query.split("[,，]")) {
            String part = token.trim();
            if (!part.isEmpty() && holds(row.get("id"), part, code)) return true;
        }
        return false;
    }

    private boolean identifies(String target, String query) {
        for (String token : query.split("[,，]")) {
            String part = token.trim();
            if (!part.isEmpty() && !refId(target, part).isEmpty()) return true;
        }
        return false;
    }

    private boolean linked(Object stored, String target, String query) {
        for (String token : query.split("[,，]")) {
            String id = refId(target, token.trim());
            if (!id.isEmpty() && holds(stored, id, "")) return true;
        }
        return false;
    }

    private String columnText(String table, String field, Object value) {
        StringBuilder text = new StringBuilder();
        String target = "";
        for (String[] ref : RefCatalog.FIELDS) {
            if (ref[0].equals(table) && ref[1].equals(field)) {
                target = ref[2];
                break;
            }
        }
        for (String token : textItems(value, 64)) {
            appendToken(text, token);
            if (target.isEmpty()) continue;
            Map<String, Object> linked = byId(target, refId(target, token));
            if (linked == null) continue;
            appendToken(text, text(linked, "name"));
            appendToken(text, text(linked, "code"));
        }
        return text.toString();
    }

    private static void appendToken(StringBuilder text, String token) {
        if (token == null || token.isEmpty()) return;
        if (text.length() > 0) text.append(' ');
        text.append(token);
    }

    private String employeeSkills(Object value) {
        return database.skillIds(value);
    }

    public synchronized List<String> skillCallers(String skillId) {
        List<String> callers = new ArrayList<String>();
        List<Map<String, Object>> employees = store.tables.get("employees");
        if (employees == null || skillId == null) return callers;
        for (Map<String, Object> row : employees) {
            if (!ScheduleDatabase.holdsSkill(skillText(row.get("skill")), skillId)) continue;
            String name = text(row, "name");
            callers.add("员工档案 · " + (name.isEmpty() ? "未命名" : name));
        }
        return callers;
    }

    public synchronized void clearSkill(String skillId, String replacement) {
        List<Map<String, Object>> employees = store.tables.get("employees");
        if (employees == null || skillId == null) return;
        boolean dirty = false;
        for (Map<String, Object> row : employees) {
            String current = skillText(row.get("skill"));
            if (!ScheduleDatabase.holdsSkill(current, skillId)) continue;
            row.put("skill", ScheduleDatabase.withoutSkill(current, skillId, replacement));
            dirty = true;
        }
        if (dirty) database.save(store);
    }

    private static String skillText(Object value) {
        if (value instanceof List) {
            StringBuilder text = new StringBuilder();
            for (Object item : (List<?>) value) {
                if (item == null) continue;
                String token = String.valueOf(item).trim();
                if (token.isEmpty()) continue;
                if (text.length() > 0) text.append(',');
                text.append(token);
            }
            return text.toString();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> textItems(Object value, int limit) {
        List<String> items = new ArrayList<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) addItem(items, item, limit);
        } else {
            addItem(items, value, limit);
        }
        return items;
    }

    private static void addItem(List<String> items, Object value, int limit) {
        if (value == null || items.size() >= 16) return;
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "[]".equals(text) || items.contains(text)) return;
        if (text.length() > limit) text = text.substring(0, limit);
        items.add(text);
    }

    private Object typed(String table, String key, Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (item instanceof Map) return copyLines(list);
            }
            return textItems(value, 64);
        }
        if (numeric(table, key)) return whole(numValue(value));
        if (database.isLongText(table, key)) {
            if (value == null) return "";
            String text = String.valueOf(value).trim();
            return text.length() <= 4000 ? text : text.substring(0, 4000);
        }
        return clean(key, value);
    }

    public synchronized String copyEntity(String source, String code, String title) {
        String message = database.copyEntity(source, code, title);
        if (message == null) database.loadInto(store);
        return message;
    }

    private static Object clean(String key, Object value) {
        if (NUMBERS.contains(key)) {
            return whole(numValue(value));
        }
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text.length() <= 80 ? text : text.substring(0, 80);
    }

    private static final class Spec {
        private final String prefix;
        private final String status;
        private final String saved;
        private final boolean derive;
        private final boolean derivePay;
        private final String[][] required;
        private final Map<String, String> defaults;

        private Spec(String prefix, String status, String saved, boolean derive, boolean derivePay, String[][] required, Map<String, String> defaults) {
            this.prefix = prefix;
            this.status = status;
            this.saved = saved;
            this.derive = derive;
            this.derivePay = derivePay;
            this.required = required;
            this.defaults = defaults;
        }

        private static Spec of(String table) {
            if ("customers".equals(table)) return spec("CU", "启用", "已保存客户", "name:客户名称", "contact:联系人", "phone:电话");
            if ("contracts".equals(table)) return spec(null, "草稿", "已保存合同", "customer:客户", "amount:金额", "start:开始日期", "end:结束日期");
            if ("salesOrders".equals(table)) return derive("SO", "草稿", "已保存销售订单", "customer:客户", "material:物料", "qty:数量", "price:单价", "date:日期");
            if ("deliveries".equals(table)) return spec("DN", "待发货", "已保存发货单", "customer:客户", "material:物料", "qty:数量", "warehouse:仓库", "date:日期");
            if ("suppliers".equals(table)) return spec("SU", "启用", "已保存供应商", "name:供应商名称", "contact:联系人", "phone:电话");
            if ("supplierCategories".equals(table)) return spec(null, "启用", "已保存类别", "name:类别");
            if ("purchaseContracts".equals(table)) return spec("PC", "草稿", "已保存采购合同", "supplier:供应商", "amount:金额", "start:开始日期", "end:结束日期");
            if ("purchaseOrders".equals(table)) return derive("PO", "草稿", "已保存采购订单", "supplier:供应商", "material:物料", "qty:数量", "price:单价", "date:日期");
            if ("receipts".equals(table)) return spec("RC", "待收货", "已保存收货单", "supplier:供应商", "material:物料", "qty:数量", "warehouse:仓库", "date:日期");
            if ("materials".equals(table)) return spec("M", "启用", "已保存物料", "name:物料名称", "spec:规格");
            if ("units".equals(table)) return spec(null, "启用", "已保存单位", "name:单位");
            if ("categories".equals(table)) return spec(null, "启用", "已保存分类", "name:分类");
            if ("stock".equals(table)) return spec(null, "正常", "已保存库存", "material:物料", "warehouse:仓库");
            if ("ioBills".equals(table)) return spec("IO", "草稿", "已保存出入库单", "warehouse:仓库", "material:物料", "qty:数量", "date:日期");
            if ("transfers".equals(table)) return spec("TR", "待调拨", "已保存调拨单", "material:物料", "qty:数量", "from:调出仓库", "to:调入仓库", "date:日期");
            if ("counts".equals(table)) return spec("CK", "盘点中", "已保存盘点单", "warehouse:仓库", "material:物料", "date:日期");
            if ("boms".equals(table)) return spec(null, "启用", "已保存物料清单", "product:产品", "version:版本");
            if ("jobs".equals(table)) return spec("MO", "生产中", "已保存生产工单", "product:产品", "qty:数量", "start:开工日期");
            if ("checks".equals(table)) return spec("QC", "待检验", "已保存质检单", defaults("result", "待检验"), "material:物料", "qty:数量", "date:日期");
            if ("receivables".equals(table)) return spec("YS", "待收款", "已保存应收", "customer:客户", "amount:金额");
            if ("payables".equals(table)) return spec("YF", "待付款", "已保存应付", "supplier:供应商", "amount:金额");
            if ("payMethods".equals(table)) return spec(null, "启用", "已保存收付方式", "name:方式");
            if ("collects".equals(table)) return spec("SK", "待核销", "已保存收款单", "bill:应收账款", "customer:客户", "amount:金额", "date:日期");
            if ("payments".equals(table)) return spec("FK", "待核销", "已保存付款单", "bill:应付账款", "supplier:供应商", "amount:金额", "date:日期");
            if ("expenses".equals(table)) return spec("EX", "待审批", "已保存报销单", "amount:金额", "date:日期");
            if ("expenseTypes".equals(table)) return spec(null, "启用", "已保存类型", "name:类型");
            if ("employees".equals(table)) return spec("E", "在职", "已保存员工", "name:姓名", "phone:电话");
            if ("shifts".equals(table)) return spec(null, "启用", "已保存班次", "name:班次");
            if ("leaveTypes".equals(table)) return spec(null, "启用", "已保存请假类型", "name:类型");
            if ("attendance".equals(table)) return spec(null, "正常", "已保存考勤", "date:日期");
            if ("leaves".equals(table)) return spec(null, "待审批", "已保存请假", "start:开始", "end:结束", "days:天数");
            if ("payroll".equals(table)) return pay("待发放", "已保存工资", "month:月份", "attend:出勤天数");
            if ("notices".equals(table)) return spec(null, "已发布", "已发布公告", "title:标题", "date:日期");
            if ("assets".equals(table)) return spec("FA", "在用", "已保存资产", "name:名称", "date:启用日期");
            if ("depts".equals(table)) return spec(null, "启用", "已保存部门", "name:部门");
            if ("warehouses".equals(table)) return spec(null, "启用", "已保存仓库", "name:仓库");
            if ("approvals".equals(table)) return spec("AP", "待审批", "已保存审批", "source:板块", "title:标题");
            return null;
        }

        private static Spec spec(String prefix, String status, String saved, String... required) {
            return new Spec(prefix, status, saved, false, false, pairs(required), null);
        }

        private static Spec spec(String prefix, String status, String saved, Map<String, String> defaults, String... required) {
            return new Spec(prefix, status, saved, false, false, pairs(required), defaults);
        }

        private static Spec derive(String prefix, String status, String saved, String... required) {
            return new Spec(prefix, status, saved, true, false, pairs(required), null);
        }

        private static Spec pay(String status, String saved, String... required) {
            return new Spec(null, status, saved, false, true, pairs(required), null);
        }

        private static Map<String, String> defaults(String key, String value) {
            Map<String, String> map = new LinkedHashMap<String, String>();
            map.put(key, value);
            return map;
        }

        private static String[][] pairs(String[] required) {
            String[][] pairs = new String[required.length][2];
            for (int i = 0; i < required.length; i++) {
                String[] parts = required[i].split(":", 2);
                pairs[i][0] = parts[0];
                pairs[i][1] = parts[1];
            }
            return pairs;
        }
    }
}
