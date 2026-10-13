<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import NpFrame from './NpFrame.vue'
import SkillSelect from './SkillSelect.vue'
import { loadErp } from './erp/api'
import { erpStore } from './erp/model'
import {
  assignManual,
  cancelAssign,
  createCustom,
  createFromTemplate,
  completeTask,
  deleteTask,
  deptName,
  loadAdmin,
  noticeTone,
  npState,
  publishOpen,
  publishTask,
  revertTask,
  runSolve,
  setDept,
  staffName,
  startTask,
  statusText,
  updateTask,
  visibleTasks,
} from './npState'

const props = defineProps({
  bare: { type: Boolean, default: false },
  section: { type: String, default: 'tasks' },
})

const menus = [
  { key: 'tasks', label: '任务管理' },
  { key: 'loads', label: '人员负载' },
]
const page = ref(props.section === 'loads' ? 'loads' : 'tasks')
const frameBind = computed(() => {
  if (props.bare) return { class: 'np-erp-pane' }
  return { role: 'admin', menus, active: page.value, userName: '管理员' }
})

watch(() => props.section, (value) => {
  page.value = value === 'loads' ? 'loads' : 'tasks'
})
const loading = ref(true)
const listPage = ref(1)
const pageSize = 10
const selected = ref([])
const solveTasks = ref([])
const solveStaff = ref([])
const solveError = ref('')
const dialog = ref('')
const createTab = ref('once')
const deleteTarget = ref(null)
const revertTarget = ref(null)
const unassignTask = ref(null)
const unassignStaffId = ref('')
const assignTask = ref(null)
const personOpen = ref(false)
const focusStaffId = ref('s2')

const query = reactive({ keyword: '', status: 'all', deptId: 'east' })
const applied = reactive({ keyword: '', status: 'all', deptId: 'east' })

const templateForm = reactive({
  deptId: 'east',
  startDate: '2026-03-03',
  endDate: '2026-03-05',
  staffDept: ['east'],
  preferOwn: '否',
  items: [{ name: '', startClock: '', endClock: '', headcount: 1, skill: [], priority: 2 }],
})
const customForm = reactive({
  name: '',
  deptId: 'east',
  startText: '',
  endText: '',
  headcount: 1,
  skill: [],
  staffDept: ['east'],
  preferOwn: '否',
  priority: 2,
})
const editForm = reactive({
  id: '',
  name: '',
  deptId: 'east',
  startText: '',
  endText: '',
  headcount: 1,
  skill: [],
  staffDept: ['east'],
  preferOwn: '否',
  priority: 2,
})
const loadQuery = ref('')
const personWork = ref([])
let skipEditDept = 0
let skipDailyDept = 0
const manual = reactive({ taskId: '', staffId: '' })

