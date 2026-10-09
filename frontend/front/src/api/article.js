import { request } from './http'

export function fetchPublishedArticles(page = 1, size = 10, tag) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (tag) params.set('tag', tag)
  return request(`/api/articles?${params}`)
}

export function fetchPublishedArticle(id) {
  return request(`/api/articles/${id}`)
}

export function fetchAdminArticles(page = 1, size = 10, status) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status !== undefined && status !== null && status !== '') {
    params.set('status', String(status))
  }
  return request(`/api/admin/articles?${params}`)
}

export function fetchAdminArticle(id) {
  return request(`/api/admin/articles/${id}`)
}

export function createArticle(payload) {
  return request('/api/admin/articles', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateArticle(id, payload) {
  return request(`/api/admin/articles/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteArticle(id) {
  return request(`/api/admin/articles/${id}`, { method: 'DELETE' })
}

export function updateArticleStatus(id, status) {
  return request(`/api/admin/articles/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  })
}
