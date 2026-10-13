import { erpStore } from './model'
import { authHeaders, enterRole, npSession } from '../npSession'

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
      erpStore.notice = json.msg || '请先登录'
      return null
    }
    if (!response.ok || json.code !== 200) {
      erpStore.notice = json.msg || '请求失败'
      return null
    }
    applyErp(json.data)
    return json.data
  } catch (error) {
    erpStore.notice = '无法连接业务服务'
    return null
  }
}

function applyErp(data) {
  if (!data) return
  erpStore.notice = data.notice || ''
  erpStore.tables = data.tables || {}
  erpStore.skills = data.skills || []
  erpStore.posts = data.posts || []
  if (data.orgName) erpStore.orgName = data.orgName
  if (data.orgSites) erpStore.orgSites = data.orgSites
}

export async function loadMeta() {
  try {
    const response = await fetch('/api/np/admin/v1/meta', { headers: authHeaders(false) })
    const json = await response.json()
    if (response.ok && json.code === 200) erpStore.meta = json.data.entities || []
  } catch (error) {
    erpStore.meta = erpStore.meta || []
  }
}

export function loadErp() {
  return call('GET', '/admin/erp')
}

export function createDoc(table, body) {
  return call('POST', `/admin/erp/${table}`, body)
}

export function updateDoc(table, id, body) {
  return call('PUT', `/admin/erp/${table}/${id}`, body)
}

export function actDoc(table, id, action) {
  return call('POST', `/admin/erp/${table}/${id}/${action}`)
}

export async function createOption(table, label, retried) {
  try {
    const response = await fetch(`/api/np/admin/erp/${table}/option`, {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ label }),
    })
    const json = await response.json()
    if (response.status === 401 && !retried) {
      const role = npSession.role || 'admin'
      npSession.token = ''
      if (await enterRole(role)) return createOption(table, label, true)
    }
    if (!response.ok || json.code !== 200) {
      return { id: '', label: '', notice: json.msg || '请求失败' }
    }
    const data = json.data || { id: '', label: '', notice: '' }
    if (data.id) await loadErp()
    return data
  } catch (error) {
    return { id: '', label: '', notice: '无法连接业务服务' }
  }
}

export async function optionDocs(table) {
  try {
    const response = await fetch(`/api/np/admin/erp/${table}/options`, { headers: authHeaders(false) })
    const json = await response.json()
    if (!response.ok || json.code !== 200) return []
    return json.data || []
  } catch (error) {
    return []
  }
}

export async function queryDocs(table, columns) {
  try {
    const response = await fetch(`/api/np/admin/erp/${table}/query`, {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ columns }),
    })
    const json = await response.json()
    if (response.status === 401) {
      erpStore.notice = json.msg || '请先登录'
      return null
    }
    if (!response.ok || json.code !== 200) {
      erpStore.notice = json.msg || '查询失败'
      return null
    }
    return json.data
  } catch (error) {
    erpStore.notice = '无法连接业务服务'
    return null
  }
}

async function metaCall(method, path, body) {
  const response = await fetch(`/api/np/admin/v1${path}`, {
    method,
    headers: authHeaders(body != null && !(body instanceof FormData)),
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  })
  const json = await response.json()
  if (!response.ok || json.code !== 200) {
    erpStore.notice = json.msg || '请求失败'
    return null
  }
  if (json.data && json.data.entities) erpStore.meta = json.data.entities
  return json.data
}

export function listFiles(entity, id) {
  return metaCall('GET', `/${entity}/${id}/files`)
}

export function uploadFile(entity, id, file) {
  const body = new FormData()
  body.append('file', file)
  return metaCall('POST', `/${entity}/${id}/files`, body)
}

export function removeFile(entity, id, fileId) {
  return metaCall('DELETE', `/${entity}/${id}/files/${fileId}`)
}

export async function downloadFile(entity, id, file) {
  const response = await fetch(`/api/np/admin/v1/${entity}/${id}/files/${file.id}`, { headers: authHeaders(false) })
  if (!response.ok) {
    erpStore.notice = '附件无法下载'
    return
  }
  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = file.name || '附件'
  link.click()
  URL.revokeObjectURL(link.href)
}

export function addField(code, body) {
  return metaCall('POST', `/meta/entities/${code}/fields`, body)
}

export function removeField(code, key) {
  return metaCall('DELETE', `/meta/entities/${code}/fields/${encodeURIComponent(key)}`)
}
