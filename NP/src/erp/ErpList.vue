<script setup>
import { computed, reactive, ref, watch } from 'vue'
import RefSelect from './RefSelect.vue'
import StockLines from './StockLines.vue'
import SkillSelect from '../SkillSelect.vue'
import { actDoc, addField, createDoc, downloadFile, listFiles, queryDocs, removeField, removeFile, updateDoc, uploadFile } from './api'
import { erpStore, money, refLabel, resolvePage, rowLabel, tagClass } from './model'

const props = defineProps({
  pageKey: { type: String, required: true },
  columnPreset: { type: Object, default: null },
})

const emit = defineEmits(['open'])

const page = computed(() => resolvePage(props.pageKey))
const listPage = ref(1)
const pageSize = 10
const dialog = ref('')
const formError = ref('')
const editingId = ref('')
const viewRow = ref(null)
const pending = ref(null)
const confirmError = ref('')
const insistMessage = ref('')
const insistExplain = ref('')
const insistStep = ref('warn')
const insistHint = ref('')
const forceSettlement = ref(false)
const pulledSource = ref('')
const pulledBill = ref('')
const pulledName = ref('')
const leaveStamp = ref('')
const form = reactive({})
const formLines = ref([])
const fieldForm = reactive({ key: '', label: '', type: 'string' })
const files = ref([])
const addedName = ref('')
const pendingFiles = ref([])
const query = reactive({ keyword: '', status: '全部' })
const applied = reactive({ keyword: '', status: '全部' })
const columnQuery = reactive({})
const appliedColumns = reactive({})
const columnHits = ref(null)

const sourceRows = computed(() => erpStore.tables[page.value?.table] || [])

const queryColumns = computed(() => (page.value?.columns || []).filter((col) => col.key !== 'status'))

const filtered = computed(() => {
  const rows = columnHits.value == null ? sourceRows.value : columnHits.value
  const keyword = applied.keyword.trim()
  return rows.filter((row) => {
    if (keyword) {
      const blob = (page.value?.columns || []).map((col) => fieldText(row, col.key)).join('\n')
      if (!blob.includes(keyword)) return false
    }
    if (columnHits.value == null && !localColumnsMatch(row)) return false
    if (applied.status && applied.status !== '全部' && row.status !== applied.status) return false
    return true
  })
})

const ordered = computed(() => {
  const rows = filtered.value
  if (props.pageKey !== 'depts') return rows
  const all = sourceRows.value
  return rows.slice().sort((left, right) => {
    const pathLeft = ancestorNames(left, all).concat(left.name || '').join('\u0001')
    const pathRight = ancestorNames(right, all).concat(right.name || '').join('\u0001')
    return pathLeft.localeCompare(pathRight, 'zh')
  })
})
const pageCount = computed(() => Math.max(1, Math.ceil(ordered.value.length / pageSize)))
const pageNumbers = computed(() => {
  const end = Math.min(pageCount.value, Math.max(listPage.value, 1) + 2)
  const from = Math.max(1, end - 4)
  const list = []
  for (let i = from; i <= Math.min(pageCount.value, from + 4); i += 1) list.push(i)
  return list
})
const pageRows = computed(() => {
  const start = (listPage.value - 1) * pageSize
  return ordered.value.slice(start, start + pageSize)
})
const formFields = computed(() => (page.value?.fields || []).filter((field) => field.key !== 'remark'))
const lineKeys = computed(() => {
  if (props.pageKey === 'ioBills') return ['warehouse', 'material', 'qty']
  if (props.pageKey === 'boms' && !editingId.value) return ['material', 'qty']
  return []
})
const editorFields = computed(() => formFields.value.filter((field) => !lineKeys.value.includes(field.key)))
const lineKind = computed(() => {
  if (props.pageKey === 'ioBills') return 'io'
  if (props.pageKey === 'boms' && !editingId.value) return 'bom'
  return ''
})
const moduleChoices = computed(() => (erpStore.meta || [])
  .filter((item) => item.code && item.code !== 'approvals')
  .slice()
  .sort((a, b) => `${a.module || ''}${a.title || ''}`.localeCompare(`${b.module || ''}${b.title || ''}`, 'zh')))
const sourceDocs = computed(() => {
  const table = form.source
  if (!table) return []
  return (erpStore.tables[table] || []).map((row) => ({ id: row.id, label: sourceDocLabel(table, row) }))
})
const tableColumns = computed(() => {
  const columns = page.value?.columns || []
  if (columns.some((column) => column.key === 'remark')) return columns
  if (!(page.value?.fields || []).some((field) => field.key === 'remark')) return columns
  const remark = { key: 'remark', label: '备注' }
  const status = columns.findIndex((column) => column.key === 'status')
  if (status < 0) return columns.concat([remark])
  return columns.slice(0, status).concat([remark], columns.slice(status))
})
const detailFields = computed(() => {
  const columns = tableColumns.value.filter((column) => column.key !== 'remark')
  const extra = formFields.value.filter((field) => !columns.some((column) => column.key === field.key) && field.key !== 'remark')
  return columns.concat(extra.map((field) => ({ key: field.key, label: field.label })))
})

watch(pageCount, (count) => {
  if (listPage.value > count) listPage.value = count
})

function localColumnsMatch(row) {
  return queryColumns.value.every((col) => {
    const needle = String(appliedColumns[col.key] || '').trim()
    return !needle || fieldText(row, col.key).includes(needle)
  })
}

async function search() {
  applied.keyword = query.keyword
  applied.status = query.status
  const columns = {}
  Object.keys(columnQuery).forEach((key) => {
    const text = String(columnQuery[key] || '').trim()
    if (text) columns[key] = text
  })
  Object.keys(appliedColumns).forEach((key) => delete appliedColumns[key])
  Object.assign(appliedColumns, columns)
  if (page.value?.table && Object.keys(columns).length) {
    const data = await queryDocs(page.value.table, columns)
    columnHits.value = data ? data.rows || [] : []
  } else {
    columnHits.value = null
  }
  listPage.value = 1
}

function resetQuery() {
  query.keyword = ''
  query.status = '全部'
  Object.keys(columnQuery).forEach((key) => { columnQuery[key] = '' })
  search()
}

function columnFilterActive() {
  return Object.keys(columnQuery).some((key) => String(columnQuery[key] || '').trim())
}

async function loadTable(table, requery) {
  const data = await queryDocs(table, {})
  if (!data?.rows) return
  erpStore.tables[table] = data.rows
  if (dialog.value === 'view' && props.pageKey === table && viewRow.value?.id) {
    const fresh = data.rows.find((item) => item.id === viewRow.value.id)
    if (fresh) viewRow.value = fresh
  }
  if (requery && props.pageKey === table && columnFilterActive()) await search()
}

watch(() => [props.pageKey, props.columnPreset && props.columnPreset.token], async () => {
  listPage.value = 1
  query.keyword = ''
  query.status = '全部'
  applied.keyword = ''
  applied.status = '全部'
  Object.keys(columnQuery).forEach((key) => delete columnQuery[key])
  Object.keys(appliedColumns).forEach((key) => delete appliedColumns[key])
  columnHits.value = null
  dialog.value = ''
  const reloadOnEnter = ['deliveries', 'receipts', 'ioBills', 'transfers', 'counts', 'checks', 'collects', 'payments', 'jobs', 'stock', 'attendance', 'leaves', 'payroll', 'approvals']
  if (reloadOnEnter.includes(props.pageKey)) await loadTable(props.pageKey, false)
  const preset = props.columnPreset
  if (preset && preset.page === props.pageKey && preset.columns) {
    Object.entries(preset.columns).forEach(([key, value]) => { columnQuery[key] = value })
    await search()
  }
}, { immediate: true })

const recordRefs = {
  depts: true, employees: true, materials: true, units: true, categories: true, warehouses: true, customers: true, suppliers: true,
  salesOrders: true, purchaseOrders: true, contracts: true, purchaseContracts: true,
  jobs: true, receivables: true, payables: true, payMethods: true, shifts: true, leaveTypes: true,
  supplierCategories: true, expenseTypes: true,
}

const inlineCreate = {
  units: true, categories: true, shifts: true, leaveTypes: true, payMethods: true,
  supplierCategories: true, expenseTypes: true,
}

function isRecordRef(field) {
  return !!(field.optionsFrom && recordRefs[field.optionsFrom])
}

function hiddenTreeNames(field) {
  if (!field.tree) return new Set()
  const hidden = new Set()
  if (editingId.value) hidden.add(editingId.value)
  const rows = erpStore.tables[field.optionsFrom] || []
  let grew = true
  while (grew) {
    grew = false
    for (const item of rows) {
      if (item.id && hidden.has(realParent(item.parent)) && !hidden.has(item.id)) {
        hidden.add(item.id)
        grew = true
      }
    }
  }
  return hidden
}

function valuesOf(value, field) {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean)
  if (value === undefined || value === null || value === '') return []
  const text = String(value).trim()
  if (field && (field.optionsFrom === 'skills' || field.multiple)) return text.split(/[,，]/).map((item) => item.trim()).filter(Boolean)
  return text ? [text] : []
}

