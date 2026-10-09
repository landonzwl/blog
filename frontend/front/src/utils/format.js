/** 格式化为 YYYY.MM，兼容 LocalDateTime 字符串 */
export function formatYearMonth(value) {
  if (!value) return ''
  const d = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value)
  if (Number.isNaN(d.getTime())) return String(value).slice(0, 7).replace('-', '.')
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}.${m}`
}

export function formatDateTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

export function firstTag(tags) {
  if (!tags) return '文章'
  return tags.split(',')[0].trim() || '文章'
}

export function splitTags(tags) {
  if (!tags) return []
  return tags.split(',').map((t) => t.trim()).filter(Boolean)
}