const tasks = computed(() => visibleTasks())
const openTasks = computed(() => tasks.value.filter((item) => item.status === 'open'))
const filtered = computed(() => tasks.value.filter((item) => {
  if (applied.status !== 'all' && item.status !== applied.status) return false
  if (applied.keyword && !item.name.includes(applied.keyword)) return false
  return true
}))
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const pageNumbers = computed(() => {
  const end = Math.min(pageCount.value, Math.max(listPage.value, 1) + 2)
  const from = Math.max(1, end - 4)
  const list = []
  for (let i = from; i <= Math.min(pageCount.value, from + 4); i += 1) list.push(i)
  return list
})
const pageRows = computed(() => {
  const start = (listPage.value - 1) * pageSize
  return filtered.value.slice(start, start + pageSize)
})
const pageOpenIds = computed(() => pageRows.value.filter((item) => item.status === 'open').map((item) => item.id))
const allChecked = computed(() => pageOpenIds.value.length > 0 && pageOpenIds.value.every((id) => selected.value.includes(id)))
const loads = computed(() => {
  const word = loadQuery.value.trim()
  return npState.loads.filter((row) => !word || row.name.includes(word) || deptName(row.deptId).includes(word))
})
const dialogTitle = computed(() => {
  if (dialog.value === 'edit') return '修改任务'
  if (dialog.value === 'assign') return '指派人员'
  if (dialog.value === 'unassign') return '取消指派'
  if (dialog.value === 'revert') return '退回任务'
  if (dialog.value === 'delete') return '删除任务'
  if (dialog.value === 'solve') return '选择员工范围'
  return '新建任务'
})
const solvePeople = computed(() => npState.staff.filter((person) => person.maxMinutes > 0))
function skillName(id) {
  return (npState.skills || []).find((item) => item.id === id)?.name || id
}
function splitIds(value) {
  if (Array.isArray(value)) return value.filter(Boolean)
  return String(value || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean)
}
function toggleDept(list, id, checked) {
  if (checked) {
    if (!list.includes(id)) list.push(id)
  } else {
    const next = list.filter((item) => item !== id)
    list.splice(0, list.length, ...next)
  }
}
function skillText(value) {
  return String(value || '').split(/[,，]/).map((id) => id.trim()).filter(Boolean).map(skillName).join('、')
}
function daySkillId() {
  return (npState.skills || []).find((item) => item.name === '白班')?.id || ''
}
function peopleForTask(task) {
  const listed = splitIds(task?.staffDept)
  return npState.staff.filter((person) => {
    if (person.maxMinutes <= 0) return false
    if (!listed.length) return person.deptId === task?.deptId
    return listed.includes(person.deptId)
  })
}
watch(() => customForm.deptId, (id, prev) => {
  if (!id || id === prev) return
  customForm.staffDept = [id]
})
watch(() => editForm.deptId, (id, prev) => {
  if (skipEditDept > 0) {
    skipEditDept -= 1
    return
  }
  if (!id || id === prev) return
  editForm.staffDept = [id]
})
watch(() => templateForm.deptId, (id, prev) => {
  if (skipDailyDept > 0) {
    skipDailyDept -= 1
    return
  }
  if (!id || id === prev) return
  templateForm.staffDept = [id]
})
const revertText = computed(() => {
  const item = revertTarget.value
  if (!item) return ''
  if (item.status === 'published') return `确认撤回「${item.name}」的发布？任务回到待分配，员工将暂时看不到。`
  if (item.status === 'running') return `确认将「${item.name}」退回已发布？`
  if (item.status === 'done') return `确认将「${item.name}」退回正在执行？`
  return ''
})

watch(filtered, () => {
  if (listPage.value > pageCount.value) listPage.value = pageCount.value
})

onMounted(async () => {
  await loadAdmin()
  loading.value = false
})

function namesOf(taskId) {
  return npState.assignments
    .filter((item) => item.taskId === taskId)
    .map((item) => staffName(item.staffId))
    .join('、') || '—'
}

function seated(taskId) {
  return npState.assignments.filter((item) => item.taskId === taskId).length
}

function assigneesOf(taskId) {
  return npState.assignments
    .filter((item) => item.taskId === taskId)
    .map((item) => ({ staffId: item.staffId, name: staffName(item.staffId) }))
}

function accepted(text) {
  return typeof text === 'string' && text.startsWith('已')
}

function tagClass(status) {
  if (status === 'published') return 'np-tag np-tag-published'
  if (status === 'running') return 'np-tag np-tag-running'
  if (status === 'done') return 'np-tag np-tag-done'
  return 'np-tag'
}

function search() {
  applied.keyword = query.keyword.trim()
  applied.status = query.status
  applied.deptId = query.deptId
  setDept(query.deptId)
  selected.value = []
  listPage.value = 1
}

function resetQuery() {
  query.keyword = ''
  query.status = 'all'
  query.deptId = 'east'
  search()
}

function toggleTask(id, checked) {
  if (checked) {
    if (!selected.value.includes(id)) selected.value.push(id)
  } else {
    selected.value = selected.value.filter((item) => item !== id)
  }
}

function togglePage(checked) {
  if (checked) {
    pageOpenIds.value.forEach((id) => {
      if (!selected.value.includes(id)) selected.value.push(id)
    })
    return
  }
  selected.value = selected.value.filter((id) => !pageOpenIds.value.includes(id))
}