function optionPairs(field) {
  if (!isRecordRef(field)) {
    const items = optionsOf(field).map((name) => ({ value: name, label: name }))
    const current = form[field.key]
    if (current && !Array.isArray(current) && !items.some((item) => item.value === String(current))) {
      items.push({ value: String(current), label: String(current) })
    }
    return items
  }
  const hidden = hiddenTreeNames(field)
  const items = []
  for (const item of erpStore.tables[field.optionsFrom] || []) {
    if (field.optionsStatus && item.status && item.status !== field.optionsStatus) continue
    if (hidden.has(item.id)) continue
    const codeFirst = field.optionsFrom === 'contracts' || field.optionsFrom === 'purchaseContracts' || field.optionsFrom === 'salesOrders' || field.optionsFrom === 'purchaseOrders'
    items.push({ value: item.id, label: codeFirst ? (item.code || item.name || item.id) : (item.name || item.code || item.id) })
  }
  valuesOf(form[field.key], field).forEach((id) => {
    if (!items.some((item) => item.value === id)) items.push({ value: id, label: refLabel(field.optionsFrom, id) })
  })
  return items
}

function choiceItems(field) {
  if (field.optionsFrom === 'skills') {
    const items = (erpStore.skills || []).map((item) => ({ value: item.id, label: item.name }))
    valuesOf(form[field.key], field).forEach((id) => {
      if (!items.some((item) => item.value === id)) items.push({ value: id, label: id })
    })
    return items
  }
  if (isRecordRef(field)) return optionPairs(field)
  return optionsOf(field).map((name) => ({ value: name, label: name }))
}

function skillText(value) {
  const ids = valuesOf(Array.isArray(value) ? value.join(',') : value, { optionsFrom: 'skills' })
  if (!ids.length) return ''
  return ids.map((id) => (erpStore.skills || []).find((item) => item.id === id)?.name || id).join('、')
}

function optionsOf(field) {
  if (field.options) return field.options
  const source = field.optionsFrom || field.suggestFrom
  if (!source) return []
  const key = field.optionsKey || field.suggestKey || 'name'
  const hidden = hiddenTreeNames(field)
  const names = []
  const push = (value) => {
    for (const item of valuesOf(value)) {
      if (!names.includes(item) && !hidden.has(item)) names.push(item)
    }
  }
  for (const item of erpStore.tables[source] || []) {
    if (field.optionsStatus && item.status && item.status !== field.optionsStatus) continue
    push(item[key])
  }
  push(form[field.key])
  return names
}

function addChoice(field) {
  const name = addedName.value.trim().slice(0, 32)
  addedName.value = ''
  if (!name) return
  const current = valuesOf(form[field.key])
  if (!current.includes(name)) form[field.key] = current.concat(name)
}

function realParent(value) {
  if (Array.isArray(value)) return realParent(value[0])
  if (!value || value === '—' || value === '-' || value === '无' || value === '无上级') return ''
  return value
}

function ancestorNames(row, rows) {
  const names = []
  let cursor = realParent(row.parent)
  const seen = new Set()
  while (cursor && !seen.has(cursor)) {
    seen.add(cursor)
    const up = rows.find((item) => item.id === cursor)
    names.unshift(up?.name || cursor)
    cursor = up ? realParent(up.parent) : ''
  }
  return names
}

function deptDepth(row) {
  if (props.pageKey !== 'depts') return 0
  return ancestorNames(row, sourceRows.value).length
}

function postText(value) {
  const ids = Array.isArray(value) ? value : String(value || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean)
  return ids.map((id) => (erpStore.posts || []).find((item) => item.id === id)?.name || id).filter(Boolean).join('、')
}

const pageRefs = {
  employees: { dept: 'depts' },
  attendance: { name: 'employees', dept: 'depts', shift: 'shifts' },
  leaves: { name: 'employees', type: 'leaveTypes' },
  payroll: { name: 'employees', dept: 'depts' },
  notices: { dept: 'depts' },
  assets: { dept: 'depts', keeper: 'employees' },
  depts: { parent: 'depts', manager: 'employees' },
  warehouses: { dept: 'depts', keeper: 'employees' },
  customers: { owner: 'employees' },
  contracts: { customer: 'customers', owner: 'employees' },
  salesOrders: { contract: 'contracts', customer: 'customers', material: 'materials', owner: 'employees' },
  deliveries: { order: 'salesOrders', customer: 'customers', material: 'materials', warehouse: 'warehouses' },
  suppliers: { owner: 'employees', category: 'supplierCategories' },
  purchaseContracts: { supplier: 'suppliers', owner: 'employees' },
  purchaseOrders: { contract: 'purchaseContracts', supplier: 'suppliers', material: 'materials', buyer: 'employees' },
  receipts: { order: 'purchaseOrders', supplier: 'suppliers', material: 'materials', warehouse: 'warehouses' },
  materials: { unit: 'units', category: 'categories' },
  stock: { material: 'materials', warehouse: 'warehouses' },
  ioBills: { warehouse: 'warehouses', material: 'materials' },
  transfers: { material: 'materials', from: 'warehouses', to: 'warehouses' },
  counts: { warehouse: 'warehouses', material: 'materials' },
  boms: { product: 'materials', material: 'materials' },
  jobs: { product: 'materials', owner: 'employees', workshop: 'depts', warehouse: 'warehouses' },
  checks: { job: 'jobs', material: 'materials', owner: 'employees' },
  receivables: { customer: 'customers', source: 'salesOrders' },
  payables: { supplier: 'suppliers', source: 'purchaseOrders' },
  collects: { bill: 'receivables', contract: 'contracts', customer: 'customers', method: 'payMethods' },
  payments: { bill: 'payables', contract: 'purchaseContracts', supplier: 'suppliers', method: 'payMethods' },
  expenses: { applicant: 'employees', type: 'expenseTypes' },
  approvals: { applicant: 'employees' },
}

function sourceDocLabel(table, row) {
  if (table === 'leaves') {
    const name = refLabel('employees', Array.isArray(row.name) ? row.name[0] : row.name)
    const kind = refLabel('leaveTypes', Array.isArray(row.type) ? row.type[0] : row.type)
    return [name, kind, row.start].filter(Boolean).join(' ')
  }
  if (row.code && row.name) return `${row.code} ${row.name}`
  if (row.code || row.name || row.title) return row.code || row.name || row.title
  if (row.material) return [refLabel('materials', Array.isArray(row.material) ? row.material[0] : row.material), refLabel('warehouses', Array.isArray(row.warehouse) ? row.warehouse[0] : row.warehouse)].filter(Boolean).join(' ')
  return refLabel(table, row.id) || row.id
}

function fieldText(row, key) {
  return String(cellText(row, { key }) ?? '')
}

const refByKey = {
  dept: 'depts', customer: 'customers', supplier: 'suppliers', material: 'materials', product: 'materials',
  warehouse: 'warehouses', from: 'warehouses', to: 'warehouses', owner: 'employees', buyer: 'employees',
  contract: 'contracts',
  keeper: 'employees', manager: 'employees', applicant: 'employees', workshop: 'depts',
}

function deptHeadcount(row) {
  if (!row) return 0
  if (!erpStore.tables.employees) return Number(row.people) || 0
  const ids = [row.id, row.name].filter(Boolean)
  return rowsOf('employees').filter((item) => (item.status || '在职') === '在职' && holdsToken(item.dept, ids)).length
}

function cellText(row, col) {
  if (col.key === 'available') return Number(row?.qty || 0) - Number(row?.hold || 0)
  if (props.pageKey === 'depts' && col.key === 'people') return deptHeadcount(row)
  if (props.pageKey === 'approvals' && col.key === 'sourceId') {
    const id = Array.isArray(row?.sourceId) ? row.sourceId.find((item) => item) : row?.sourceId
    if (!id) return ''
    const sourceRow = (erpStore.tables[row.source] || []).find((item) => item.id === id)
    return sourceRow ? sourceDocLabel(row.source, sourceRow) : String(id)
  }
  const value = row?.[col.key]
  if (col.key === 'skill') return skillText(value)
  if (col.key === 'post') return postText(value)
  const refTable = pageRefs[props.pageKey]?.[col.key] || refByKey[col.key]
  if (refTable) {
    if (Array.isArray(value)) return value.map((id) => refLabel(refTable, id)).filter(Boolean).join('、')
    if (value === '—' || value === '-' || value === '无' || value === '无上级') return ''
    return value ? refLabel(refTable, value) : ''
  }
  if (Array.isArray(value)) return value.join('、')
  if (col.key === 'parent' && (value === '—' || value === '-' || value === '无' || value === '无上级')) return ''
  return value ?? (col.num ? 0 : '')
}

function canEdit(row) {
  if (!page.value?.fields) return false
  if (!page.value.editWhen) return true
  return page.value.editWhen.includes(row.status)
}

async function refreshFiles(id) {
  files.value = id ? (await listFiles(page.value.table, id)) || [] : []
}

function blankLine() {
  return { material: [], warehouse: [], qty: 1, actual: 0 }
}

function seedMark(col) {
  const keys = tableColumns.value.map((item) => item.key)
  const anchor = keys.includes('name') ? 'name' : keys.includes('material') ? 'material' : keys.find((key) => key !== 'code') || keys[0]
  return col.key === anchor
}

function firstId(value) {
  if (Array.isArray(value)) return value.find((item) => item) || ''
  return value == null ? '' : String(value)
}

function stockCard(material, warehouse) {
  const rows = (erpStore.tables.stock || []).filter((item) => firstId(item.material) === material && firstId(item.warehouse) === warehouse)
  return rows.sort((a, b) => (Number(b.qty) || 0) - (Number(a.qty) || 0))[0]
}

