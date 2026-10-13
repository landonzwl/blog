<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { homePath, login } from './npSession'

const router = useRouter()
const username = ref('admin')
const password = ref('')
const error = ref('')
const pending = ref(false)

async function submit() {
  error.value = ''
  pending.value = true
  try {
    const message = await login(username.value.trim(), password.value)
    if (message) {
      error.value = message
      return
    }
    const target = homePath()
    if (router.resolve(target).matched.length) router.push(target)
  } catch (err) {
    error.value = '无法连接业务服务'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="np-login">
    <form class="np-login-card" @submit.prevent="submit">
      <div class="np-logo">
        <span class="np-logo-mark">企</span>
        <span>企业管理系统</span>
      </div>
      <h1>登录</h1>
      <label class="np-field">
        <span><i class="np-req">*</i>账号</span>
        <input v-model="username" class="np-control" autocomplete="username" />
      </label>
      <label class="np-field">
        <span><i class="np-req">*</i>口令</span>
        <input v-model="password" class="np-control" type="password" autocomplete="current-password" />
      </label>
      <p v-if="error" class="np-login-error">{{ error }}</p>
      <button type="submit" class="np-btn np-btn-primary" :disabled="pending">登录</button>
      <div class="np-login-hint">
        <p>初始账号</p>
        <p>管理员 admin / Np@admin2026</p>
        <p>员工李四 lisi / Np@lisi2026</p>
        <p>访客 guest / Np@guest2026</p>
        <p>其他在岗人员为姓名拼音，口令 Np@拼音2026</p>
      </div>
    </form>
  </div>
</template>

<style scoped>
.np-login {
  --np-primary: #1677ff;
  --np-primary-hover: #4096ff;
  --np-text: rgba(0, 0, 0, 0.88);
  --np-border: #d9d9d9;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  padding: 24px;
  font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
  color: rgba(0, 0, 0, 0.88);
}
.np-login-card {
  width: 400px;
  max-width: 100%;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 28px 28px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.np-login-card h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.np-login-error {
  margin: 0;
  color: #cf1322;
  font-size: 13px;
}
.np-login-hint {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.7;
}
.np-login-hint p {
  margin: 0;
}
</style>
<style src="./np.css"></style>
