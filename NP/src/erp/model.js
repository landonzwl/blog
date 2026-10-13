import { reactive } from 'vue'
import optionBook from './options.json'

export const erpStore = reactive({
  notice: '',
  tables: {},
  skills: [],
  posts: [],
  orgName: '',
  orgSites: '',
  meta: [],
})

const doneStatus = ['已完成', '已发货', '已收货', '已结清', '正常', '在职', '已同意', '启用', '已通过', '已发放', '已核销', '合格', '执行中']
const blueStatus = ['已审核', '待发货', '待收货', '已发布', '在用']
const warnStatus = ['待审批', '预警', '隔离', '盘点中', '生产中', '待检验', '待发放', '待核销', '待收款', '待付款', '出勤中', '请假']

export function todayText() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
}

export function tagClass(status) {
  if (doneStatus.includes(status)) return 'np-tag np-tag-done'
  if (blueStatus.includes(status)) return 'np-tag np-tag-published'
  if (warnStatus.includes(status)) return 'np-tag np-tag-running'
  return 'np-tag'
}

export function refLabel(table, id) {
  const row = (erpStore.tables[table] || []).find((item) => item.id === id)
  if (!row) return id || ''
  const coded = ['salesOrders', 'purchaseOrders', 'contracts', 'purchaseContracts', 'jobs', 'checks', 'deliveries', 'receipts', 'collects', 'payments', 'ioBills', 'transfers', 'counts']
  if (coded.includes(table)) return row.code || row.name || id
  if (table === 'receivables') return row.code || [refLabel('customers', firstId(row.customer)), row.amount].filter((item) => item !== undefined && item !== null && item !== '').join(' ')
  if (table === 'payables') return row.code || [refLabel('suppliers', firstId(row.supplier)), row.amount].filter((item) => item !== undefined && item !== null && item !== '').join(' ')
  return row.name || row.code || id
}

function firstId(value) {
  if (Array.isArray(value)) return value.find((item) => item) || ''
  return value || ''
}

export function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function rows(name) {
  return erpStore.tables[name] || []
}

export function pendingApprovals() {
  return rows('approvals').filter((row) => row.status === '待审批')
}

export function summary() {
  const orders = rows('salesOrders')
  return {
    salesAmount: orders.reduce((sum, row) => sum + Number(row.amount), 0),
    waitShip: rows('deliveries').filter((row) => row.status === '待发货').length,
    warn: rows('stock').filter((row) => row.status === '预警').length,
    pending: pendingApprovals().length,
    orders: orders.slice(0, 5),
    todos: pendingApprovals().slice(0, 6),
    notices: rows('notices').slice(0, 4),
  }
}

export const menu = [
  { type: 'item', key: 'home', label: '工作台' },
  {
    type: 'group',
    key: 'sales',
    label: '销售管理',
    items: [
      { key: 'customers', label: '客户档案' },
      { key: 'contracts', label: '销售合同' },
      { key: 'salesOrders', label: '销售订单' },
      { key: 'deliveries', label: '发货单' },
    ],
  },
  {
    type: 'group',
    key: 'purchase',
    label: '采购管理',
    items: [
      { key: 'suppliers', label: '供应商' },
      { key: 'purchaseContracts', label: '采购合同' },
      { key: 'purchaseOrders', label: '采购订单' },
      { key: 'receipts', label: '收货单' },
    ],
  },
  {
    type: 'group',
    key: 'stock',
    label: '库存管理',
    items: [
      { key: 'materials', label: '物料' },
      { key: 'stock', label: '即时库存' },
      { key: 'ioBills', label: '出入库' },
      { key: 'transfers', label: '调拨' },
      { key: 'counts', label: '盘点' },
    ],
  },
  {
    type: 'group',
    key: 'make',
    label: '生产管理',
    items: [
      { key: 'boms', label: '物料清单' },
      { key: 'jobs', label: '生产工单' },
      { key: 'checks', label: '质检单' },
    ],
  },
  {
    type: 'group',
    key: 'finance',
    label: '财务管理',
    items: [
      { key: 'receivables', label: '应收账款' },
      { key: 'payables', label: '应付账款' },
      { key: 'collects', label: '收款单' },
      { key: 'payments', label: '付款单' },
      { key: 'expenses', label: '费用报销' },
    ],
  },
  {
    type: 'group',
    key: 'hr',
    label: '人力资源',
    items: [
      { key: 'employees', label: '员工档案' },
      { key: 'attendance', label: '考勤记录' },
      { key: 'leaves', label: '请假申请' },
      { key: 'payroll', label: '工资表' },
      { key: 'schedule', label: '任务管理' },
      { key: 'loads', label: '人员负载' },
    ],
  },
  {
    type: 'group',
    key: 'office',
    label: '协同办公',
    items: [
      { key: 'approvals', label: '审批中心' },
      { key: 'notices', label: '公告' },
    ],
  },
  {
    type: 'group',
    key: 'base',
    label: '基础设置',
    items: [
      { key: 'assets', label: '固定资产' },
      { key: 'depts', label: '部门' },
      { key: 'warehouses', label: '仓库' },
      { key: 'roles', label: '角色权限' },
      { key: 'objects', label: '业务对象' },
      { key: 'audit', label: '操作日志' },
    ],
  },
]