function readonlyValue(field) {
  if (field.key === 'materialCode') return stockCard(firstId(form.material), firstId(form.from))?.code || ''
  if (field.key === 'book') {
    const row = stockCard(firstId(form.material), firstId(form.warehouse))
    return row ? Number(row.qty) || 0 : 0
  }
  if (field.key === 'balance') {
    const paid = props.pageKey === 'payables' ? form.paid : form.received
    return Math.round(((Number(form.amount) || 0) - (Number(paid) || 0)) * 100) / 100
  }
  if ((field.key === 'received' || field.key === 'paid') && (form[field.key] === '' || form[field.key] == null)) return 0
  if (props.pageKey === 'payroll' && field.key === 'amount') {
    const earned = Math.round(((Number(form.base) || 0) * (Number(form.attend) || 0)) / 22)
    return earned + (Number(form.extra) || 0)
  }
  return form[field.key] ?? ''
}

function rowById(table, value) {
  const id = firstId(value)
  if (!id) return null
  return (erpStore.tables[table] || []).find((item) => item.id === id || item.code === id) || null
}

function sameIds(left, right) {
  const one = idsOf(left).slice().sort()
  const two = idsOf(right).slice().sort()
  return one.length === two.length && one.every((item, index) => item === two[index])
}

function pullFinance() {
  if (dialog.value !== 'create') {
    pulledSource.value = ''
    pulledBill.value = ''
    return
  }
  if (props.pageKey === 'receivables' || props.pageKey === 'payables') {
    const sourceId = firstId(form.source)
    if (!sourceId || sourceId === pulledSource.value) return
    const keepStored = !!editingId.value && !pulledSource.value
    pulledSource.value = sourceId
    const order = rowById(props.pageKey === 'receivables' ? 'salesOrders' : 'purchaseOrders', form.source)
    if (!order || keepStored) return
    form.amount = Number(order.amount) || 0
    form[props.pageKey === 'receivables' ? 'customer' : 'supplier'] = valuesOf(order[props.pageKey === 'receivables' ? 'customer' : 'supplier'], { multiple: true })
    return
  }
  if (props.pageKey !== 'collects' && props.pageKey !== 'payments') return
  const billId = firstId(form.bill)
  if (!billId || billId === pulledBill.value) return
  pulledBill.value = billId
  const collect = props.pageKey === 'collects'
  const bill = rowById(collect ? 'receivables' : 'payables', form.bill)
  if (!bill) return
  const order = rowById(collect ? 'salesOrders' : 'purchaseOrders', bill.source)
  const party = collect ? 'customer' : 'supplier'
  if (order) {
    form[party] = valuesOf(order[party], { multiple: true })
    form.contract = valuesOf(order.contract, { multiple: true })
  } else {
    form[party] = valuesOf(bill[party], { multiple: true })
  }
  const balance = Number(bill.balance)
  form.amount = Number.isFinite(balance) ? balance : (Number(bill.amount) || 0)
}

function leaveDays(start, end) {
  const from = Date.parse(String(start || '').replace(' ', 'T'))
  const to = Date.parse(String(end || '').replace(' ', 'T'))
  if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) return null
  return Math.round(((to - from) / 86400000) * 100) / 100
}

function pullHr() {
  if (dialog.value !== 'create' && dialog.value !== 'edit') return
  if (props.pageKey === 'leaves') {
    const stamp = `${form.start || ''}|${form.end || ''}`
    const firstEdit = !!editingId.value && !leaveStamp.value
    if (stamp !== leaveStamp.value) {
      leaveStamp.value = stamp
      if (!firstEdit) {
        const days = leaveDays(form.start, form.end)
        if (days != null) form.days = days
      }
    }
  }
  if (props.pageKey !== 'attendance' && props.pageKey !== 'payroll') return
  const nameId = firstId(form.name)
  if (!nameId || nameId === pulledName.value) return
  const keepStored = !!editingId.value && !pulledName.value
  pulledName.value = nameId
  const person = rowById('employees', nameId)
  if (!person || keepStored) return
  form.dept = valuesOf(person.dept, { multiple: true })
  if (props.pageKey === 'payroll') form.base = Number(person.base) || 0
}

function settlementProblem(payload) {
  const collect = props.pageKey === 'collects'
  const bill = rowById(collect ? 'receivables' : 'payables', payload.bill)
  if (!bill) return ''
  const order = rowById(collect ? 'salesOrders' : 'purchaseOrders', bill.source)
  const party = collect ? 'customer' : 'supplier'
  let mismatch = false
  if (order) {
    if (!sameIds(payload[party], order[party])) mismatch = true
    if (!sameIds(payload.contract, order.contract)) mismatch = true
  } else if (!sameIds(payload[party], bill[party])) {
    mismatch = true
  }
  const duplicate = (erpStore.tables[props.pageKey] || []).some((item) => {
    if (editingId.value && item.id === editingId.value) return false
    const tokens = idsOf(item.bill)
    if (tokens.includes(bill.id) || (bill.code && tokens.includes(bill.code))) return true
    if (tokens.length) return false
    const party = collect ? 'customer' : 'supplier'
    return sameIds(item[party], bill[party]) && Math.abs((Number(item.amount) || 0) - (Number(bill.amount) || 0)) <= 0.001
  })
  const pending = (erpStore.tables.approvals || []).some((item) => item.source === props.pageKey && item.status === '待审批' && String(item.payload || '').includes(bill.id))
  const partyText = collect ? '客户或合同与订单不符' : '供应商或合同与订单不符'
  const againText = pending && !duplicate
    ? (collect ? '该应收账款的收款正在审批' : '该应付账款的付款正在审批')
    : (collect ? '该应收账款已有收款单' : '该应付账款已有付款单')
  if (mismatch && (duplicate || pending)) return `${partyText}，且${againText}`
  if (duplicate || pending) return againText
  if (mismatch) return partyText
  return ''
}

function ledgerProblem() {
  if (props.pageKey !== 'receivables' && props.pageKey !== 'payables') return ''
  const order = rowById(props.pageKey === 'receivables' ? 'salesOrders' : 'purchaseOrders', form.source)
  if (!order) return ''
  const receivable = props.pageKey === 'receivables'
  const amountDiff = Math.abs((Number(form.amount) || 0) - (Number(order.amount) || 0)) > 0.001
  let duplicate = false
  if (!editingId.value) {
    duplicate = (erpStore.tables[props.pageKey] || []).some((item) => {
      const tokens = idsOf(item.source)
      return tokens.includes(order.id) || (order.code && tokens.includes(order.code))
    })
  }
  const amountText = receivable ? '应收金额与订单不一致' : '应付金额与订单不一致'
  const againText = receivable ? '该订单已有应收账款' : '该订单已有应付账款'
  if (amountDiff && duplicate) return `${amountText}，且${againText}`
  if (duplicate) return againText
  if (amountDiff) return amountText
  return ''
}

function openInsist(message) {
  insistMessage.value = message
  insistStep.value = /请填写情况说明/.test(message) ? 'explain' : 'warn'
  insistHint.value = props.pageKey === 'receivables' || props.pageKey === 'payables'
    ? '审批通过后才会写入新的金额，驳回则保持原来的金额。'
    : '审批通过后才会生成单号并写入数据库，驳回则不生成。'
  dialog.value = 'insist'
}

function bookQty(material, warehouse) {
  const rows = (erpStore.tables.stock || []).filter((item) => firstId(item.material) === material && firstId(item.warehouse) === warehouse)
  return rows.reduce((max, item) => Math.max(max, Number(item.qty) || 0), 0)
}

function linesFrom(row) {
  if (Array.isArray(row?.lines) && row.lines.length) {
    return row.lines.map((line) => ({
      material: valuesOf(line.material, { multiple: true }),
      warehouse: valuesOf(line.warehouse, { multiple: true }),
      qty: Number(line.qty) || 1,
      actual: line.actual == null ? 0 : Number(line.actual),
    }))
  }
  const material = valuesOf(row?.material, { multiple: true })
  const warehouse = valuesOf(row?.warehouse, { multiple: true })
  if (!material.length && !warehouse.length) return [blankLine()]
  const book = bookQty(material[0], warehouse[0])
  return [{ material, warehouse, qty: Number(row?.qty) || 1, actual: book + Number(row?.diff || 0) }]
}

watch(() => [dialog.value, props.pageKey, firstId(form.source), firstId(form.bill), firstId(form.name), String(form.start || ''), String(form.end || '')], () => {
  pullFinance()
  pullHr()
})

function openCreate() {
  pulledSource.value = ''
  pulledBill.value = ''
  pulledName.value = ''
  leaveStamp.value = ''
  editingId.value = ''
  files.value = []
  pendingFiles.value = []
  formLines.value = lineKind.value ? [blankLine()] : []
  Object.keys(form).forEach((key) => delete form[key])
  page.value.fields.forEach((field) => {
    const options = optionsOf(field)
    if (field.readonly) form[field.key] = ''
    else if (field.optionsFrom === 'posts' || field.optionsFrom === 'skills' || (field.type === 'select' && isRecordRef(field)) || field.multiple) form[field.key] = []
    else if (field.type === 'number') form[field.key] = field.key === 'actual' ? '' : (field.default !== undefined ? field.default : 1)
    else if (field.type === 'select' || field.suggestFrom) form[field.key] = options.includes(field.default) ? field.default : (field.clearable ? '' : (options[0] || ''))
    else if (field.default !== undefined) form[field.key] = field.default
    else form[field.key] = ''
  })
  formError.value = ''
  dialog.value = 'create'
}

