/** 轻量 Markdown → HTML（先转义，再解析常用语法） */
export function renderMarkdown(source) {
  if (!source) return ''
  const text = String(source).replace(/\r\n/g, '\n')
  const blocks = []
  const fence = /```([\w-]*)\n([\s\S]*?)```/g
  let last = 0
  let m
  while ((m = fence.exec(text))) {
    if (m.index > last) blocks.push({ type: 'md', value: text.slice(last, m.index) })
    blocks.push({ type: 'code', lang: m[1] || '', value: m[2].replace(/\n$/, '') })
    last = m.index + m[0].length
  }
  if (last < text.length) blocks.push({ type: 'md', value: text.slice(last) })

  return blocks
    .map((b) => (b.type === 'code' ? renderCode(b.value, b.lang) : renderMdChunk(b.value)))
    .join('')
}

function escapeHtml(s) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function renderCode(code, lang) {
  const cls = lang ? ` class="language-${escapeHtml(lang)}"` : ''
  return `<pre class="md-pre"><code${cls}>${escapeHtml(code)}</code></pre>`
}

function inline(s) {
  let out = escapeHtml(s)
  out = out.replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  out = out.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  return out
}

function renderMdChunk(chunk) {
  const lines = chunk.split('\n')
  const html = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) {
      i += 1
      continue
    }
    if (/^---+\s*$/.test(line)) {
      html.push('<hr class="md-hr" />')
      i += 1
      continue
    }
    const h = /^(#{1,3})\s+(.+)$/.exec(line)
    if (h) {
      const level = h[1].length
      html.push(`<h${level} class="md-h${level}">${inline(h[2])}</h${level}>`)
      i += 1
      continue
    }
    if (/^>\s?/.test(line)) {
      const quote = []
      while (i < lines.length && /^>\s?/.test(lines[i])) {
        quote.push(lines[i].replace(/^>\s?/, ''))
        i += 1
      }
      html.push(`<blockquote class="md-quote">${inline(quote.join(' '))}</blockquote>`)
      continue
    }
    if (/^\|.+\|/.test(line) && i + 1 < lines.length && /^\|?\s*-+/.test(lines[i + 1])) {
      const rows = []
      while (i < lines.length && /^\|/.test(lines[i])) {
        if (!/^\|?\s*-+/.test(lines[i])) rows.push(lines[i])
        i += 1
      }
      html.push(renderTable(rows))
      continue
    }
    if (/^[-*]\s+/.test(line)) {
      const items = []
      while (i < lines.length && /^[-*]\s+/.test(lines[i])) {
        items.push(`<li>${inline(lines[i].replace(/^[-*]\s+/, ''))}</li>`)
        i += 1
      }
      html.push(`<ul class="md-ul">${items.join('')}</ul>`)
      continue
    }
    if (/^\d+\.\s+/.test(line)) {
      const items = []
      while (i < lines.length && /^\d+\.\s+/.test(lines[i])) {
        items.push(`<li>${inline(lines[i].replace(/^\d+\.\s+/, ''))}</li>`)
        i += 1
      }
      html.push(`<ol class="md-ol">${items.join('')}</ol>`)
      continue
    }
    const para = []
    while (i < lines.length && lines[i].trim() && !/^(#{1,3}\s|[-*]\s|\d+\.\s|>|```|\|)/.test(lines[i])) {
      para.push(lines[i])
      i += 1
    }
    html.push(`<p class="md-p">${inline(para.join(' '))}</p>`)
  }
  return html.join('')
}

function renderTable(rows) {
  if (!rows.length) return ''
  const split = (row) =>
    row
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((c) => c.trim())
  const head = split(rows[0])
  const body = rows.slice(1).map(split)
  const th = head.map((c) => `<th>${inline(c)}</th>`).join('')
  const tr = body
    .map((r) => `<tr>${r.map((c) => `<td>${inline(c)}</td>`).join('')}</tr>`)
    .join('')
  return `<div class="md-table-wrap"><table class="md-table"><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table></div>`
}
