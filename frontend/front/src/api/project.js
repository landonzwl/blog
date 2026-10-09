import { request } from './http'

export function fetchPublishedProjects() {
  return request('/api/projects')
}

export function fetchPublishedProject(id) {
  return request(`/api/projects/${id}`)
}

export function fetchAdminProjects() {
  return request('/api/admin/projects')
}

export function fetchAdminProject(id) {
  return request(`/api/admin/projects/${id}`)
}

export function createProject(payload) {
  return request('/api/admin/projects', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateProject(id, payload) {
  return request(`/api/admin/projects/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteProject(id) {
  return request(`/api/admin/projects/${id}`, { method: 'DELETE' })
}