function openEdit(row) {
  pulledSource.value = ''
  pulledName.value = ''
  leaveStamp.value = ''
  pendingFiles.value = []
  Object.keys(form).forEach((key) => delete form[key])
  page.value.fields.forEach((field) => {
    const value = row[field.key]
    const blankParent = field.key === 'parent' && (value === '—' || value === '-' || value === '无' || value === '无上级')
    const record = field.optionsFrom === 'posts' || field.optionsFrom === 'skills' || isRecordRef(field) || field.multiple
    form[field.key] = record ? valuesOf(blankParent ? '' : value, field) : (value === undefined || value === null || blankParent ? '' : value)
  })
  editingId.value = row.id
  pulledBill.value = firstId(row.bill)
  formLines.value = lineKind.value ? linesFrom(row) : []
  formError.value = ''
  dialog.value = 'create'
  refreshFiles(row.id)
}

function viewTables(key) {
  const packs = {
    customers: ['deliveries'],
    contracts: ['deliveries', 'salesOrders'],
    salesOrders: ['deliveries'],
    deliveries: ['deliveries'],
    suppliers: ['receipts'],
    purchaseContracts: ['receipts', 'purchaseOrders'],
    purchaseOrders: ['receipts'],
    receipts: ['receipts'],
    materials: ['stock', 'ioBills', 'transfers', 'counts'],
    stock: ['ioBills', 'transfers', 'counts'],
    ioBills: ['stock'],
    transfers: ['stock'],
    counts: ['stock'],
    boms: ['jobs', 'checks', 'stock'],
    jobs: ['checks', 'stock', 'boms'],
    checks: ['jobs', 'stock', 'boms'],
    receivables: ['collects', 'salesOrders'],
    payables: ['payments', 'purchaseOrders'],
    collects: ['receivables'],
    payments: ['payables'],
    expenses: ['employees'],
    employees: ['attendance', 'leaves', 'payroll'],
    attendance: ['employees'],
    leaves: ['employees'],
    payroll: ['employees', 'attendance'],
    depts: ['employees', 'warehouses', 'stock', 'assets', 'notices', 'attendance', 'leaves', 'payroll', 'jobs', 'expenses'],
    warehouses: ['stock', 'ioBills', 'transfers', 'counts'],
    assets: ['depts', 'employees'],
    notices: ['depts'],
  }
  return packs[key] || []
}

async function openView(row) {
  viewRow.value = row
  dialog.value = 'view'
  refreshFiles(row.id)
  for (const table of viewTables(props.pageKey)) await loadTable(table, false)
}

function tokenList(value) {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean)
  if (value == null || value === '') return []
  return String(value).split(/[,，]/).map((item) => item.trim()).filter(Boolean)
}

function holdsToken(value, ids) {
  return tokenList(value).some((token) => ids.includes(token))
}

function rowsOf(table) {
  return erpStore.tables[table] || []
}

function relatedGroup(key, title, rows, query) {
  const columns = resolvePage(key)?.columns || []
  const listed = columns.some((col) => col.key === 'code') ? columns : [{ key: 'code', label: '单号' }].concat(columns)
  return { key, title, rows, columns: listed, query }
}

