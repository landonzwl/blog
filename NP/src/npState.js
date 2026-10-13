import { reactive, ref } from 'vue'
import { authHeaders, enterRole, npSession } from './npSession'

export const npEmbedRole = ref(null)

export const npState = reactive({
  deptId: 'east',
  notice: '',
  tasks: [],
  assignments: [],
  gaps: [],
  variance: 0,
  depts: [],
  staff: [],
  loads: [],
  skills: [],
  me: null,
  myTaskList: [],
  myBlockList: [],
  publicTasks: [],
})

async function call(method, path, body, retried) {
  try {
    const response = await fetch(`/api/np${path}`, {
      method,
      headers: authHeaders(!!body),
      body: body ? JSON.stringify(body) : undefined,
    })
    const json = await response.json()
    if (response.status === 401 && !retried) {
      const role = npSession.role || 'admin'
      npSession.token = ''
      if (!(await enterRole(role))) return call(method, path, body, true)
    }
    if (response.status === 401) {
      npState.notice = json.msg || '请先登录'
      return null
    }
    if (!response.ok || json.code !== 200) {
      npState.notice = json.msg || '请求失败'
      return null
    }
    return json.data
  } catch (error) {
    npState.notice = '无法连接排班服务'
    return null
  }
}

function applyAdmin(data) {
  npState.notice = data.notice || ''
  npState.depts = data.depts || []
  npState.staff = data.staff || []
  npState.tasks = data.tasks || []
  npState.assignments = data.assignments || []
  npState.gaps = data.gaps || []
  npState.variance = data.variance || 0
  npState.loads = data.loads || []
  npState.skills = data.skills || []
}

function applyStaff(data) {
  npState.notice = data.notice || ''
  npState.me = data.me
  npState.myTaskList = data.tasks || []
  npState.myBlockList = data.blocks || []
}

export function setDept(deptId) {
  npState.deptId = deptId
}

export function deptName(deptId) {
  return npState.depts.find((item) => item.id === deptId)?.name || deptId
}

export function staffName(staffId) {
  return npState.staff.find((item) => item.id === staffId)?.name || staffId
}

export function visibleTasks(deptId = npState.deptId) {
  if (deptId === 'all') return npState.tasks
  return npState.tasks.filter((item) => item.deptId === deptId)
}

export function peopleIn(deptId = npState.deptId) {
  if (deptId === 'all') return npState.staff
  return npState.staff.filter((item) => item.deptId === deptId)
}

export function loadRows(deptId = npState.deptId) {
  if (deptId === 'all') return npState.loads
  return npState.loads.filter((item) => item.deptId === deptId)
}

export function myTasks() {
  return npState.myTaskList
}

export function myBlocks() {
  return npState.myBlockList
}

