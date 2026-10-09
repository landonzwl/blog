/**
 * 轻量 fetch 封装，统一处理 Result{code,msg,data}
 */
export async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  let body
  try {
    body = await res.json()
  } catch {
    throw new Error(`请求失败（HTTP ${res.status}）`)
  }

  if (body.code !== 200) {
    throw new Error(body.msg || '请求失败')
  }
  return body.data
}