function closeDialog() {
  dialog.value = ''
}

function openSolve(taskIds) {
  solveTasks.value = taskIds.slice()
  solveError.value = ''
  const dept = npState.deptId
  solveStaff.value = npState.staff
    .filter((person) => person.maxMinutes > 0 && (dept === 'all' || person.deptId === dept))
    .map((person) => person.id)
  dialog.value = 'solve'
}

function toggleSolveStaff(id, checked) {
  if (checked) {
    if (!solveStaff.value.includes(id)) solveStaff.value.push(id)
  } else {
    solveStaff.value = solveStaff.value.filter((item) => item !== id)
  }
}

async function submitSolve() {
  solveError.value = ''
  if (!solveStaff.value.length) {
    solveError.value = '请选择可参与排班的员工'
    return
  }
  const data = await runSolve(solveTasks.value, solveStaff.value.slice())
  const text = npState.notice || ''
  if (data && (accepted(text) || text.startsWith('有任务'))) {
    dialog.value = ''
    return
  }
  solveError.value = text || '请求失败'
}

function openCreate() {
  createTab.value = 'once'
  const day = daySkillId()
  const dept = npState.deptId && npState.deptId !== 'all' ? npState.deptId : (npState.depts[0]?.id || 'east')
  customForm.name = ''
  customForm.deptId = dept
  customForm.startText = ''
  customForm.endText = ''
  customForm.headcount = 1
  customForm.skill = day ? [day] : []
  customForm.staffDept = [dept]
  customForm.preferOwn = '否'
  customForm.priority = 2
  if (templateForm.deptId !== dept) skipDailyDept = 1
  templateForm.deptId = dept
  templateForm.staffDept = [dept]
  templateForm.preferOwn = '否'
  templateForm.items.forEach((item) => {
    if (!splitIds(item.skill).length && day) item.skill = [day]
  })
  dialog.value = 'create'
}

function addDailyTask() {
  const skill = daySkillId()
  templateForm.items.push({ name: '', startClock: '', endClock: '', headcount: 1, skill: skill ? [skill] : [], priority: 2 })
}

function removeDailyTask(index) {
  if (templateForm.items.length === 1) return
  templateForm.items.splice(index, 1)
}

async function submitOnce() {
  const data = await createCustom(customForm)
  if (data) dialog.value = ''
}

async function submitDaily() {
  const data = await createFromTemplate(templateForm)
  if (data) dialog.value = ''
}

function openEdit(item) {
  editForm.id = item.id
  editForm.name = item.name
  if (editForm.deptId !== item.deptId) skipEditDept = 1
  editForm.deptId = item.deptId
  editForm.startText = item.startText
  editForm.endText = item.endText
  editForm.headcount = item.headcount
  editForm.skill = splitIds(item.skill)
  editForm.staffDept = splitIds(item.staffDept || item.deptId)
  editForm.preferOwn = item.preferOwn === '是' ? '是' : '否'
  editForm.priority = item.priority
  dialog.value = 'edit'
}

async function submitEdit() {
  const data = await updateTask(editForm.id, editForm)
  if (data) dialog.value = ''
}

function openDelete(item) {
  deleteTarget.value = item
  dialog.value = 'delete'
}

async function submitDelete() {
  const id = deleteTarget.value.id
  const data = await deleteTask(id)
  if (!data) return
  selected.value = selected.value.filter((item) => item !== id)
  dialog.value = ''
}

function openAssign(item) {
  assignTask.value = item
  manual.taskId = item.id
  manual.staffId = ''
  dialog.value = 'assign'
}

async function submitAssign() {
  if (!manual.staffId) return
  const data = await assignManual(manual.taskId, manual.staffId)
  if (data) dialog.value = ''
}

function submitDialog() {
  if (dialog.value === 'edit') return submitEdit()
  if (dialog.value === 'assign') return submitAssign()
  if (dialog.value === 'unassign') return submitUnassign()
  if (createTab.value === 'daily') return submitDaily()
  return submitOnce()
}

async function submitPublish(id) {
  await publishTask(id)
}