const relatedGroups = computed(() => {
  const row = viewRow.value
  if (!row?.id) return []
  if (props.pageKey === 'customers') {
    const match = (item) => holdsToken(item.customer, [row.id, row.name])
    return [
      relatedGroup('contracts', '销售合同', rowsOf('contracts').filter(match), { customer: row.id }),
      relatedGroup('salesOrders', '销售订单', rowsOf('salesOrders').filter(match), { customer: row.id }),
      relatedGroup('deliveries', '发货单', rowsOf('deliveries').filter(match), { customer: row.id }),
    ]
  }
  if (props.pageKey === 'contracts') {
    const orders = rowsOf('salesOrders').filter((item) => holdsToken(item.contract, [row.id, row.code]))
    const keys = orders.flatMap((item) => [item.id, item.code]).filter(Boolean)
    const deliveries = rowsOf('deliveries').filter((item) => holdsToken(item.order, keys))
    return [
      relatedGroup('salesOrders', '销售订单', orders, { contract: row.id }),
      relatedGroup('deliveries', '发货单', deliveries, { order: orders.map((item) => item.id).join(',') }),
    ]
  }
  if (props.pageKey === 'salesOrders') {
    const contractIds = tokenList(row.contract)
    const contracts = rowsOf('contracts').filter((item) => contractIds.includes(item.id) || contractIds.includes(item.code))
    const deliveries = rowsOf('deliveries').filter((item) => holdsToken(item.order, [row.id, row.code]))
    return [
      relatedGroup('contracts', '销售合同', contracts, { id: contractIds.join(',') }),
      relatedGroup('deliveries', '发货单', deliveries, { order: row.id }),
    ]
  }
  if (props.pageKey === 'deliveries') {
    const orderIds = tokenList(row.order)
    const orders = rowsOf('salesOrders').filter((item) => orderIds.includes(item.id) || orderIds.includes(item.code))
    const contractIds = [...new Set(orders.flatMap((item) => tokenList(item.contract)))]
    const contracts = rowsOf('contracts').filter((item) => contractIds.includes(item.id) || contractIds.includes(item.code))
    return [
      relatedGroup('contracts', '销售合同', contracts, { id: contractIds.join(',') }),
      relatedGroup('salesOrders', '销售订单', orders, { id: orderIds.join(',') }),
    ]
  }
  if (props.pageKey === 'suppliers') {
    const match = (item) => holdsToken(item.supplier, [row.id, row.name])
    return [
      relatedGroup('purchaseContracts', '采购合同', rowsOf('purchaseContracts').filter(match), { supplier: row.id }),
      relatedGroup('purchaseOrders', '采购订单', rowsOf('purchaseOrders').filter(match), { supplier: row.id }),
      relatedGroup('receipts', '收货单', rowsOf('receipts').filter(match), { supplier: row.id }),
    ]
  }
  if (props.pageKey === 'purchaseContracts') {
    const orders = rowsOf('purchaseOrders').filter((item) => holdsToken(item.contract, [row.id, row.code]))
    const keys = orders.flatMap((item) => [item.id, item.code]).filter(Boolean)
    const receipts = rowsOf('receipts').filter((item) => holdsToken(item.order, keys))
    return [
      relatedGroup('purchaseOrders', '采购订单', orders, { contract: row.id }),
      relatedGroup('receipts', '收货单', receipts, { order: orders.map((item) => item.id).join(',') }),
    ]
  }
  if (props.pageKey === 'purchaseOrders') {
    const contractIds = tokenList(row.contract)
    const contracts = rowsOf('purchaseContracts').filter((item) => contractIds.includes(item.id) || contractIds.includes(item.code))
    const receipts = rowsOf('receipts').filter((item) => holdsToken(item.order, [row.id, row.code]))
    return [
      relatedGroup('purchaseContracts', '采购合同', contracts, { id: contractIds.join(',') }),
      relatedGroup('receipts', '收货单', receipts, { order: row.id }),
    ]
  }
  if (props.pageKey === 'receipts') {
    const orderIds = tokenList(row.order)
    const orders = rowsOf('purchaseOrders').filter((item) => orderIds.includes(item.id) || orderIds.includes(item.code))
    const contractIds = [...new Set(orders.flatMap((item) => tokenList(item.contract)))]
    const contracts = rowsOf('purchaseContracts').filter((item) => contractIds.includes(item.id) || contractIds.includes(item.code))
    return [
      relatedGroup('purchaseContracts', '采购合同', contracts, { id: contractIds.join(',') }),
      relatedGroup('purchaseOrders', '采购订单', orders, { id: orderIds.join(',') }),
    ]
  }
  if (props.pageKey === 'materials') {
    const ids = [row.id, row.name, row.code].filter(Boolean)
    const query = { material: row.id }
    return [
      relatedGroup('stock', '即时库存', rowsOf('stock').filter((item) => holdsToken(item.material, ids)), query),
      relatedGroup('ioBills', '出入库', rowsOf('ioBills').filter((item) => holdsToken(item.material, ids)), query),
      relatedGroup('transfers', '调拨', rowsOf('transfers').filter((item) => holdsToken(item.material, ids)), query),
      relatedGroup('counts', '盘点', rowsOf('counts').filter((item) => holdsToken(item.material, ids)), query),
    ]
  }
  if (props.pageKey === 'stock') {
    const materials = tokenList(row.material)
    const houses = tokenList(row.warehouse)
    const query = { material: materials.join(','), warehouse: houses.join(',') }
    return [
      relatedGroup('ioBills', '出入库', rowsOf('ioBills').filter((item) => holdsToken(item.material, materials) && holdsToken(item.warehouse, houses)), query),
      relatedGroup('transfers', '调拨', rowsOf('transfers').filter((item) => holdsToken(item.material, materials) && (holdsToken(item.from, houses) || holdsToken(item.to, houses))), { material: materials.join(',') }),
      relatedGroup('counts', '盘点', rowsOf('counts').filter((item) => holdsToken(item.material, materials) && holdsToken(item.warehouse, houses)), query),
    ]
  }
  if (props.pageKey === 'ioBills' || props.pageKey === 'counts') {
    const materials = tokenList(row.material)
    const houses = tokenList(row.warehouse)
    return [
      relatedGroup('stock', '即时库存', rowsOf('stock').filter((item) => holdsToken(item.material, materials) && (!houses.length || holdsToken(item.warehouse, houses))), { material: materials.join(',') }),
    ]
  }
  if (props.pageKey === 'transfers') {
    const materials = tokenList(row.material)
    const houses = tokenList(row.from).concat(tokenList(row.to))
    return [
      relatedGroup('stock', '即时库存', rowsOf('stock').filter((item) => holdsToken(item.material, materials) && holdsToken(item.warehouse, houses)), { material: materials.join(',') }),
    ]
  }
  if (props.pageKey === 'boms') {
    const parents = tokenList(row.product)
    const children = tokenList(row.material)
    const materials = parents.concat(children)
    return [
      relatedGroup('jobs', '生产工单', rowsOf('jobs').filter((item) => holdsToken(item.product, parents)), { product: parents.join(',') }),
      relatedGroup('checks', '质检单', rowsOf('checks').filter((item) => holdsToken(item.material, materials)), { material: parents.join(',') }),
      relatedGroup('stock', '即时库存', rowsOf('stock').filter((item) => holdsToken(item.material, materials)), { material: materials.join(',') }),
    ]
  }
  if (props.pageKey === 'jobs') {
    const products = tokenList(row.product)
    return [
      relatedGroup('boms', '物料清单', rowsOf('boms').filter((item) => holdsToken(item.product, products)), { product: products.join(',') }),
      relatedGroup('checks', '质检单', rowsOf('checks').filter((item) => holdsToken(item.job, [row.id, row.code]) || holdsToken(item.material, products)), { job: row.id }),
      relatedGroup('stock', '即时库存', rowsOf('stock').filter((item) => holdsToken(item.material, products)), { material: products.join(',') }),
    ]
  }
  if (props.pageKey === 'checks') {
    const jobs = tokenList(row.job)
    const materials = tokenList(row.material)
    const matchedJobs = rowsOf('jobs').filter((item) => jobs.includes(item.id) || jobs.includes(item.code) || holdsToken(item.product, materials))
    return [
      relatedGroup('jobs', '生产工单', matchedJobs, { id: (jobs.length ? jobs : matchedJobs.map((item) => item.id)).join(',') }),
      relatedGroup('boms', '物料清单', rowsOf('boms').filter((item) => holdsToken(item.product, materials) || holdsToken(item.material, materials)), { product: materials.join(',') }),
      relatedGroup('stock', '即时库存', rowsOf('stock').filter((item) => holdsToken(item.material, materials)), { material: materials.join(',') }),
    ]
  }
  if (props.pageKey === 'receivables') {
    const customers = tokenList(row.customer)
    const sources = tokenList(row.source)
    return [
      relatedGroup('salesOrders', '销售订单', rowsOf('salesOrders').filter((item) => sources.includes(item.id) || sources.includes(item.code)), { id: sources.join(',') }),
      relatedGroup('collects', '收款单', rowsOf('collects').filter((item) => holdsToken(item.bill, [row.id]) || holdsToken(item.customer, customers)), { customer: customers.join(',') }),
    ]
  }
  if (props.pageKey === 'payables') {
    const suppliers = tokenList(row.supplier)
    const sources = tokenList(row.source)
    return [
      relatedGroup('purchaseOrders', '采购订单', rowsOf('purchaseOrders').filter((item) => sources.includes(item.id) || sources.includes(item.code)), { id: sources.join(',') }),
      relatedGroup('payments', '付款单', rowsOf('payments').filter((item) => holdsToken(item.bill, [row.id]) || holdsToken(item.supplier, suppliers)), { supplier: suppliers.join(',') }),
    ]
  }
  if (props.pageKey === 'collects') {
    const bills = tokenList(row.bill)
    const customers = tokenList(row.customer)
    const matched = rowsOf('receivables').filter((item) => bills.includes(item.id) || holdsToken(item.customer, customers))
    return [
      relatedGroup('receivables', '应收账款', matched, bills.length ? { id: bills.join(',') } : { customer: customers.join(',') }),
    ]
  }
  if (props.pageKey === 'payments') {
    const bills = tokenList(row.bill)
    const suppliers = tokenList(row.supplier)
    const matched = rowsOf('payables').filter((item) => bills.includes(item.id) || holdsToken(item.supplier, suppliers))
    return [
      relatedGroup('payables', '应付账款', matched, bills.length ? { id: bills.join(',') } : { supplier: suppliers.join(',') }),
    ]
  }
  if (props.pageKey === 'expenses') {
    const people = tokenList(row.applicant)
    return [
      relatedGroup('employees', '员工档案', rowsOf('employees').filter((item) => people.includes(item.id) || people.includes(item.name)), { id: people.join(',') }),
    ]
  }
  if (props.pageKey === 'employees') {
    const ids = [row.id, row.name].filter(Boolean)
    const query = { name: row.id }
    return [
      relatedGroup('attendance', '考勤记录', rowsOf('attendance').filter((item) => holdsToken(item.name, ids)), query),
      relatedGroup('leaves', '请假申请', rowsOf('leaves').filter((item) => holdsToken(item.name, ids)), query),
      relatedGroup('payroll', '工资表', rowsOf('payroll').filter((item) => holdsToken(item.name, ids)), query),
    ]
  }
  if (props.pageKey === 'attendance' || props.pageKey === 'leaves' || props.pageKey === 'payroll') {
    const people = tokenList(row.name)
    return [
      relatedGroup('employees', '员工档案', rowsOf('employees').filter((item) => people.includes(item.id) || people.includes(item.name)), { id: people.join(',') }),
    ]
  }
  if (props.pageKey === 'depts') {
    const ids = [row.id, row.name].filter(Boolean)
    const staff = rowsOf('employees').filter((item) => (item.status || '在职') === '在职' && holdsToken(item.dept, ids))
    const staffIds = staff.flatMap((item) => [item.id, item.name]).filter(Boolean)
    const houses = rowsOf('warehouses').filter((item) => holdsToken(item.dept, ids))
    const houseIds = houses.flatMap((item) => [item.id, item.name]).filter(Boolean)
    const onPerson = (item) => holdsToken(item.name, staffIds) || holdsToken(item.applicant, staffIds)
    return [
      relatedGroup('employees', '人员', staff, { dept: row.id }),
      relatedGroup('warehouses', '仓库', houses, { dept: row.id }),
      relatedGroup('stock', '物品', rowsOf('stock').filter((item) => holdsToken(item.warehouse, houseIds)), { warehouse: houseIds.join(',') }),
      relatedGroup('assets', '固定资产', rowsOf('assets').filter((item) => holdsToken(item.dept, ids)), { dept: row.id }),
      relatedGroup('attendance', '考勤记录', rowsOf('attendance').filter((item) => holdsToken(item.dept, ids) || holdsToken(item.name, staffIds)), { dept: row.id }),
      relatedGroup('leaves', '请假申请', rowsOf('leaves').filter(onPerson), { name: staff.map((item) => item.id).join(',') }),
      relatedGroup('payroll', '工资表', rowsOf('payroll').filter((item) => holdsToken(item.dept, ids) || holdsToken(item.name, staffIds)), { dept: row.id }),
      relatedGroup('jobs', '生产工单', rowsOf('jobs').filter((item) => holdsToken(item.workshop, ids)), { workshop: row.id }),
      relatedGroup('expenses', '费用报销', rowsOf('expenses').filter(onPerson), { applicant: staff.map((item) => item.id).join(',') }),
      relatedGroup('notices', '公告', rowsOf('notices').filter((item) => holdsToken(item.dept, ids)), { dept: row.id }),
      relatedGroup('depts', '下级部门', rowsOf('depts').filter((item) => item.id !== row.id && holdsToken(item.parent, ids)), { parent: row.id }),
    ]
  }
  if (props.pageKey === 'warehouses') {
    const ids = [row.id, row.name].filter(Boolean)
    const query = { warehouse: row.id }
    return [
      relatedGroup('stock', '即时库存', rowsOf('stock').filter((item) => holdsToken(item.warehouse, ids)), query),
      relatedGroup('ioBills', '出入库', rowsOf('ioBills').filter((item) => holdsToken(item.warehouse, ids)), query),
      relatedGroup('counts', '盘点', rowsOf('counts').filter((item) => holdsToken(item.warehouse, ids)), query),
    ]
  }
  if (props.pageKey === 'assets') {
    const depts = tokenList(row.dept)
    const keepers = tokenList(row.keeper)
    return [
      relatedGroup('depts', '部门', rowsOf('depts').filter((item) => depts.includes(item.id) || depts.includes(item.name)), { id: depts.join(',') }),
      relatedGroup('employees', '员工档案', rowsOf('employees').filter((item) => keepers.includes(item.id) || keepers.includes(item.name)), { id: keepers.join(',') }),
    ]
  }
  if (props.pageKey === 'notices') {
    const depts = tokenList(row.dept)
    return [
      relatedGroup('depts', '部门', rowsOf('depts').filter((item) => depts.includes(item.id) || depts.includes(item.name)), { id: depts.join(',') }),
    ]
  }
  return []
})

function relatedText(groupKey, row, col) {
  if (col.key === 'available') return Number(row?.qty || 0) - Number(row?.hold || 0)
  if (groupKey === 'depts' && col.key === 'people') return deptHeadcount(row)
  if (col.money) return money(row[col.key])
  const value = row[col.key]
  const refTable = pageRefs[groupKey]?.[col.key] || refByKey[col.key]
  if (refTable) {
    if (Array.isArray(value)) return value.map((id) => refLabel(refTable, id)).filter(Boolean).join('、')
    if (!value || value === '—' || value === '-' || value === '无') return ''
    return refLabel(refTable, value)
  }
  if (Array.isArray(value)) return value.join('、')
  return value ?? (col.num ? 0 : '')
}

function queryOf(group) {
  const columns = {}
  Object.entries(group.query || {}).forEach(([key, value]) => {
    const text = String(value || '').trim()
    if (text) columns[key] = text
  })
  return columns
}

function openRelated(group) {
  const columns = queryOf(group)
  if (!Object.keys(columns).length) return
  dialog.value = ''
  emit('open', { page: group.key, columns })
}