export async function loadAdmin() {
  const data = await call('GET', '/admin/board', null, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function createSkill(name) {
  const data = await call('POST', '/admin/skills', { name })
  if (data && Array.isArray(data.skills)) npState.skills = data.skills
  return data
}

export async function removeSkill(id, confirm) {
  const data = await call('POST', `/admin/skills/${encodeURIComponent(id)}/delete`, { confirm: !!confirm })
  if (data && data.deleted) {
    if (Array.isArray(data.skills)) npState.skills = data.skills
    await loadAdmin()
  }
  return data
}

export async function createPost(name) {
  return call('POST', '/admin/posts', { name })
}

export async function removePost(id, confirm) {
  return call('POST', `/admin/posts/${encodeURIComponent(id)}/delete`, { confirm: !!confirm })
}

export async function loadStaff() {
  const data = await call('GET', '/staff/home', null, 'staff')
  if (data) applyStaff(data)
  return data
}

export async function loadVisitor() {
  const data = await call('GET', '/visitor/tasks', null, 'visitor')
  if (data) {
    npState.depts = data.depts || []
    npState.publicTasks = data.tasks || []
  }
}

export async function createFromTemplate(form) {
  const data = await call('POST', '/admin/tasks/template', {
    deptId: form.deptId,
    startDate: form.startDate,
    endDate: form.endDate,
    staffDept: Array.isArray(form.staffDept) ? form.staffDept.join(',') : (form.staffDept || ''),
    preferOwn: form.preferOwn || '否',
    items: form.items.map((item) => ({
      name: item.name,
      startClock: item.startClock,
      endClock: item.endClock,
      headcount: item.headcount,
      skill: Array.isArray(item.skill) ? item.skill.join(',') : item.skill,
      priority: item.priority,
    })),
  }, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function createCustom(form) {
  const data = await call('POST', '/admin/tasks/custom', {
    name: form.name,
    deptId: form.deptId,
    startText: form.startText,
    endText: form.endText,
    headcount: form.headcount,
    skill: Array.isArray(form.skill) ? form.skill.join(',') : form.skill,
    staffDept: Array.isArray(form.staffDept) ? form.staffDept.join(',') : (form.staffDept || ''),
    preferOwn: form.preferOwn || '否',
    priority: form.priority,
  }, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function runSolve(taskIds, staffIds) {
  const body = {
    deptId: npState.deptId,
    taskIds: Array.isArray(taskIds) ? taskIds : [],
  }
  if (Array.isArray(staffIds) && staffIds.length) body.staffIds = staffIds
  const data = await call('POST', '/admin/solve', body, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function updateTask(id, form) {
  const data = await call('PUT', `/admin/tasks/${encodeURIComponent(id)}`, {
    name: form.name,
    deptId: form.deptId,
    startText: form.startText,
    endText: form.endText,
    headcount: form.headcount,
    skill: Array.isArray(form.skill) ? form.skill.join(',') : form.skill,
    staffDept: Array.isArray(form.staffDept) ? form.staffDept.join(',') : (form.staffDept || ''),
    preferOwn: form.preferOwn || '否',
    priority: form.priority,
  }, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function deleteTask(id) {
  const data = await call('DELETE', `/admin/tasks/${encodeURIComponent(id)}`, null, 'admin')
  if (data) applyAdmin(data)
  return data
}

export function statusText(status) {
  if (status === 'published') return '已发布'
  if (status === 'running') return '正在执行'
  if (status === 'done') return '已完成'
  return '待分配'
}

export function noticeTone(text) {
  if (!text) return ''
  if (/不能|不足|失败|冲突|不存在|没有|必须|晚于|无法/.test(text)) return 'error'
  return 'success'
}

export async function startTask(id) {
  const data = await call('POST', `/admin/tasks/${encodeURIComponent(id)}/start`, {}, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function completeTask(id) {
  const data = await call('POST', `/admin/tasks/${encodeURIComponent(id)}/complete`, {}, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function startOwnTask(id) {
  const data = await call('POST', `/staff/tasks/${encodeURIComponent(id)}/start`, {}, 'staff')
  if (data) applyStaff(data)
  return data
}

export async function completeOwnTask(id) {
  const data = await call('POST', `/staff/tasks/${encodeURIComponent(id)}/complete`, {}, 'staff')
  if (data) applyStaff(data)
  return data
}

export async function publishTask(id) {
  const data = await call('POST', `/admin/tasks/${encodeURIComponent(id)}/publish`, {}, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function revertTask(id) {
  const data = await call('POST', `/admin/tasks/${encodeURIComponent(id)}/revert`, {}, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function revertOwnTask(id) {
  const data = await call('POST', `/staff/tasks/${encodeURIComponent(id)}/revert`, {}, 'staff')
  if (data) applyStaff(data)
  return data
}

export async function cancelAssign(taskId, staffId) {
  const data = await call('POST', '/admin/assignments/cancel', { taskId, staffId }, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function updateBlock(id, startText, endText, reason) {
  const data = await call('PUT', `/staff/blocks/${encodeURIComponent(id)}`, { startText, endText, reason }, 'staff')
  if (data) applyStaff(data)
  return data
}

export async function deleteBlock(id) {
  const data = await call('DELETE', `/staff/blocks/${encodeURIComponent(id)}`, null, 'staff')
  if (data) applyStaff(data)
  return data
}

export async function publishOpen() {
  const data = await call('POST', `/admin/publish?deptId=${encodeURIComponent(npState.deptId)}`, null, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function assignManual(taskId, staffId) {
  const data = await call('POST', '/admin/assignments', { taskId, staffId }, 'admin')
  if (data) applyAdmin(data)
  return data
}

export async function addBlock(startText, endText, reason) {
  const data = await call('POST', '/staff/blocks', { startText, endText, reason }, 'staff')
  if (data) applyStaff(data)
  return data
}