function openRevert(item) {
  revertTarget.value = item
  dialog.value = 'revert'
}

async function submitRevert() {
  const data = await revertTask(revertTarget.value.id)
  if (data && accepted(npState.notice)) dialog.value = ''
}

function openUnassign(item) {
  unassignTask.value = item
  unassignStaffId.value = ''
  dialog.value = 'unassign'
}

async function submitUnassign() {
  if (!unassignStaffId.value) return
  const data = await cancelAssign(unassignTask.value.id, unassignStaffId.value)
  if (data && accepted(npState.notice)) dialog.value = ''
}

const workLinks = [
  ['customers', 'owner', '客户', '负责人'],
  ['contracts', 'owner', '销售合同', '负责人'],
  ['salesOrders', 'owner', '销售订单', '负责人'],
  ['suppliers', 'owner', '供应商', '采购员'],
  ['purchaseContracts', 'owner', '采购合同', '采购员'],
  ['purchaseOrders', 'buyer', '采购订单', '采购员'],
  ['jobs', 'owner', '生产工单', '负责人'],
  ['checks', 'owner', '质检单', '负责人'],
  ['expenses', 'applicant', '费用', '申请人'],
  ['approvals', 'applicant', '审批', '申请人'],
  ['assets', 'keeper', '资产', '保管人'],
  ['warehouses', 'keeper', '仓库', '仓管'],
  ['depts', 'manager', '部门', '负责人'],
  ['attendance', 'name', '考勤', '本人'],
  ['leaves', 'name', '请假', '本人'],
  ['payroll', 'name', '工资', '本人'],
]
function holdsRef(value, id) {
  return splitIds(value).includes(id)
}
function buildWork(staffId) {
  const person = npState.staff.find((item) => item.id === staffId)
  const employee = (erpStore.tables.employees || []).find((row) => row.staffId === staffId || row.name === person?.name)
  const keys = [employee?.id, staffId].filter(Boolean)
  const tasks = npState.assignments.filter((row) => row.staffId === staffId).map((row) => {
    const task = npState.tasks.find((item) => item.id === row.taskId)
    if (!task) return null
    return {
      kind: '任务',
      title: task.name,
      role: row.source === 'manual' ? '手动指派' : '算法分配',
      status: statusText(task.status),
      detail: `${deptName(task.deptId)} ${task.startText} ~ ${task.endText}`,
    }
  }).filter(Boolean)
  const docs = []
  for (const [table, field, title, role] of workLinks) {
    for (const row of erpStore.tables[table] || []) {
      if (!keys.some((id) => holdsRef(row[field], id))) continue
      docs.push({
        kind: title,
        title: row.code || row.name || row.title || row.id,
        role,
        status: row.status || '',
        detail: [row.date, row.month, row.amount].filter((value) => value !== undefined && value !== null && value !== '').join(' '),
      })
    }
  }
  return tasks.concat(docs)
}
async function openPerson(id) {
  focusStaffId.value = id
  personOpen.value = true
  await loadErp()
  personWork.value = buildWork(id)
}
</script>