const shrinkFields = computed(() => {
  const entity = (erpStore.meta || []).find((item) => item.code === page.value?.table)
  return (entity?.fields || []).filter((field) => !['id', 'origin', 'code', 'status'].includes(field.key))
})

function openShrink() {
  formError.value = ''
  dialog.value = 'shrink'
}

function openField() {
  fieldForm.key = ''
  fieldForm.label = ''
  fieldForm.type = 'string'
  formError.value = ''
  dialog.value = 'field'
}

const queryLabels = computed(() => ({
  id: '单据',
  order: props.pageKey === 'receipts' ? '采购订单' : '销售订单',
  contract: props.pageKey === 'purchaseOrders' ? '采购合同' : '销售合同',
  job: '生产工单',
  bill: props.pageKey === 'payments' ? '应付账款' : '应收账款',
  name: '员工',
  product: '产品',
  material: '物料',
  source: '来源单据',
  applicant: '申请人',
  parent: '上级',
  dept: '部门',
  warehouse: '仓库',
  workshop: '车间',
}))

const extraQueryColumns = computed(() => {
  const known = new Set(queryColumns.value.map((col) => col.key))
  return Object.keys(columnQuery)
    .filter((key) => !known.has(key) && String(columnQuery[key] || '').trim())
    .map((key) => ({ key, label: queryLabels.value[key] || key }))
})

function linkIndex(action, row) {
  const source = action.valueKey ? row[action.valueKey] : row.id
  if (Array.isArray(source)) return source.map((item) => String(item).trim()).filter(Boolean).join(',')
  return source == null ? '' : String(source).trim()
}

function runAction(action, row) {
  if (action.go) {
    const column = action.column || 'contract'
    emit('open', { page: action.go, columns: { [column]: linkIndex(action, row) } })
    return
  }
  ask(action, row)
}

function ask(action, row) {
  pending.value = { action, row }
  confirmError.value = ''
  dialog.value = 'confirm'
}

function rowActions(row) {
  return (page.value.actions || []).filter((action) => {
    if (action.when && !action.when.includes(row.status)) return false
    if (action.manualOnly && !['入库', '出库', '其他入库', '其他出库'].includes(row.type)) return false
    return true
  })
}

function idsOf(value) {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean)
  if (value == null || value === '') return []
  return [String(value).trim()]
}

function expandLines(lines) {
  const out = []
  lines.forEach((line) => {
    const materials = idsOf(line.material)
    const houses = idsOf(line.warehouse)
    materials.forEach((material, index) => {
      out.push({
        material,
        warehouse: houses.length ? houses[Math.min(index, houses.length - 1)] : '',
        qty: Number(line.qty) || 0,
        actual: line.actual == null || line.actual === '' ? 0 : Number(line.actual),
      })
    })
  })
  return out
}

async function submitCreate() {
  formError.value = ''
  for (const field of page.value.fields) {
    if (lineKeys.value.includes(field.key)) continue
    const value = form[field.key]
    if (field.required && (value === '' || value === null || value === undefined || (Array.isArray(value) && !value.length))) {
      formError.value = `请填写${field.label}`
      return
    }
    if (field.required && field.type === 'number' && !(Number(value) > 0)) {
      formError.value = `请填写${field.label}`
      return
    }
  }
  const payload = {}
  page.value.fields.forEach((field) => {
    if (lineKeys.value.includes(field.key)) return
    if (Array.isArray(form[field.key])) payload[field.key] = form[field.key]
    else payload[field.key] = field.type === 'number' ? Number(form[field.key]) : String(form[field.key] ?? '').trim()
  })
  if (props.pageKey === 'transfers' && !readonlyValue({ key: 'materialCode' })) {
    formError.value = '该仓库没有这个物料，不能调拨'
    return
  }
  if (props.pageKey === 'counts' && (form.actual === '' || form.actual == null || Number.isNaN(Number(form.actual)))) {
    formError.value = '请填写实际数量'
    return
  }
  const review = ledgerProblem() || (!editingId.value && (props.pageKey === 'collects' || props.pageKey === 'payments') ? settlementProblem(payload) : '')
  if (review) {
    if (!forceSettlement.value) {
      insistExplain.value = ''
      openInsist(review)
      return
    }
    if (!insistExplain.value.trim()) {
      formError.value = '请填写情况说明'
      insistStep.value = 'explain'
      dialog.value = 'insist'
      return
    }
    payload.insist = true
    payload.explain = insistExplain.value.trim()
  }
  if (lineKind.value === 'bom') {
    const lines = []
    for (const line of formLines.value) {
      const materials = idsOf(line.material)
      const qty = Number(line.qty)
      if (!materials.length) continue
      if (!(qty > 0)) {
        formError.value = '请填写数量'
        return
      }
      materials.forEach((material) => lines.push({ material, qty }))
    }
    if (!lines.length) {
      formError.value = '请填写材料'
      return
    }
    payload.lines = lines
  } else if (lineKind.value) {
    const lines = expandLines(formLines.value)
    if (!lines.length) {
      formError.value = '请填写物料'
      return
    }
    if (lineKind.value !== 'transfer' && lines.some((line) => !line.warehouse)) {
      formError.value = '请填写仓库'
      return
    }
    if (lineKind.value !== 'count' && lines.some((line) => !(line.qty > 0))) {
      formError.value = '请填写数量'
      return
    }
    payload.lines = lines
    payload.material = [...new Set(lines.map((line) => line.material))]
    const houses = [...new Set(lines.map((line) => line.warehouse).filter(Boolean))]
    if (houses.length) payload.warehouse = houses
    payload.qty = lines.reduce((sum, line) => sum + Number(line.qty || 0), 0) || 1
  }
  const data = editingId.value
    ? await updateDoc(page.value.table, editingId.value, payload)
    : await createDoc(page.value.table, payload)
  forceSettlement.value = false
  const notice = String(erpStore.notice || '')
  const reviewable = /不符|已有收款单|已有付款单|已有应收账款|已有应付账款|金额与订单不一致|请填写情况说明|正在审批/.test(notice) && !notice.startsWith('已')
  if (reviewable && (ledgerProblem() || !editingId.value)) {
    openInsist(notice)
    return
  }
  if (data && typeof erpStore.notice === 'string' && erpStore.notice.startsWith('已')) {
    const savedId = editingId.value || erpStore.tables[page.value.table]?.[0]?.id
    for (const file of pendingFiles.value) {
      if (savedId) await uploadFile(page.value.table, savedId, file)
    }
    pendingFiles.value = []
    dialog.value = ''
    if (page.value.table === 'deliveries' || page.value.table === 'receipts') await loadTable(page.value.table, true)
    return
  }
  formError.value = erpStore.notice || '请求失败'
}

async function pickFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    formError.value = '单个文件不能超过 2MB'
    return
  }
  if (editingId.value) {
    const saved = await uploadFile(page.value.table, editingId.value, file)
    if (saved) await refreshFiles(editingId.value)
    else formError.value = erpStore.notice || '文件没有保存'
    return
  }
  pendingFiles.value.push(file)
}

async function dropFile(file) {
  if (file.local) {
    pendingFiles.value = pendingFiles.value.filter((item) => item !== file)
    return
  }
  const removed = await removeFile(page.value.table, editingId.value || viewRow.value?.id, file.id)
  if (removed) await refreshFiles(editingId.value || viewRow.value?.id)
}

async function dropField(field) {
  formError.value = ''
  const data = await removeField(page.value.table, field.key)
  if (data) return
  formError.value = erpStore.notice || '请求失败'
}

async function submitField() {
  formError.value = ''
  if (!/^[a-z][a-zA-Z0-9]{0,31}$/.test(fieldForm.key)) {
    formError.value = '字段编码需以小写字母开头'
    return
  }
  if (!fieldForm.label.trim()) {
    formError.value = '请填写字段名称'
    return
  }
  const data = await addField(page.value.table, { key: fieldForm.key, label: fieldForm.label.trim(), type: fieldForm.type })
  if (data) dialog.value = ''
  else formError.value = erpStore.notice || '请求失败'
}

async function submitConfirm() {
  const current = pending.value
  if (!current) return
  confirmError.value = ''
  const table = props.pageKey
  const id = current.row.id
  const action = current.action.key
  const data = await actDoc(table, id, action)
  if (data && typeof erpStore.notice === 'string' && erpStore.notice.startsWith('已')) {
    dialog.value = ''
    if (table === 'deliveries' || action === 'pushDelivery' || (action === 'undoPush' && table === 'salesOrders')) await loadTable('deliveries', true)
    if (table === 'receipts' || action === 'pushReceipt' || action === 'receive' || action === 'unreceive' || (action === 'undoPush' && table === 'purchaseOrders')) await loadTable('receipts', true)
    if (table === 'checks' || action === 'pushCheck' || (action === 'undoPush' && table === 'jobs') || (action === 'finish' && table === 'jobs')) await loadTable('checks', true)
    if (columnHits.value) await search()
    return
  }
  confirmError.value = erpStore.notice || '请求失败'
}

function backFromInsist() {
  forceSettlement.value = false
  formError.value = ''
  dialog.value = 'create'
}

async function submitInsist() {
  if (!insistExplain.value.trim()) {
    formError.value = '请填写情况说明'
    insistStep.value = 'explain'
    return
  }
  forceSettlement.value = true
  formError.value = ''
  dialog.value = 'create'
  await submitCreate()
}

function noticeError(text) {
  return /不能|不足|没有|只有|请填写|已有|不存在/.test(text)
}
</script>

