import { reactive } from 'vue'

const KEY = 'np-session'

export const npSession = reactive({
  token: '',
  role: '',
  name: '',
  staffId: '',
})

function read() {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    npSession.token = data.token || ''
    npSession.role = data.role || ''
    npSession.name = data.name || ''
    npSession.staffId = data.staffId || ''
  } catch (error) {
    sessionStorage.removeItem(KEY)
  }
}

read()

export function authHeaders(json) {
  const headers = {}
  if (npSession.token) headers.Authorization = `Bearer ${npSession.token}`
  if (json) headers['Content-Type'] = 'application/json'
  return headers
}

export function clearSession() {
  npSession.token = ''
  npSession.role = ''
  npSession.name = ''
  npSession.staffId = ''
  sessionStorage.removeItem(KEY)
}

function saveSession(data) {
  npSession.token = data.token || ''
  npSession.role = data.role || ''
  npSession.name = data.name || ''
  npSession.staffId = data.staffId || ''
  sessionStorage.setItem(KEY, JSON.stringify({
    token: npSession.token,
    role: npSession.role,
    name: npSession.name,
    staffId: npSession.staffId,
  }))
}

export async function login(username, password) {
  const response = await fetch('/api/np/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const json = await response.json()
  if (!response.ok || json.code !== 200 || !json.data?.token) {
    return json.msg || '登录失败'
  }
  saveSession(json.data)
  return ''
}

export const previewAccounts = [
  { role: 'admin', username: 'admin', password: 'Np@admin2026', name: '管理员', post: '系统管理员' },
  { role: 'staff', username: 'lisi', password: 'Np@lisi2026', name: '李四', post: '门店员工' },
  { role: 'visitor', username: 'guest', password: 'Np@guest2026', name: '访客', post: '只读账号' },
]

export async function enterRole(role) {
  const account = previewAccounts.find((item) => item.role === role) || previewAccounts[0]
  if (npSession.token && npSession.role === account.role) return ''
  return login(account.username, account.password)
}

export async function ensurePreview() {
  if (npSession.token) return ''
  return enterRole('admin')
}

export async function logout() {
  const token = npSession.token
  clearSession()
  if (!token) return
  try {
    await fetch('/api/np/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
  } catch (error) {
    // 本地会话已经清除
  }
}

export function homePath() {
  if (npSession.role === 'staff') return '/staff'
  if (npSession.role === 'visitor') return '/visitor'
  return '/admin'
}