<template>
  <component :is="bare ? 'div' : NpFrame" v-bind="frameBind" @navigate="page = $event">
    <template v-if="page === 'tasks'">
      <h1 class="np-page-title">任务管理</h1>
      <form class="np-filter" @submit.prevent="search">
        <label>任务名称<input v-model="query.keyword" placeholder="请输入" /></label>
        <label>部门
          <select v-model="query.deptId">
            <option value="all">全部部门</option>
            <option v-for="dept in npState.depts" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </label>
        <label>状态
          <select v-model="query.status">
            <option value="all">全部</option>
            <option value="open">待分配</option>
            <option value="published">已发布</option>
            <option value="running">正在执行</option>
            <option value="done">已完成</option>
          </select>
        </label>
        <div class="np-filter-actions">
          <button type="submit" class="np-btn np-btn-primary">查询</button>
          <button type="button" class="np-btn" @click="resetQuery">重置</button>
        </div>
      </form>

      <div v-if="npState.notice" class="np-alert" :class="noticeTone(npState.notice) === 'error' ? 'np-alert-error' : 'np-alert-success'">
        <span>{{ npState.notice }}</span>
        <button type="button" aria-label="关闭提示" @click="npState.notice = ''">×</button>
      </div>
      <div v-if="npState.gaps.length" class="np-alert np-alert-error">
        <span>{{ npState.gaps.join('；') }}</span>
      </div>

      <section class="np-card">
        <div class="np-card-bar">
          <div class="np-card-meta">
            共 {{ filtered.length }} 条
            <template v-if="selected.length">，已选 {{ selected.length }} 项</template>
            ，待分配 {{ openTasks.length }}，人数不足 {{ npState.gaps.length }}，工时方差 {{ npState.variance }}
          </div>
          <div class="np-card-actions">
            <button type="button" class="np-btn np-btn-primary" @click="openCreate">新建任务</button>
            <button type="button" class="np-btn" :disabled="!selected.length" @click="openSolve(selected.slice())">分配所选</button>
            <button type="button" class="np-btn" @click="openSolve([])">全量分配</button>
            <button type="button" class="np-btn" @click="publishOpen">发布</button>
          </div>
        </div>
        <div class="np-table-wrap">
          <table class="np-table">
            <thead>
              <tr>
                <th class="np-check">
                  <input type="checkbox" aria-label="选择本页待分配任务" :checked="allChecked" :disabled="!pageOpenIds.length" @change="togglePage($event.target.checked)" />
                </th>
                <th>任务名称</th>
                <th>申请部门</th>
                <th>开始时间</th>
                <th>结束时间</th>
                <th class="np-num">人数</th>
                <th>技能</th>
                <th class="np-num">优先级</th>
                <th>状态</th>
                <th>人员</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading || !pageRows.length" class="np-empty">
                <td colspan="11">{{ loading ? '加载中…' : '暂无数据' }}</td>
              </tr>
              <tr v-for="item in pageRows" :key="item.id">
                <td class="np-check">
                  <input
                    v-if="item.status === 'open'"
                    type="checkbox"
                    :checked="selected.includes(item.id)"
                    :aria-label="'选择 ' + item.name"
                    @change="toggleTask(item.id, $event.target.checked)"
                  />
                </td>
                <td>{{ item.name }}</td>
                <td>{{ deptName(item.deptId) }}</td>
                <td>{{ item.startText }}</td>
                <td>{{ item.endText }}</td>
                <td class="np-num">{{ seated(item.id) }} / {{ item.headcount }}</td>
                <td>{{ skillText(item.skill) }}</td>
                <td class="np-num">{{ item.priority }}</td>
                <td><span :class="tagClass(item.status)">{{ statusText(item.status) }}</span></td>
                <td>{{ namesOf(item.id) }}</td>
                <td>
                  <div class="np-ops">
                    <button v-if="item.status === 'open' && seated(item.id) >= item.headcount" type="button" class="np-link" @click="submitPublish(item.id)">发布</button>
                    <button v-if="item.status === 'open'" type="button" class="np-link" @click="openAssign(item)">指派</button>
                    <button v-if="seated(item.id)" type="button" class="np-link" @click="openUnassign(item)">取消指派</button>
                    <button v-if="item.status !== 'open'" type="button" class="np-link" @click="openRevert(item)">退回</button>
                    <button v-if="item.status === 'published'" type="button" class="np-link" @click="startTask(item.id)">开始执行</button>
                    <button v-if="item.status === 'running'" type="button" class="np-link" @click="completeTask(item.id)">完成</button>
                    <button v-if="item.status === 'open'" type="button" class="np-link" @click="openEdit(item)">修改</button>
                    <button v-if="item.status === 'open'" type="button" class="np-link np-link-danger" @click="openDelete(item)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="np-pager">
          <span>共 {{ filtered.length }} 条</span>
          <button type="button" :disabled="listPage === 1" @click="listPage -= 1">上一页</button>
          <button
            v-for="n in pageNumbers"
            :key="n"
            type="button"
            :class="{ 'is-current': n === listPage }"
            @click="listPage = n"
          >{{ n }}</button>
          <button type="button" :disabled="listPage === pageCount" @click="listPage += 1">下一页</button>
        </div>
      </section>
    </template>

    <template v-else>
      <h1 class="np-page-title">人员负载</h1>
      <form class="np-filter" @submit.prevent>
        <label>姓名 / 部门<input v-model="loadQuery" placeholder="请输入" /></label>
      </form>
      <section class="np-card">
        <div class="np-table-wrap">
          <table class="np-table">
            <thead>
              <tr>
                <th>姓名</th>
                <th>部门</th>
                <th class="np-num">任务数</th>
                <th class="np-num">工时（小时）</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!loads.length" class="np-empty">
                <td colspan="5">暂无数据</td>
              </tr>
              <tr v-for="row in loads" :key="row.id">
                <td>{{ row.name }}</td>
                <td>{{ deptName(row.deptId) }}</td>
                <td class="np-num">{{ row.count }}</td>
                <td class="np-num">{{ row.hours }}</td>
                <td><button type="button" class="np-link" @click="openPerson(row.id)">查看</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <Teleport to="body">
      <div v-if="dialog" class="np-modal" @click.self="closeDialog">
        <div class="np-dialog" :class="{ 'is-wide': dialog === 'create' && createTab === 'daily' }" role="dialog" aria-modal="true" :aria-label="dialogTitle">
          <div class="np-dialog-h">
            <span>{{ dialogTitle }}</span>
            <button type="button" aria-label="关闭" @click="closeDialog">×</button>
          </div>
          <div class="np-dialog-b">
            <template v-if="dialog === 'delete'">
              确认删除任务「{{ deleteTarget?.name }}」？删除后不可恢复。
            </template>
            <template v-else-if="dialog === 'revert'">
              {{ revertText }}
            </template>
            <template v-else-if="dialog === 'unassign'">
              <div class="np-form-grid">
                <label class="np-field np-span-2">任务名称<input class="np-control" :value="unassignTask?.name" disabled /></label>
                <label class="np-field np-span-2">
                  <span><i class="np-req">*</i>员工</span>
                  <select v-model="unassignStaffId" class="np-control">
                    <option value="">请选择</option>
                    <option v-for="person in assigneesOf(unassignTask?.id)" :key="person.staffId" :value="person.staffId">{{ person.name }}</option>
                  </select>
                </label>
              </div>
            </template>
            <template v-else-if="dialog === 'assign'">
              <div class="np-form-grid">
                <label class="np-field np-span-2">任务名称<input class="np-control" :value="assignTask?.name" disabled /></label>
                <label class="np-field np-span-2">
                  <span><i class="np-req">*</i>员工</span>
                  <select v-model="manual.staffId" class="np-control">
                    <option value="">请选择</option>
                    <option v-for="person in peopleForTask(assignTask)" :key="person.id" :value="person.id">{{ person.name }} · {{ deptName(person.deptId) }}</option>
                  </select>
                </label>
              </div>
            </template>
            <template v-else-if="dialog === 'edit'">
              <div class="np-form-grid">
                <label class="np-field np-span-2"><span><i class="np-req">*</i>任务名称</span><input v-model="editForm.name" class="np-control" placeholder="请输入" /></label>
                <label class="np-field"><span><i class="np-req">*</i>申请部门</span>
                  <select v-model="editForm.deptId" class="np-control">
                    <option v-for="dept in npState.depts" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
                  </select>
                </label>
                <div class="np-field np-span-2"><span>人员要求</span>
                  <div class="np-need">
                    <div class="np-field">
                      <span>所属部门</span>
                      <div class="np-checks">
                        <label v-for="dept in npState.depts" :key="dept.id">
                          <input type="checkbox" :checked="editForm.staffDept.includes(dept.id)" @change="toggleDept(editForm.staffDept, dept.id, $event.target.checked)" />
                          {{ dept.name }}
                        </label>
                      </div>
                    </div>
                    <div class="np-field"><span>技能要求</span>
                      <SkillSelect v-model="editForm.skill" :skills="npState.skills" multiple />
                    </div>
                    <label class="np-field"><span>是否本部门优先</span>
                      <select v-model="editForm.preferOwn" class="np-control">
                        <option value="否">否</option>
                        <option value="是">是</option>
                      </select>
                    </label>
                  </div>
                </div>
                <label class="np-field"><span><i class="np-req">*</i>开始时间</span><input v-model="editForm.startText" class="np-control" placeholder="yyyy-MM-dd HH:mm" /></label>
                <label class="np-field"><span><i class="np-req">*</i>结束时间</span><input v-model="editForm.endText" class="np-control" placeholder="yyyy-MM-dd HH:mm" /></label>
                <label class="np-field"><span><i class="np-req">*</i>人数</span><input v-model.number="editForm.headcount" class="np-control" type="number" min="1" max="20" /></label>
                <label class="np-field"><span><i class="np-req">*</i>优先级</span><input v-model.number="editForm.priority" class="np-control" type="number" min="1" max="5" /></label>
              </div>
            </template>
            <template v-else-if="dialog === 'solve'">
              <p v-if="solveError" class="np-form-error">{{ solveError }}</p>
              <p>默认勾选当前部门。人员要求里的所属部门也可以参与，本部门优先时同部门排在前面。</p>
              <div class="np-checks">
                <label v-for="person in solvePeople" :key="person.id">
                  <input type="checkbox" :checked="solveStaff.includes(person.id)" @change="toggleSolveStaff(person.id, $event.target.checked)" />
                  {{ person.name }} · {{ deptName(person.deptId) }} · {{ skillText(person.skill) }}
                </label>
              </div>
            </template>
            <template v-else>
              <div class="np-tabs">
                <button type="button" :class="{ 'is-current': createTab === 'once' }" @click="createTab = 'once'">单次任务</button>
                <button type="button" :class="{ 'is-current': createTab === 'daily' }" @click="createTab = 'daily'">按日重复</button>
              </div>
              <div v-if="createTab === 'once'" class="np-form-grid">
                <label class="np-field np-span-2"><span><i class="np-req">*</i>任务名称</span><input v-model="customForm.name" class="np-control" placeholder="请输入" /></label>
                <label class="np-field"><span><i class="np-req">*</i>申请部门</span>
                  <select v-model="customForm.deptId" class="np-control">
                    <option v-for="dept in npState.depts" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
                  </select>
                </label>
                <div class="np-field np-span-2"><span>人员要求</span>
                  <div class="np-need">
                    <div class="np-field">
                      <span>所属部门</span>
                      <div class="np-checks">
                        <label v-for="dept in npState.depts" :key="dept.id">
                          <input type="checkbox" :checked="customForm.staffDept.includes(dept.id)" @change="toggleDept(customForm.staffDept, dept.id, $event.target.checked)" />
                          {{ dept.name }}
                        </label>
                      </div>
                    </div>
                    <div class="np-field"><span>技能要求</span>
                      <SkillSelect v-model="customForm.skill" :skills="npState.skills" multiple />
                    </div>
                    <label class="np-field"><span>是否本部门优先</span>
                      <select v-model="customForm.preferOwn" class="np-control">
                        <option value="否">否</option>
                        <option value="是">是</option>
                      </select>
                    </label>
                  </div>
                </div>
                <label class="np-field"><span><i class="np-req">*</i>开始时间</span><input v-model="customForm.startText" class="np-control" placeholder="yyyy-MM-dd HH:mm" /></label>
                <label class="np-field"><span><i class="np-req">*</i>结束时间</span><input v-model="customForm.endText" class="np-control" placeholder="yyyy-MM-dd HH:mm" /></label>
                <label class="np-field"><span><i class="np-req">*</i>人数</span><input v-model.number="customForm.headcount" class="np-control" type="number" min="1" max="20" /></label>
                <label class="np-field"><span><i class="np-req">*</i>优先级</span><input v-model.number="customForm.priority" class="np-control" type="number" min="1" max="5" /></label>
              </div>
              <div v-else>
                <div class="np-form-grid">
                  <label class="np-field"><span><i class="np-req">*</i>申请部门</span>
                    <select v-model="templateForm.deptId" class="np-control">
                      <option v-for="dept in npState.depts" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
                    </select>
                  </label>
                  <div class="np-field np-span-2"><span>人员要求</span>
                    <div class="np-need">
                      <div class="np-field">
                        <span>所属部门</span>
                        <div class="np-checks">
                          <label v-for="dept in npState.depts" :key="dept.id">
                            <input type="checkbox" :checked="templateForm.staffDept.includes(dept.id)" @change="toggleDept(templateForm.staffDept, dept.id, $event.target.checked)" />
                            {{ dept.name }}
                          </label>
                        </div>
                      </div>
                      <label class="np-field"><span>是否本部门优先</span>
                        <select v-model="templateForm.preferOwn" class="np-control">
                          <option value="否">否</option>
                          <option value="是">是</option>
                        </select>
                      </label>
                    </div>
                  </div>
                  <label class="np-field"><span><i class="np-req">*</i>开始日期</span><input v-model="templateForm.startDate" class="np-control" placeholder="yyyy-MM-dd" /></label>
                  <label class="np-field"><span><i class="np-req">*</i>结束日期</span><input v-model="templateForm.endDate" class="np-control" placeholder="yyyy-MM-dd" /></label>
                </div>
                <div class="np-table-wrap" style="margin-top: 16px">
                  <table class="np-table np-subtable">
                    <thead>
                      <tr>
                        <th>每日任务</th>
                        <th>开始</th>
                        <th>结束</th>
                        <th>人数</th>
                        <th>技能</th>
                        <th>优先级</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(item, index) in templateForm.items" :key="index">
                        <td><input v-model="item.name" placeholder="请输入" /></td>
                        <td><input v-model="item.startClock" placeholder="HH:mm" /></td>
                        <td><input v-model="item.endClock" placeholder="HH:mm" /></td>
                        <td><input v-model.number="item.headcount" type="number" min="1" max="20" /></td>
                        <td>
                          <SkillSelect v-model="item.skill" :skills="npState.skills" multiple />
                        </td>
                        <td><input v-model.number="item.priority" type="number" min="1" max="5" /></td>
                        <td><button type="button" class="np-link np-link-danger" :disabled="templateForm.items.length === 1" @click="removeDailyTask(index)">删除</button></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <button type="button" class="np-btn np-inline-add" @click="addDailyTask">添加一行</button>
              </div>
            </template>
          </div>
          <div class="np-dialog-f">
            <button type="button" class="np-btn" @click="closeDialog">取消</button>
            <button v-if="dialog === 'delete'" type="button" class="np-btn np-btn-danger" @click="submitDelete">删除</button>
            <button v-else-if="dialog === 'revert'" type="button" class="np-btn np-btn-primary" @click="submitRevert">确定</button>
            <button v-else-if="dialog === 'solve'" type="button" class="np-btn np-btn-primary" @click="submitSolve">分配</button>
            <button v-else type="button" class="np-btn np-btn-primary" @click="submitDialog">确定</button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="personOpen" class="np-modal is-drawer" @click.self="personOpen = false">
        <aside class="np-drawer is-wide" role="dialog" aria-modal="true" aria-label="参与业务">
          <div class="np-drawer-h">
            <span>参与业务</span>
            <button type="button" aria-label="关闭" @click="personOpen = false">×</button>
          </div>
          <div class="np-drawer-b">
            <table class="np-table">
              <thead>
                <tr>
                  <th>板块</th>
                  <th>单据</th>
                  <th>角色</th>
                  <th>状态</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!personWork.length" class="np-empty">
                  <td colspan="5">暂无数据</td>
                </tr>
                <tr v-for="(item, index) in personWork" :key="index">
                  <td>{{ item.kind }}</td>
                  <td>{{ item.title }}</td>
                  <td>{{ item.role }}</td>
                  <td><span class="np-tag">{{ item.status || '—' }}</span></td>
                  <td>{{ item.detail }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </aside>
      </div>
    </Teleport>
  </component>
</template>