<template>
  <h1 class="np-page-title">{{ page.title }}</h1>
  <form class="np-filter" @submit.prevent="search">
    <label>
      关键词
      <input v-model="query.keyword" placeholder="全量查询" />
    </label>
    <label v-for="field in (page.filters || []).filter((item) => item.key === 'status')" :key="field.key">
      {{ field.label }}
      <select v-model="query.status">
        <option v-for="option in field.options" :key="option" :value="option">{{ option }}</option>
      </select>
    </label>
    <label v-for="col in queryColumns" :key="col.key">
      {{ col.label }}
      <input v-model="columnQuery[col.key]" placeholder="列查询" />
    </label>
    <label v-for="col in extraQueryColumns" :key="col.key">
      {{ col.label }}
      <input v-model="columnQuery[col.key]" placeholder="列查询" />
    </label>
    <div class="np-filter-actions">
      <button type="submit" class="np-btn np-btn-primary">查询</button>
      <button type="button" class="np-btn" @click="resetQuery">重置</button>
    </div>
  </form>

  <div v-if="erpStore.notice" class="np-alert" :class="noticeError(erpStore.notice) ? 'np-alert-error' : 'np-alert-success'">
    <span>{{ erpStore.notice }}</span>
    <button type="button" aria-label="关闭提示" @click="erpStore.notice = ''">×</button>
  </div>

  <section class="np-card">
    <div class="np-card-bar">
      <div class="np-card-meta">共 {{ filtered.length }} 条</div>
      <div class="np-card-actions">
        <button v-if="page.table" type="button" class="np-btn" @click="openField">添加字段</button>
        <button v-if="page.table" type="button" class="np-btn" @click="openShrink">缩减字段</button>
        <button v-if="page.fields" type="button" class="np-btn np-btn-primary" @click="openCreate">新建</button>
      </div>
    </div>
    <div class="np-table-wrap">
      <table class="np-table">
        <thead>
          <tr>
            <th v-for="col in tableColumns" :key="col.key" :class="{ 'np-num': col.num }">{{ col.label }}</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!pageRows.length" class="np-empty">
            <td :colspan="tableColumns.length + 1">暂无数据</td>
          </tr>
          <tr v-for="row in pageRows" :key="row.id">
            <td v-for="(col, index) in tableColumns" :key="col.key" :class="{ 'np-num': col.num, 'np-remark-cell': col.key === 'remark' }" :title="col.key === 'remark' ? row.remark : undefined">
              <span v-if="col.tag" :class="tagClass(row[col.key])">{{ row[col.key] }}</span>
              <template v-else-if="col.money">{{ money(row[col.key]) }}</template>
              <span v-else :style="deptDepth(row) && col.key === 'name' ? { paddingLeft: `${deptDepth(row) * 16}px` } : undefined">{{ cellText(row, col) }}</span>
              <span v-if="row.origin === 'seed' && seedMark(col)" class="np-tag">演示</span>
            </td>
            <td class="np-ops">
              <button type="button" class="np-link" @click="openView(row)">查看</button>
              <button v-if="canEdit(row)" type="button" class="np-link" @click="openEdit(row)">修改</button>
              <button
                v-for="action in rowActions(row)"
                :key="action.key"
                type="button"
                :class="action.danger ? 'np-link-danger' : 'np-link'"
                @click="runAction(action, row)"
              >
                {{ action.label }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="np-pager">
      <span>共 {{ filtered.length }} 条</span>
      <button type="button" :disabled="listPage === 1" @click="listPage -= 1">上一页</button>
      <button v-for="number in pageNumbers" :key="number" type="button" :class="{ 'is-current': number === listPage }" @click="listPage = number">{{ number }}</button>
      <button type="button" :disabled="listPage === pageCount" @click="listPage += 1">下一页</button>
    </div>
  </section>

  <Teleport to="body">
    <div v-if="dialog === 'create'" class="np-modal" @click.self="dialog = ''">
      <div class="np-dialog" :class="{ 'is-wide': lineKind }" role="dialog" aria-modal="true" :aria-label="`${editingId ? '修改' : '新建'}${page.title}`">
        <div class="np-dialog-h">
          <span>{{ editingId ? '修改' : '新建' }}{{ page.title }}</span>
          <button type="button" aria-label="关闭" @click="dialog = ''">×</button>
        </div>
        <div class="np-dialog-b">
          <p v-if="formError" class="np-form-error">{{ formError }}</p>
          <p v-if="pageKey === 'stock'" class="np-form-note">一行对应一个物料和一个仓库。同一仓库同一物料已有库存时不能再建，请直接修改。现存数量和隔离数量由收发货、出入库、调拨、盘点和生产完工写入。</p>
          <p v-if="pageKey === 'boms'" class="np-form-note">一行对应一个产品和一种材料，每种材料单独填写数量。已经有的产品和材料会跳过。</p>
          <p v-if="pageKey === 'transfers'" class="np-form-note">先选择调出仓库和物料，系统按这笔库存锁定物料编码，再填写数量和调入仓库。</p>
          <p v-if="pageKey === 'counts'" class="np-form-note">选择仓库和物料后带出应有数量。实际数量一致时直接完成；不一致时交给审批中心，同意后才写入库存，驳回则不改库存。</p>
          <p v-if="pageKey === 'approvals'" class="np-form-note">先选板块，再选这个板块里的一张单据。同意或驳回只写入审批结果，具体怎么改业务数据由后端按板块继续处理。</p>
          <p v-if="pageKey === 'receivables'" class="np-form-note">选择来源单据后带出应收金额，金额仍可修改。与订单金额不同，或这个订单已经有应收账款时，填写情况说明后送审核，通过后才写入并生成单号。余额由应收金额减已收得出，不能手改。</p>
          <p v-if="pageKey === 'payables'" class="np-form-note">选择来源单据后带出应付金额，金额仍可修改。与订单金额不同，或这个订单已经有应付账款时，填写情况说明后送审核，通过后才写入并生成单号。余额由应付金额减已付得出，不能手改。</p>
          <p v-if="pageKey === 'collects'" class="np-form-note">一张应收账款只能开一张收款单。选择单号后带出客户和销售合同。再次建立，或与订单不一致时，先确认再填写情况说明，审批通过后才生成收款单号。</p>
          <p v-if="pageKey === 'payments'" class="np-form-note">一张应付账款只能开一张付款单。选择单号后带出供应商和采购合同。再次建立，或与订单不一致时，先确认再填写情况说明，审批通过后才生成付款单号。</p>
          <p v-if="pageKey === 'attendance'" class="np-form-note">选择姓名后带出部门，部门仍可修改。班次从班次档案选择。上班时间和下班时间默认为空。</p>
          <p v-if="pageKey === 'leaves'" class="np-form-note">类型从请假类型档案选择。天数按开始和结束时间自动计算，仍可修改。保存后送入审批中心，同意后才写入不可工作时段，驳回则不生效。</p>
          <p v-if="pageKey === 'payroll'" class="np-form-note">选择姓名后带出部门和基本工资。其他工资填正数增加、负数扣减。实际发放按基本工资×出勤天数/22，再加上其他工资。</p>
          <p v-if="pageKey === 'depts'" class="np-form-note">人数按在职员工统计。仓库、物品、考勤、请假、工资、资产、公告和生产工单在查看时从对应板块读取，不在部门里另行保存。</p>
          <div class="np-form-grid">
            <StockLines v-if="lineKind === 'io'" v-model:lines="formLines" :kind="lineKind" />
            <div v-else-if="lineKind === 'bom'" class="np-lines">
              <div v-for="(line, index) in formLines" :key="index" class="np-line">
                <label>
                  <span><i class="np-req">*</i>材料</span>
                  <RefSelect v-model="line.material" multiple table="materials" placeholder="请选择" />
                </label>
                <label>
                  <span><i class="np-req">*</i>数量</span>
                  <input v-model.number="line.qty" class="np-control" type="number" />
                </label>
                <button type="button" class="np-link np-link-danger" @click="formLines.splice(index, 1)">删除</button>
              </div>
              <button type="button" class="np-btn" @click="formLines.push(blankLine())">添加材料</button>
            </div>
            <label v-for="field in editorFields" :key="field.key" class="np-field" :class="{ 'np-span-2': field.type === 'text' }">
              <span><i v-if="field.required" class="np-req">*</i>{{ field.label }}</span>
              <SkillSelect v-if="field.optionsFrom === 'skills'" v-model="form[field.key]" multiple :skills="erpStore.skills" />
              <SkillSelect v-else-if="field.optionsFrom === 'posts'" v-model="form[field.key]" kind="post" multiple :posts="erpStore.posts" />
              <RefSelect
                v-else-if="isRecordRef(field)"
                v-model="form[field.key]"
                multiple
                :table="field.optionsFrom"
                :status="field.optionsStatus || ''"
                :clearable="!!field.clearable"
                :creatable="!!inlineCreate[field.optionsFrom]"
                :exclude="[...hiddenTreeNames(field)]"
                :placeholder="field.key === 'parent' ? '无上级' : '请选择'"
              />
              <div v-else-if="field.multiple" class="np-checks">
                <div v-for="option in choiceItems(field)" :key="option.value" class="np-check">
                  <input v-model="form[field.key]" type="checkbox" :value="option.value" />
                  <span>{{ option.label }}</span>
                </div>
                <input v-if="field.allowAdd" v-model="addedName" class="np-control" placeholder="新技能" @keydown.enter.prevent="addChoice(field)" />
              </div>
              <select v-else-if="field.type === 'module'" v-model="form[field.key]" class="np-control" @change="form.sourceId = ''">
                <option value="">请选择板块</option>
                <option v-for="item in moduleChoices" :key="item.code" :value="item.code">{{ item.module }} · {{ item.title }}</option>
              </select>
              <select v-else-if="field.type === 'sourceDoc'" v-model="form[field.key]" class="np-control">
                <option value="">可不选来源单据</option>
                <option v-for="item in sourceDocs" :key="item.id" :value="item.id">{{ item.label }}</option>
              </select>
              <select v-else-if="field.type === 'select'" v-model="form[field.key]" class="np-control">
                <option v-if="field.clearable" value="">{{ field.key === 'parent' ? '无上级' : '请选择' }}</option>
                <option v-for="option in optionPairs(field)" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <input v-else-if="field.readonly" class="np-control" readonly :value="readonlyValue(field)" />
              <input v-else-if="field.type === 'number'" v-model.number="form[field.key]" class="np-control" type="number" />
              <textarea v-else-if="field.type === 'text'" v-model="form[field.key]" class="np-control np-span-2" rows="4" :placeholder="field.placeholder || ''" />
              <template v-else-if="field.suggestFrom">
                <input v-model="form[field.key]" class="np-control" :list="`${page.table}-${field.key}`" />
                <datalist :id="`${page.table}-${field.key}`">
                  <option v-for="option in optionsOf(field)" :key="option" :value="option" />
                </datalist>
              </template>
              <input v-else v-model="form[field.key]" class="np-control" />
            </label>
            <div class="np-field np-span-2">
              <span>附件</span>
              <input class="np-control" type="file" @change="pickFile" />
              <div class="np-file-list">
                <div v-for="file in files" :key="file.id">
                  <button type="button" class="np-link" @click="downloadFile(page.table, editingId, file)">{{ file.name }}</button>
                  <button type="button" class="np-link np-link-danger" @click="dropFile(file)">删除</button>
                </div>
                <div v-for="(file, index) in pendingFiles" :key="index">
                  {{ file.name }}
                  <button type="button" class="np-link np-link-danger" @click="pendingFiles.splice(index, 1)">删除</button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <label v-if="page.fields?.some((field) => field.key === 'remark')" class="np-remark-bar">
          <span>备注</span>
          <textarea v-model="form.remark" class="np-control" rows="2" placeholder="请输入备注" />
        </label>
        <div class="np-dialog-f">
          <button type="button" class="np-btn" @click="dialog = ''">取消</button>
          <button type="button" class="np-btn np-btn-primary" @click="submitCreate">确定</button>
        </div>
      </div>
    </div>
    <div v-else-if="dialog === 'confirm'" class="np-modal" @click.self="dialog = ''">
      <div class="np-dialog" role="dialog" aria-modal="true" :aria-label="pending?.action.label">
        <div class="np-dialog-h">
          <span>{{ pending?.action.label }}</span>
          <button type="button" aria-label="关闭" @click="dialog = ''">×</button>
        </div>
        <div class="np-dialog-b">
          <p v-if="confirmError" class="np-form-error">{{ confirmError }}</p>
          确认{{ pending?.action.label }}「{{ rowLabel(pending?.row || {}) }}」？
        </div>
        <div class="np-dialog-f">
          <button type="button" class="np-btn" @click="dialog = ''">取消</button>
          <button type="button" class="np-btn np-btn-primary" @click="submitConfirm">确定</button>
        </div>
      </div>
    </div>
    <div v-else-if="dialog === 'insist'" class="np-modal" @click.self="backFromInsist">
      <div class="np-dialog" role="dialog" aria-modal="true" aria-label="需要审批">
        <div class="np-dialog-h">
          <span>需要审批</span>
          <button type="button" aria-label="关闭" @click="backFromInsist">×</button>
        </div>
        <div class="np-dialog-b">
          <p v-if="formError" class="np-form-error">{{ formError }}</p>
          <p>{{ insistMessage }}</p>
          <p v-if="insistStep === 'warn'">确认后请填写情况说明，再送入审核中心。</p>
          <label v-else class="np-field">
            <span><i class="np-req">*</i>情况说明</span>
            <textarea v-model="insistExplain" class="np-control" rows="4" maxlength="800" placeholder="请说明为什么需要再次建立或修改金额" />
            <p>{{ insistHint }}</p>
          </label>
        </div>
        <div class="np-dialog-f">
          <button type="button" class="np-btn" @click="backFromInsist">返回修改</button>
          <button v-if="insistStep === 'warn'" type="button" class="np-btn np-btn-primary" @click="insistStep = 'explain'">确认</button>
          <button v-else type="button" class="np-btn np-btn-primary" @click="submitInsist">送入审核</button>
        </div>
      </div>
    </div>
    <div v-else-if="dialog === 'field'" class="np-modal" @click.self="dialog = ''">
      <div class="np-dialog" role="dialog" aria-modal="true" aria-label="添加字段">
        <div class="np-dialog-h">
          <span>添加字段</span>
          <button type="button" aria-label="关闭" @click="dialog = ''">×</button>
        </div>
        <div class="np-dialog-b">
          <p v-if="formError" class="np-form-error">{{ formError }}</p>
          <div class="np-form-grid">
            <label class="np-field"><span><i class="np-req">*</i>字段编码</span><input v-model="fieldForm.key" class="np-control" placeholder="如 note" /></label>
            <label class="np-field"><span><i class="np-req">*</i>字段名称</span><input v-model="fieldForm.label" class="np-control" placeholder="请输入" /></label>
            <label class="np-field"><span>类型</span>
              <select v-model="fieldForm.type" class="np-control">
                <option value="string">文本</option>
                <option value="text">长文本</option>
                <option value="number">数字</option>
                <option value="date">日期</option>
              </select>
            </label>
          </div>
        </div>
        <div class="np-dialog-f">
          <button type="button" class="np-btn" @click="dialog = ''">取消</button>
          <button type="button" class="np-btn np-btn-primary" @click="submitField">确定</button>
        </div>
      </div>
    </div>
    <div v-else-if="dialog === 'shrink'" class="np-modal" @click.self="dialog = ''">
      <div class="np-dialog" role="dialog" aria-modal="true" aria-label="缩减字段">
        <div class="np-dialog-h">
          <span>缩减字段</span>
          <button type="button" aria-label="关闭" @click="dialog = ''">×</button>
        </div>
        <div class="np-dialog-b">
          <p v-if="formError" class="np-form-error">{{ formError }}</p>
          <p>只能删除还没有填写内容的字段。编号和状态会保留。</p>
          <div v-if="!shrinkFields.length">当前没有可删除的字段。</div>
          <div v-else class="np-file-list">
            <div v-for="field in shrinkFields" :key="field.key">
              <span>{{ field.label }}</span>
              <button type="button" class="np-link np-link-danger" @click="dropField(field)">删除</button>
            </div>
          </div>
        </div>
        <div class="np-dialog-f">
          <button type="button" class="np-btn" @click="dialog = ''">关闭</button>
        </div>
      </div>
    </div>
    <div v-else-if="dialog === 'view'" class="np-modal is-drawer" @click.self="dialog = ''">
      <aside class="np-drawer" :class="{ 'is-related': relatedGroups.length }" role="dialog" aria-modal="true" aria-label="查看">
        <div class="np-drawer-h">
          <span>查看{{ page.title }}</span>
          <button type="button" aria-label="关闭" @click="dialog = ''">×</button>
        </div>
        <div class="np-drawer-b">
          <div v-if="page.fields?.some((field) => field.key === 'remark')" class="np-remark-read">
            <span>备注</span>
            <p>{{ viewRow?.remark || '无' }}</p>
          </div>
          <dl class="np-detail">
            <template v-for="col in detailFields" :key="col.key">
              <dt>{{ col.label }}</dt>
              <dd>{{ col.money ? money(viewRow?.[col.key]) : cellText(viewRow, col) }}</dd>
            </template>
          </dl>
          <section v-if="Array.isArray(viewRow?.lines) && viewRow.lines.length" class="np-related">
            <div class="np-related-h"><span>明细（{{ viewRow.lines.length }}）</span></div>
            <div class="np-table-wrap">
              <table class="np-table">
                <thead>
                  <tr>
                    <th>物料</th>
                    <th>仓库</th>
                    <th>{{ pageKey === 'counts' ? '实盘' : '数量' }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(line, index) in viewRow.lines" :key="index">
                    <td>{{ refLabel('materials', line.material) }}</td>
                    <td>{{ refLabel('warehouses', line.warehouse) }}</td>
                    <td>{{ pageKey === 'counts' ? line.actual : line.qty }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <div class="np-file-list">
            <div v-for="file in files" :key="file.id">
              <button type="button" class="np-link" @click="downloadFile(page.table, viewRow?.id, file)">{{ file.name }}</button>
            </div>
          </div>
          <section v-for="group in relatedGroups" :key="group.key" class="np-related">
            <div class="np-related-h">
              <span>{{ group.title }}（{{ group.rows.length }}）</span>
              <button v-if="Object.keys(queryOf(group)).length" type="button" class="np-link" @click="openRelated(group)">跳转</button>
            </div>
            <div class="np-table-wrap">
              <table class="np-table">
                <thead>
                  <tr>
                    <th v-for="col in group.columns" :key="col.key">{{ col.label }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="!group.rows.length" class="np-empty">
                    <td :colspan="group.columns.length">暂无</td>
                  </tr>
                  <tr v-for="row in group.rows" :key="row.id">
                    <td v-for="col in group.columns" :key="col.key">
                      <span v-if="col.tag" :class="tagClass(row[col.key])">{{ row[col.key] }}</span>
                      <template v-else>{{ relatedText(group.key, row, col) }}</template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </aside>
    </div>
  </Teleport>
</template>
