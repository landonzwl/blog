import { request } from './http'

export function fetchSiteContent() {
  return request('/api/site/content')
}

export function fetchAdminSiteContent() {
  return request('/api/admin/site/content')
}

export function saveAdminSiteContent(items) {
  return request('/api/admin/site/content', {
    method: 'PUT',
    body: JSON.stringify(items),
  })
}