const pages = [
  {
    key: 'customers', title: '客户档案', table: 'customers', codePrefix: 'CU', saved: '已保存客户',
    filters: [{ key: 'keyword', label: '客户', fields: ['code', 'name', 'contact'] }, { key: 'status', label: '状态', options: ['全部', '启用', '停用'] }],
    columns: [{ key: 'code', label: '客户编号' }, { key: 'name', label: '客户名称' }, { key: 'contact', label: '联系人' }, { key: 'phone', label: '电话' }, { key: 'level', label: '等级' }, { key: 'owner', label: '负责人' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '客户名称', required: true },
      { key: 'contact', label: '联系人', required: true },
      { key: 'phone', label: '电话', required: true },
      { key: 'level', label: '等级', type: 'select', options: optionBook.customerLevels },
      { key: 'owner', label: '负责人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
    ],
    createStatus: '启用',
    actions: [
      { key: 'remove', label: '删除', danger: true },
    ],
  },
  {
    key: 'contracts', title: '销售合同', table: 'contracts', codePrefix: 'CT', saved: '已保存合同',
    filters: [{ key: 'keyword', label: '客户', fields: ['code', 'customer'] }, { key: 'status', label: '状态', options: ['全部', '草稿', '执行中', '已完成'] }],
    columns: [{ key: 'code', label: '合同编号' }, { key: 'customer', label: '客户' }, { key: 'amount', label: '金额', num: true, money: true }, { key: 'start', label: '开始日期' }, { key: 'end', label: '结束日期' }, { key: 'owner', label: '负责人' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'customer', label: '客户', type: 'select', optionsFrom: 'customers', multiple: true, required: true },
      { key: 'amount', label: '金额', type: 'number', required: true, default: 1000 },
      { key: 'start', label: '开始日期', required: true, default: todayText() },
      { key: 'end', label: '结束日期', required: true, default: '2026-12-31' },
      { key: 'owner', label: '负责人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
    ],
    editWhen: ['草稿'],
    actions: [
      { key: 'audit', label: '审核', when: ['草稿'] },
      { key: 'undoAudit', label: '撤回审核', when: ['执行中'] },
      { key: 'toOrders', label: '销售订单', when: ['执行中'], go: 'salesOrders', column: 'contract' },
    ],
  },
  {
    key: 'salesOrders', title: '销售订单', table: 'salesOrders', codePrefix: 'SO', saved: '已保存销售订单', derive: true,
    filters: [{ key: 'keyword', label: '单号/客户', fields: ['code', 'customer', 'material'] }, { key: 'status', label: '状态', options: ['全部', '草稿', '已审核', '待发货', '已完成', '已驳回'] }],
    columns: [{ key: 'code', label: '订单编号' }, { key: 'contract', label: '销售合同' }, { key: 'customer', label: '客户' }, { key: 'material', label: '物料' }, { key: 'qty', label: '数量', num: true }, { key: 'amount', label: '金额', num: true, money: true }, { key: 'owner', label: '业务员' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'contract', label: '销售合同', type: 'select', optionsFrom: 'contracts', multiple: true, clearable: true },
      { key: 'customer', label: '客户', type: 'select', optionsFrom: 'customers', multiple: true, required: true },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'price', label: '单价', type: 'number', required: true, default: 10 },
      { key: 'owner', label: '业务员', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['草稿', '已驳回'],
    actions: [
      { key: 'audit', label: '审核', when: ['草稿'] },
      { key: 'undoAudit', label: '撤回审核', when: ['已审核'] },
      { key: 'undoReject', label: '撤回驳回', when: ['已驳回'] },
      { key: 'pushDelivery', label: '下推发货', when: ['已审核'] },
      { key: 'undoPush', label: '撤回下推', when: ['待发货'] },
      { key: 'toDeliveries', label: '发货单', when: ['待发货', '已完成'], go: 'deliveries', column: 'order' },
      { key: 'remove', label: '删除', when: ['草稿'], danger: true },
    ],
  },
  {
    key: 'deliveries', title: '发货单', table: 'deliveries',
    filters: [{ key: 'keyword', label: '单号/客户', fields: ['code', 'order', 'customer', 'material'] }, { key: 'status', label: '状态', options: ['全部', '待发货', '已发货'] }],
    columns: [{ key: 'code', label: '发货单号' }, { key: 'order', label: '销售订单' }, { key: 'customer', label: '客户' }, { key: 'material', label: '物料' }, { key: 'qty', label: '数量', num: true }, { key: 'warehouse', label: '仓库' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'order', label: '销售订单', type: 'select', optionsFrom: 'salesOrders', multiple: true },
      { key: 'customer', label: '客户', type: 'select', optionsFrom: 'customers', multiple: true, required: true },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'warehouse', label: '仓库', type: 'select', optionsFrom: 'warehouses', multiple: true, required: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    actions: [
      { key: 'ship', label: '确认发货', when: ['待发货'] },
      { key: 'unship', label: '撤回发货', when: ['已发货'], danger: true },
      { key: 'toOrder', label: '销售订单', go: 'salesOrders', column: 'id', valueKey: 'order' },
    ],
  },
  {
    key: 'suppliers', title: '供应商', table: 'suppliers', codePrefix: 'SU', saved: '已保存供应商',
    filters: [{ key: 'keyword', label: '供应商', fields: ['code', 'name', 'contact'] }, { key: 'status', label: '状态', options: ['全部', '启用', '停用'] }],
    columns: [{ key: 'code', label: '供应商编号' }, { key: 'name', label: '供应商名称' }, { key: 'contact', label: '联系人' }, { key: 'phone', label: '电话' }, { key: 'category', label: '类别' }, { key: 'owner', label: '采购员' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '供应商名称', required: true },
      { key: 'contact', label: '联系人', required: true },
      { key: 'phone', label: '电话', required: true },
      { key: 'category', label: '类别', type: 'select', optionsFrom: 'supplierCategories', multiple: true },
      { key: 'owner', label: '采购员', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
    ],
    createStatus: '启用',
  },
  {
    key: 'purchaseContracts', title: '采购合同', table: 'purchaseContracts', codePrefix: 'PC', saved: '已保存采购合同',
    filters: [{ key: 'keyword', label: '供应商', fields: ['code', 'supplier'] }, { key: 'status', label: '状态', options: ['全部', '草稿', '执行中', '已完成'] }],
    columns: [{ key: 'code', label: '合同编号' }, { key: 'supplier', label: '供应商' }, { key: 'amount', label: '金额', num: true, money: true }, { key: 'start', label: '开始日期' }, { key: 'end', label: '结束日期' }, { key: 'owner', label: '采购员' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'supplier', label: '供应商', type: 'select', optionsFrom: 'suppliers', multiple: true, required: true },
      { key: 'amount', label: '金额', type: 'number', required: true, default: 1000 },
      { key: 'start', label: '开始日期', required: true, default: todayText() },
      { key: 'end', label: '结束日期', required: true, default: '2026-12-31' },
      { key: 'owner', label: '采购员', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
    ],
    editWhen: ['草稿'],
    actions: [
      { key: 'audit', label: '审核', when: ['草稿'] },
      { key: 'undoAudit', label: '撤回审核', when: ['执行中'] },
      { key: 'toOrders', label: '采购订单', when: ['执行中'], go: 'purchaseOrders', column: 'contract' },
    ],
  },
  {
    key: 'purchaseOrders', title: '采购订单', table: 'purchaseOrders', codePrefix: 'PO', saved: '已保存采购订单', derive: true,
    filters: [{ key: 'keyword', label: '单号/供应商', fields: ['code', 'supplier', 'material'] }, { key: 'status', label: '状态', options: ['全部', '草稿', '已审核', '待收货', '已完成', '已驳回'] }],
    columns: [{ key: 'code', label: '订单编号' }, { key: 'contract', label: '采购合同' }, { key: 'supplier', label: '供应商' }, { key: 'material', label: '物料' }, { key: 'qty', label: '数量', num: true }, { key: 'amount', label: '金额', num: true, money: true }, { key: 'buyer', label: '采购员' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'contract', label: '采购合同', type: 'select', optionsFrom: 'purchaseContracts', multiple: true, clearable: true },
      { key: 'supplier', label: '供应商', type: 'select', optionsFrom: 'suppliers', multiple: true, required: true },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'price', label: '单价', type: 'number', required: true, default: 10 },
      { key: 'buyer', label: '采购员', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['草稿', '已驳回'],
    actions: [
      { key: 'audit', label: '审核', when: ['草稿'] },
      { key: 'undoAudit', label: '撤回审核', when: ['已审核'] },
      { key: 'undoReject', label: '撤回驳回', when: ['已驳回'] },
      { key: 'pushReceipt', label: '下推收货', when: ['已审核'] },
      { key: 'undoPush', label: '撤回下推', when: ['待收货'] },
      { key: 'toReceipts', label: '收货单', when: ['待收货', '已完成'], go: 'receipts', column: 'order' },
      { key: 'remove', label: '删除', when: ['草稿'], danger: true },
    ],
  },
  {
    key: 'receipts', title: '收货单', table: 'receipts',
    filters: [{ key: 'keyword', label: '单号/供应商', fields: ['code', 'order', 'supplier', 'material'] }, { key: 'status', label: '状态', options: ['全部', '待收货', '已收货'] }],
    columns: [{ key: 'code', label: '收货单号' }, { key: 'order', label: '采购订单' }, { key: 'supplier', label: '供应商' }, { key: 'material', label: '物料' }, { key: 'qty', label: '数量', num: true }, { key: 'warehouse', label: '仓库' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'order', label: '采购订单', type: 'select', optionsFrom: 'purchaseOrders', multiple: true },
      { key: 'supplier', label: '供应商', type: 'select', optionsFrom: 'suppliers', multiple: true, required: true },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'warehouse', label: '仓库', type: 'select', optionsFrom: 'warehouses', multiple: true, required: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    actions: [
      { key: 'receive', label: '确认收货', when: ['待收货'] },
      { key: 'unreceive', label: '撤回收货', when: ['已收货'], danger: true },
      { key: 'toOrder', label: '采购订单', go: 'purchaseOrders', column: 'id', valueKey: 'order' },
    ],
  },
  {
    key: 'materials', title: '物料', table: 'materials', codePrefix: 'M', saved: '已保存物料',
    filters: [{ key: 'keyword', label: '物料', fields: ['code', 'name', 'spec'] }, { key: 'status', label: '状态', options: ['全部', '启用', '停用'] }],
    columns: [{ key: 'code', label: '物料编码' }, { key: 'name', label: '物料名称' }, { key: 'spec', label: '规格' }, { key: 'unit', label: '单位' }, { key: 'category', label: '分类' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '物料名称', required: true },
      { key: 'spec', label: '规格', required: true },
      { key: 'unit', label: '单位', type: 'select', optionsFrom: 'units', multiple: true },
      { key: 'category', label: '分类', type: 'select', optionsFrom: 'categories', multiple: true },
    ],
    createStatus: '启用',
  },
  {
    key: 'stock', title: '即时库存', table: 'stock',
    filters: [{ key: 'keyword', label: '物料', fields: ['code', 'material', 'warehouse'] }, { key: 'status', label: '状态', options: ['全部', '正常', '预警', '隔离'] }],
    columns: [{ key: 'code', label: '物料编码' }, { key: 'material', label: '物料' }, { key: 'warehouse', label: '仓库' }, { key: 'qty', label: '现存数量', num: true }, { key: 'hold', label: '隔离数量', num: true }, { key: 'available', label: '可用数量', num: true }, { key: 'safety', label: '安全库存', num: true }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'warehouse', label: '仓库', type: 'select', optionsFrom: 'warehouses', multiple: true, required: true },
      { key: 'safety', label: '安全库存', type: 'number', default: 0 },
    ],
    actions: [
      { key: 'toIo', label: '出入库', go: 'ioBills', column: 'material', valueKey: 'material' },
    ],
  },
  {
    key: 'ioBills', title: '出入库', table: 'ioBills', codePrefix: 'IO', saved: '已保存出入库单',
    filters: [{ key: 'keyword', label: '单号/物料', fields: ['code', 'type', 'material', 'warehouse'] }, { key: 'status', label: '状态', options: ['全部', '草稿', '已完成'] }],
    columns: [{ key: 'code', label: '单据编号' }, { key: 'type', label: '类型' }, { key: 'warehouse', label: '仓库' }, { key: 'material', label: '物料' }, { key: 'qty', label: '数量', num: true }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'type', label: '类型', type: 'select', options: ['入库', '出库'], default: '入库' },
      { key: 'warehouse', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['草稿'],
    actions: [
      { key: 'post', label: '过账', when: ['草稿'] },
      { key: 'unpost', label: '撤回过账', when: ['已完成'], manualOnly: true, danger: true },
      { key: 'toStock', label: '即时库存', go: 'stock', column: 'material', valueKey: 'material' },
    ],
  },
  {
    key: 'transfers', title: '调拨', table: 'transfers', codePrefix: 'TR', saved: '已保存调拨单',
    filters: [{ key: 'keyword', label: '单号/物料', fields: ['code', 'material'] }, { key: 'status', label: '状态', options: ['全部', '待调拨', '已调拨'] }],
    columns: [{ key: 'code', label: '调拨单号' }, { key: 'materialCode', label: '物料编码' }, { key: 'material', label: '物料' }, { key: 'qty', label: '数量', num: true }, { key: 'from', label: '调出仓库' }, { key: 'to', label: '调入仓库' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'from', label: '调出仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', required: true },
      { key: 'materialCode', label: '物料编码', readonly: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'to', label: '调入仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['待调拨'],
    createStatus: '待调拨',
    actions: [
      { key: 'move', label: '确认调拨', when: ['待调拨'] },
      { key: 'unmove', label: '撤回调拨', when: ['已调拨'], danger: true },
      { key: 'toStock', label: '即时库存', go: 'stock', column: 'material', valueKey: 'material' },
    ],
  },
  {
    key: 'counts', title: '盘点', table: 'counts', codePrefix: 'CK', saved: '已保存盘点单',
    filters: [{ key: 'keyword', label: '单号/仓库', fields: ['code', 'warehouse', 'material'] }, { key: 'status', label: '状态', options: ['全部', '盘点中', '待审批', '已驳回', '已完成'] }],
    columns: [{ key: 'code', label: '盘点单号' }, { key: 'warehouse', label: '仓库' }, { key: 'material', label: '物料' }, { key: 'book', label: '应有数量', num: true }, { key: 'actual', label: '实际数量', num: true }, { key: 'diff', label: '差异数量', num: true }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'warehouse', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', required: true },
      { key: 'book', label: '应有数量', readonly: true },
      { key: 'actual', label: '实际数量', type: 'number' },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['盘点中', '已驳回'],
    createStatus: '盘点中',
    actions: [
      { key: 'toApproval', label: '审批', when: ['待审批'], go: 'approvals', column: 'sourceId', valueKey: 'id' },
      { key: 'undo', label: '撤回盘点', when: ['已完成'], danger: true },
      { key: 'toStock', label: '即时库存', go: 'stock', column: 'material', valueKey: 'material' },
    ],
  },
  {
    key: 'boms', title: '物料清单', table: 'boms', saved: '已保存物料清单',
    filters: [{ key: 'keyword', label: '物料', fields: ['id', 'product', 'material', 'version'] }],
    columns: [{ key: 'id', label: 'ID' }, { key: 'product', label: '产品' }, { key: 'material', label: '材料' }, { key: 'qty', label: '数量', num: true }, { key: 'version', label: '版本' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'product', label: '产品', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'material', label: '材料', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'version', label: '版本', required: true, default: 'V1' },
    ],
    createStatus: '启用',
    actions: [
      { key: 'remove', label: '删除', danger: true },
    ],
  },
  {
    key: 'jobs', title: '生产工单', table: 'jobs', codePrefix: 'MO', saved: '已保存生产工单',
    filters: [{ key: 'keyword', label: '单号/产品', fields: ['code', 'product', 'workshop'] }, { key: 'status', label: '状态', options: ['全部', '生产中', '已完成'] }],
    columns: [{ key: 'code', label: '工单编号' }, { key: 'product', label: '产品' }, { key: 'qty', label: '数量', num: true }, { key: 'owner', label: '负责人' }, { key: 'workshop', label: '车间' }, { key: 'start', label: '开工日期' }, { key: 'finished', label: '完工时间' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'product', label: '产品', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'owner', label: '负责人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'workshop', label: '车间', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true, clearable: true },
      { key: 'warehouse', label: '仓库', type: 'select', optionsFrom: 'warehouses', multiple: true, clearable: true },
      { key: 'start', label: '开工日期', required: true, default: todayText() },
      { key: 'finished', label: '完工时间', readonly: true },
    ],
    editWhen: ['生产中'],
    createStatus: '生产中',
    actions: [
      { key: 'finish', label: '完工', when: ['生产中'] },
      { key: 'remove', label: '删除', when: ['生产中'], danger: true },
      { key: 'undo', label: '撤回完工', when: ['已完成'], danger: true },
      { key: 'pushCheck', label: '下推质检', when: ['已完成'] },
      { key: 'undoPush', label: '撤回下推', when: ['已完成'], danger: true },
      { key: 'toChecks', label: '质检单', when: ['已完成'], go: 'checks', column: 'job' },
    ],
  },
  {
    key: 'checks', title: '质检单', table: 'checks', codePrefix: 'QC', saved: '已保存质检单',
    filters: [{ key: 'keyword', label: '单号/物料', fields: ['code', 'type', 'material'] }, { key: 'status', label: '状态', options: ['全部', '待检验', '已完成'] }],
    columns: [{ key: 'code', label: '质检单号' }, { key: 'job', label: '生产工单' }, { key: 'type', label: '类型' }, { key: 'material', label: '物料' }, { key: 'qty', label: '数量', num: true }, { key: 'owner', label: '负责人' }, { key: 'result', label: '结果' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'job', label: '生产工单', type: 'select', optionsFrom: 'jobs', multiple: true, clearable: true },
      { key: 'type', label: '类型', suggestFrom: 'checks', suggestKey: 'type' },
      { key: 'material', label: '物料', type: 'select', optionsFrom: 'materials', multiple: true, required: true },
      { key: 'qty', label: '数量', type: 'number', required: true, default: 1 },
      { key: 'owner', label: '负责人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['待检验'],
    createStatus: '待检验',
    defaults: { result: '待检验' },
    actions: [
      { key: 'pass', label: '判定合格', when: ['待检验'] },
      { key: 'fail', label: '判定不合格', when: ['待检验'], danger: true },
      { key: 'undo', label: '撤回检验', when: ['已完成'] },
      { key: 'toJob', label: '生产工单', go: 'jobs', column: 'id', valueKey: 'job' },
    ],
  },
  {
    key: 'receivables', title: '应收账款', table: 'receivables',
    filters: [{ key: 'keyword', label: '客户/来源', fields: ['customer', 'source'] }, { key: 'status', label: '状态', options: ['全部', '待收款', '已结清'] }],
    columns: [{ key: 'code', label: '应收单号' }, { key: 'customer', label: '客户' }, { key: 'source', label: '来源单据' }, { key: 'amount', label: '应收金额', num: true, money: true }, { key: 'received', label: '已收', num: true, money: true }, { key: 'balance', label: '余额', num: true, money: true }, { key: 'due', label: '到期日' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'customer', label: '客户', type: 'select', optionsFrom: 'customers', multiple: true, required: true },
      { key: 'source', label: '来源单据', type: 'select', optionsFrom: 'salesOrders', multiple: true, clearable: true },
      { key: 'amount', label: '应收金额', type: 'number', required: true, default: 100 },
      { key: 'received', label: '已收', type: 'number', readonly: true },
      { key: 'balance', label: '余额', readonly: true },
      { key: 'due', label: '到期日', default: todayText() },
    ],
    actions: [
      { key: 'toCollects', label: '收款单', when: ['待收款', '已结清'], go: 'collects', column: 'customer', valueKey: 'customer' },
      { key: 'toOrder', label: '销售订单', go: 'salesOrders', column: 'id', valueKey: 'source' },
    ],
  },
  {
    key: 'payables', title: '应付账款', table: 'payables',
    filters: [{ key: 'keyword', label: '供应商/来源', fields: ['supplier', 'source'] }, { key: 'status', label: '状态', options: ['全部', '待付款', '已结清'] }],
    columns: [{ key: 'code', label: '应付单号' }, { key: 'supplier', label: '供应商' }, { key: 'source', label: '来源单据' }, { key: 'amount', label: '应付金额', num: true, money: true }, { key: 'paid', label: '已付', num: true, money: true }, { key: 'balance', label: '余额', num: true, money: true }, { key: 'due', label: '到期日' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'supplier', label: '供应商', type: 'select', optionsFrom: 'suppliers', multiple: true, required: true },
      { key: 'source', label: '来源单据', type: 'select', optionsFrom: 'purchaseOrders', multiple: true, clearable: true },
      { key: 'amount', label: '应付金额', type: 'number', required: true, default: 100 },
      { key: 'paid', label: '已付', type: 'number', readonly: true },
      { key: 'balance', label: '余额', readonly: true },
      { key: 'due', label: '到期日', default: todayText() },
    ],
    actions: [
      { key: 'toPayments', label: '付款单', when: ['待付款', '已结清'], go: 'payments', column: 'supplier', valueKey: 'supplier' },
      { key: 'toOrder', label: '采购订单', go: 'purchaseOrders', column: 'id', valueKey: 'source' },
    ],
  },
  {
    key: 'collects', title: '收款单', table: 'collects', codePrefix: 'SK', saved: '已保存收款单',
    filters: [{ key: 'keyword', label: '单号/客户', fields: ['code', 'customer'] }, { key: 'status', label: '状态', options: ['全部', '待核销', '已核销'] }],
    columns: [{ key: 'code', label: '收款单号' }, { key: 'bill', label: '应收账款' }, { key: 'contract', label: '销售合同' }, { key: 'customer', label: '客户' }, { key: 'amount', label: '金额', num: true, money: true }, { key: 'method', label: '方式' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'bill', label: '应收账款', type: 'select', optionsFrom: 'receivables', multiple: true, required: true },
      { key: 'contract', label: '销售合同', type: 'select', optionsFrom: 'contracts', multiple: true, clearable: true },
      { key: 'customer', label: '客户', type: 'select', optionsFrom: 'customers', multiple: true, required: true },
      { key: 'amount', label: '金额', type: 'number', required: true, default: 100 },
      { key: 'method', label: '方式', type: 'select', optionsFrom: 'payMethods', multiple: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['待核销'],
    createStatus: '待核销',
    actions: [
      { key: 'settle', label: '核销', when: ['待核销'] },
      { key: 'unsettle', label: '撤回核销', when: ['已核销'], danger: true },
      { key: 'toBill', label: '应收账款', go: 'receivables', column: 'id', valueKey: 'bill' },
    ],
  },
  {
    key: 'payments', title: '付款单', table: 'payments', codePrefix: 'FK', saved: '已保存付款单',
    filters: [{ key: 'keyword', label: '单号/供应商', fields: ['code', 'supplier'] }, { key: 'status', label: '状态', options: ['全部', '待核销', '已核销'] }],
    columns: [{ key: 'code', label: '付款单号' }, { key: 'bill', label: '应付账款' }, { key: 'contract', label: '采购合同' }, { key: 'supplier', label: '供应商' }, { key: 'amount', label: '金额', num: true, money: true }, { key: 'method', label: '方式' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'bill', label: '应付账款', type: 'select', optionsFrom: 'payables', multiple: true, required: true },
      { key: 'contract', label: '采购合同', type: 'select', optionsFrom: 'purchaseContracts', multiple: true, clearable: true },
      { key: 'supplier', label: '供应商', type: 'select', optionsFrom: 'suppliers', multiple: true, required: true },
      { key: 'amount', label: '金额', type: 'number', required: true, default: 100 },
      { key: 'method', label: '方式', type: 'select', optionsFrom: 'payMethods', multiple: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['待核销'],
    createStatus: '待核销',
    actions: [
      { key: 'settle', label: '核销', when: ['待核销'] },
      { key: 'unsettle', label: '撤回核销', when: ['已核销'], danger: true },
      { key: 'toBill', label: '应付账款', go: 'payables', column: 'id', valueKey: 'bill' },
    ],
  },
  {
    key: 'expenses', title: '费用报销', table: 'expenses', codePrefix: 'EX', saved: '已保存报销单',
    filters: [{ key: 'keyword', label: '单号/申请人', fields: ['code', 'applicant', 'type'] }, { key: 'status', label: '状态', options: ['全部', '待审批', '已通过', '已驳回'] }],
    columns: [{ key: 'code', label: '报销单号' }, { key: 'applicant', label: '申请人' }, { key: 'type', label: '类型' }, { key: 'amount', label: '金额', num: true, money: true }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'applicant', label: '申请人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'type', label: '类型', type: 'select', optionsFrom: 'expenseTypes', multiple: true },
      { key: 'amount', label: '金额', type: 'number', required: true, default: 50 },
      { key: 'date', label: '日期', required: true, default: todayText() },
    ],
    editWhen: ['待审批', '已驳回'],
    createStatus: '待审批',
    actions: [
      { key: 'undo', label: '撤回审批', when: ['已通过', '已驳回'] },
      { key: 'toEmployee', label: '员工档案', go: 'employees', column: 'id', valueKey: 'applicant' },
    ],
  },
  {
    key: 'employees', title: '员工档案', table: 'employees', codePrefix: 'E', saved: '已保存员工',
    filters: [{ key: 'keyword', label: '姓名', fields: ['code', 'name', 'dept', 'post'] }, { key: 'status', label: '状态', options: ['全部', '在职', '离职'] }],
    columns: [{ key: 'code', label: '工号' }, { key: 'name', label: '姓名' }, { key: 'dept', label: '部门' }, { key: 'post', label: '岗位' }, { key: 'skill', label: '技能' }, { key: 'base', label: '基本工资', num: true, money: true }, { key: 'account', label: '登录账号' }, { key: 'phone', label: '电话' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '姓名', required: true },
      { key: 'dept', label: '部门', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
      { key: 'post', label: '岗位', type: 'select', optionsFrom: 'posts', multiple: true },
      { key: 'skill', label: '技能', type: 'select', optionsFrom: 'skills', multiple: true },
      { key: 'base', label: '基本工资', type: 'number', default: 0 },
      { key: 'phone', label: '电话', required: true },
    ],
    createStatus: '在职',
  },
  {
    key: 'attendance', title: '考勤记录', table: 'attendance', saved: '已保存考勤',
    filters: [{ key: 'keyword', label: '姓名', fields: ['name', 'dept', 'shift'] }, { key: 'status', label: '状态', options: ['全部', '正常', '出勤中', '请假'] }],
    columns: [{ key: 'name', label: '姓名' }, { key: 'dept', label: '部门' }, { key: 'date', label: '日期' }, { key: 'shift', label: '班次' }, { key: 'clockIn', label: '上班' }, { key: 'clockOut', label: '下班' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '姓名', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'dept', label: '部门', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
      { key: 'shift', label: '班次', type: 'select', optionsFrom: 'shifts', multiple: true },
      { key: 'clockIn', label: '上班' },
      { key: 'clockOut', label: '下班' },
    ],
    createStatus: '正常',
    actions: [
      { key: 'toEmployee', label: '员工档案', go: 'employees', column: 'id', valueKey: 'name' },
    ],
  },
  {
    key: 'leaves', title: '请假申请', table: 'leaves', saved: '已保存请假',
    filters: [{ key: 'keyword', label: '姓名', fields: ['name', 'type'] }, { key: 'status', label: '状态', options: ['全部', '待审批', '已同意', '已驳回'] }],
    columns: [{ key: 'name', label: '姓名' }, { key: 'type', label: '类型' }, { key: 'start', label: '开始' }, { key: 'end', label: '结束' }, { key: 'days', label: '天数', num: true }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '姓名', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'type', label: '类型', type: 'select', optionsFrom: 'leaveTypes', multiple: true },
      { key: 'start', label: '开始', required: true, default: '2026-03-06 08:00' },
      { key: 'end', label: '结束', required: true, default: '2026-03-06 18:00' },
      { key: 'days', label: '天数', type: 'number', required: true },
    ],
    editWhen: ['待审批', '已驳回'],
    createStatus: '待审批',
    actions: [
      { key: 'toApproval', label: '审批', when: ['待审批'], go: 'approvals', column: 'sourceId', valueKey: 'id' },
      { key: 'undo', label: '撤回审批', when: ['已同意', '已驳回'] },
      { key: 'toEmployee', label: '员工档案', go: 'employees', column: 'id', valueKey: 'name' },
    ],
  },
  {
    key: 'payroll', title: '工资表', table: 'payroll', saved: '已保存工资', derivePay: true,
    filters: [{ key: 'keyword', label: '姓名', fields: ['name', 'dept', 'month'] }, { key: 'status', label: '状态', options: ['全部', '待发放', '已发放'] }],
    columns: [{ key: 'name', label: '姓名' }, { key: 'dept', label: '部门' }, { key: 'month', label: '月份' }, { key: 'base', label: '基本工资', num: true, money: true }, { key: 'attend', label: '出勤天数', num: true }, { key: 'extra', label: '其他工资', num: true, money: true }, { key: 'amount', label: '实际发放', num: true, money: true }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '姓名', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'dept', label: '部门', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
      { key: 'month', label: '月份', required: true, default: '2026-03' },
      { key: 'base', label: '基本工资', type: 'number', default: 0 },
      { key: 'attend', label: '出勤天数', type: 'number', required: true, default: 22 },
      { key: 'extra', label: '其他工资', type: 'number', default: 0 },
      { key: 'amount', label: '实际发放', readonly: true },
    ],
    editWhen: ['待发放'],
    createStatus: '待发放',
    actions: [
      { key: 'pay', label: '发放', when: ['待发放'] },
      { key: 'unpay', label: '撤回发放', when: ['已发放'], danger: true },
      { key: 'toEmployee', label: '员工档案', go: 'employees', column: 'id', valueKey: 'name' },
    ],
  },
  {
    key: 'approvals', title: '审批中心', table: 'approvals', codePrefix: 'AP', saved: '已保存审批',
    filters: [{ key: 'keyword', label: '标题', fields: ['code', 'type', 'title', 'applicant', 'sourceId'] }, { key: 'status', label: '状态', options: ['全部', '待审批', '已同意', '已驳回'] }],
    columns: [
      { key: 'code', label: '单号' }, { key: 'type', label: '板块' }, { key: 'title', label: '标题' },
      { key: 'applicant', label: '申请人' }, { key: 'sourceId', label: '来源单据' }, { key: 'date', label: '日期' },
      { key: 'applied', label: '落地' }, { key: 'opinion', label: '审批意见' }, { key: 'decided', label: '审批时间' }, { key: 'status', label: '状态', tag: true },
    ],
    fields: [
      { key: 'source', label: '板块', type: 'module', required: true },
      { key: 'sourceId', label: '来源单据', type: 'sourceDoc' },
      { key: 'title', label: '标题', required: true },
      { key: 'applicant', label: '申请人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'date', label: '日期', default: todayText() },
      { key: 'body', label: '申请说明', type: 'text' },
      { key: 'payload', label: '变更内容', type: 'text', placeholder: '留给后端按板块处理的内容，可先写说明' },
    ],
    editWhen: ['待审批'],
    createStatus: '待审批',
    actions: [
      { key: 'agree', label: '同意', when: ['待审批'] },
      { key: 'reject', label: '驳回', when: ['待审批'], danger: true },
    ],
  },
  {
    key: 'notices', title: '公告', table: 'notices', saved: '已发布公告',
    filters: [{ key: 'keyword', label: '标题', fields: ['title', 'body', 'dept'] }],
    columns: [{ key: 'title', label: '标题' }, { key: 'dept', label: '部门' }, { key: 'date', label: '日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'title', label: '标题', required: true },
      { key: 'body', label: '正文', type: 'text' },
      { key: 'dept', label: '部门', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
      { key: 'date', label: '日期', required: true, default: todayText() },
      { key: 'remark', label: '备注', type: 'text' },
    ],
    createStatus: '已发布',
  },
  {
    key: 'assets', title: '固定资产', table: 'assets', codePrefix: 'FA', saved: '已保存资产',
    filters: [{ key: 'keyword', label: '资产', fields: ['code', 'name', 'dept', 'keeper'] }, { key: 'status', label: '状态', options: ['全部', '在用', '闲置'] }],
    columns: [{ key: 'code', label: '资产编号' }, { key: 'name', label: '名称' }, { key: 'dept', label: '部门' }, { key: 'keeper', label: '保管人' }, { key: 'date', label: '启用日期' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '名称', required: true },
      { key: 'dept', label: '部门', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
      { key: 'keeper', label: '保管人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
      { key: 'date', label: '启用日期', required: true, default: todayText() },
      { key: 'status', label: '状态', type: 'select', options: ['在用', '闲置'], default: '在用' },
    ],
  },
  {
    key: 'depts', title: '部门', table: 'depts', saved: '已保存部门',
    filters: [{ key: 'keyword', label: '部门', fields: ['name', 'manager'] }],
    columns: [{ key: 'name', label: '部门' }, { key: 'parent', label: '上级' }, { key: 'manager', label: '负责人' }, { key: 'people', label: '人数', num: true }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '部门', required: true },
      { key: 'parent', label: '上级', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', clearable: true, tree: true },
      { key: 'manager', label: '负责人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
    ],
    createStatus: '启用',
  },
  {
    key: 'warehouses', title: '仓库', table: 'warehouses', saved: '已保存仓库',
    filters: [{ key: 'keyword', label: '仓库', fields: ['name', 'dept', 'keeper'] }],
    columns: [{ key: 'name', label: '仓库' }, { key: 'dept', label: '部门' }, { key: 'keeper', label: '保管人' }, { key: 'status', label: '状态', tag: true }],
    fields: [
      { key: 'name', label: '仓库', required: true },
      { key: 'dept', label: '部门', type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
      { key: 'keeper', label: '保管人', type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
    ],
    createStatus: '启用',
  },
  {
    key: 'roles', title: '角色权限', table: 'roles',
    filters: [{ key: 'keyword', label: '角色', fields: ['name', 'scope', 'users'] }],
    columns: [{ key: 'name', label: '角色' }, { key: 'scope', label: '权限范围' }, { key: 'users', label: '成员' }, { key: 'status', label: '状态', tag: true }],
  },
]

export const pageMap = Object.fromEntries(pages.map((item) => [item.key, item]))

const refWidgets = {
  contract: { type: 'select', optionsFrom: 'contracts', multiple: true },
  customer: { type: 'select', optionsFrom: 'customers', multiple: true },
  supplier: { type: 'select', optionsFrom: 'suppliers', multiple: true },
  material: { type: 'select', optionsFrom: 'materials', multiple: true },
  product: { type: 'select', optionsFrom: 'materials', multiple: true },
  warehouse: { type: 'select', optionsFrom: 'warehouses', multiple: true },
  from: { type: 'select', optionsFrom: 'warehouses', multiple: true },
  to: { type: 'select', optionsFrom: 'warehouses', multiple: true },
  owner: { type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
  buyer: { type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
  keeper: { type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
  manager: { type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
  applicant: { type: 'select', optionsFrom: 'employees', optionsStatus: '在职', multiple: true },
  workshop: { type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
  dept: { type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
  job: { type: 'select', optionsFrom: 'jobs', multiple: true, clearable: true },
  parent: { type: 'select', optionsFrom: 'depts', optionsStatus: '启用', multiple: true },
}

function formField(field) {
  const widget = field.type === 'number' || field.type === 'text' ? null : refWidgets[field.key]
  return {
    key: field.key,
    label: field.label,
    required: field.required,
    type: widget ? widget.type : field.type === 'number' ? 'number' : field.type === 'text' ? 'text' : field.type === 'enum' ? 'select' : undefined,
    options: field.options ? String(field.options).split(',').filter(Boolean) : undefined,
    optionsFrom: widget?.optionsFrom,
    optionsStatus: widget?.optionsStatus,
    multiple: widget?.multiple,
  }
}

function withRemark(page, entity) {
  if (!page || !Array.isArray(page.fields) || page.fields.some((field) => field.key === 'remark')) return page
  if (entity && !(entity.fields || []).some((field) => field.key === 'remark')) return page
  return { ...page, fields: page.fields.concat([{ key: 'remark', label: '备注', type: 'text' }]) }
}

function keptPage(base, entity) {
  const catalogKeys = new Set((entity.fields || []).map((field) => field.key))
  return {
    ...base,
    columns: (base.columns || []).filter((column) => column.key === 'status' || column.key === 'available' || column.key === 'code' || column.key === 'id' || catalogKeys.has(column.key)),
    fields: (base.fields || []).filter((field) => catalogKeys.has(field.key)),
  }
}

function pageFromMeta(entity) {
  const fields = (entity.fields || []).filter((field) => !['status', 'code'].includes(field.key))
  const listed = fields.filter((field) => field.type !== 'text')
  return withRemark({
    key: entity.code,
    title: entity.title,
    table: entity.code,
    codePrefix: entity.prefix || undefined,
    saved: entity.saved || '已保存',
    filters: [{ key: 'keyword', label: '关键字', fields: fields.map((field) => field.key) }, { key: 'status', label: '状态', options: ['全部', entity.status || '草稿'] }],
    columns: listed.map((field) => ({ key: field.key, label: field.label, num: field.type === 'number' })).concat([{ key: 'status', label: '状态', tag: true }]),
    fields: fields.map(formField),
    createStatus: entity.status || '草稿',
    actions: (entity.actions || []).filter((action) => action.target).map((action) => ({ key: action.key, label: action.label, when: action.when ? [action.when] : [] })),
  }, entity)
}

export function resolvePage(key) {
  const entity = (erpStore.meta || []).find((item) => item.code === key)
  const base = pageMap[key]
  if (!base) return entity && !entity.builtin ? pageFromMeta(entity) : withRemark(base, entity)
  if (!entity) return withRemark(base)
  const shaped = keptPage(base, entity)
  const known = new Set([
    ...shaped.columns.map((column) => column.key),
    ...shaped.fields.map((field) => field.key),
  ])
  const extra = (entity.fields || []).filter((field) => !known.has(field.key) && !['id', 'origin', 'status', 'code'].includes(field.key))
  const listed = extra.filter((field) => field.type !== 'text')
  return withRemark({
    ...shaped,
    columns: shaped.columns.concat(listed.map((field) => ({ key: field.key, label: field.label, num: field.type === 'number' }))),
    fields: shaped.fields.concat(extra.map(formField)),
  }, entity)
}

export function rowLabel(row) {
  if (row.code) return row.code
  if (row.name) return row.name
  if (row.title) return row.title
  if (row.material) return refLabel('materials', row.material)
  return ''
}
