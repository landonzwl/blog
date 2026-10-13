<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import NpFrame from './NpFrame.vue'
import { addBlock, completeOwnTask, deleteBlock, loadStaff, myBlocks, myTasks, noticeTone, npState, revertOwnTask, startOwnTask, statusText, updateBlock } from './npState'

const menus = [
  { key: 'mine', label: '我的任务' },
  { key: 'blocks', label: '不可工作时段' },
]
const page = ref('mine')
const loading = ref(true)
const dialog = ref('')
const editingId = ref('')
const dropTarget = ref(null)
const revertTarget = ref(null)
const blockForm = reactive({
  startText: '2026-03-04 08:00',
  endText: '2026-03-04 12:00',
  reason: '请假',
})

const me = computed(() => npState.me || { name: '李四', deptName: '' })
const tasks = computed(() => myTasks())
const blocks = computed(() => myBlocks())

onMounted(async () => {
  await loadStaff()
  loading.value = false
})

function tagClass(status) {
  if (status === 'published') return 'np-tag np-tag-published'
  if (status === 'running') return 'np-tag np-tag-running'
  if (status === 'done') return 'np-tag np-tag-done'
  return 'np-tag'
}

function openCreateBlock() {
  editingId.value = ''
  blockForm.startText = '2026-03-04 08:00'
  blockForm.endText = '2026-03-04 12:00'
  blockForm.reason = '请假'
  dialog.value = 'form'
}

function openEditBlock(item) {
  editingId.value = item.id
  blockForm.startText = item.startText
  blockForm.endText = item.endText
  blockForm.reason = item.reason
  dialog.value = 'form'
}

function openDropBlock(item) {
  dropTarget.value = item
  dialog.value = 'drop'
}

async function submitBlock() {
  const data = editingId.value
    ? await updateBlock(editingId.value, blockForm.startText, blockForm.endText, blockForm.reason)
    : await addBlock(blockForm.startText, blockForm.endText, blockForm.reason)
  if (data && npState.notice.startsWith('已')) dialog.value = ''
}

async function submitDropBlock() {
  const data = await deleteBlock(dropTarget.value.id)
  if (data && npState.notice.startsWith('已')) dialog.value = ''
}

function openRevert(item) {
  revertTarget.value = item
  dialog.value = 'revert'
}

async function submitRevert() {
  const data = await revertOwnTask(revertTarget.value.id)
  if (data && npState.notice.startsWith('已')) dialog.value = ''
}
</script>

<template>
  <NpFrame role="staff" :menus="menus" :active="page" :user-name="me.name" @navigate="page = $event">
    <template v-if="page === 'mine'">
      <h1 class="np-page-title">我的任务</h1>
      <div v-if="npState.notice" class="np-alert" :class="noticeTone(npState.notice) === 'error' ? 'np-alert-error' : 'np-alert-success'">
        <span>{{ npState.notice }}</span>
        <button type="button" aria-label="关闭提示" @click="npState.notice = ''">×</button>
      </div>
      <section class="np-card">
        <div class="np-card-bar">
          <div class="np-card-meta">{{ me.deptName }} · 共 {{ tasks.length }} 条</div>
        </div>
        <div class="np-table-wrap">
          <table class="np-table">
            <thead>
              <tr>
                <th>任务名称</th>
                <th>开始时间</th>
                <th>结束时间</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading || !tasks.length" class="np-empty">
                <td colspan="5">{{ loading ? '加载中…' : '暂无数据' }}</td>
              </tr>
              <tr v-for="item in tasks" :key="item.id">
                <td>{{ item.name }}</td>
                <td>{{ item.startText }}</td>
                <td>{{ item.endText }}</td>
                <td><span :class="tagClass(item.status)">{{ statusText(item.status) }}</span></td>
                <td>
                  <div class="np-ops">
                    <button v-if="item.status === 'published'" type="button" class="np-link" @click="startOwnTask(item.id)">开始执行</button>
                    <button v-if="item.status === 'running'" type="button" class="np-link" @click="completeOwnTask(item.id)">完成</button>
                    <button v-if="item.status === 'running' || item.status === 'done'" type="button" class="np-link" @click="openRevert(item)">退回</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <template v-else>
      <h1 class="np-page-title">不可工作时段</h1>
      <div v-if="npState.notice" class="np-alert" :class="noticeTone(npState.notice) === 'error' ? 'np-alert-error' : 'np-alert-success'">
        <span>{{ npState.notice }}</span>
        <button type="button" aria-label="关闭提示" @click="npState.notice = ''">×</button>
      </div>
      <section class="np-card">
        <div class="np-card-bar">
          <div class="np-card-meta">共 {{ blocks.length }} 条</div>
          <div class="np-card-actions">
            <button type="button" class="np-btn np-btn-primary" @click="openCreateBlock">登记</button>
          </div>
        </div>
        <div class="np-table-wrap">
          <table class="np-table">
            <thead>
              <tr>
                <th>原因</th>
                <th>开始时间</th>
                <th>结束时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading || !blocks.length" class="np-empty">
                <td colspan="4">{{ loading ? '加载中…' : '暂无数据' }}</td>
              </tr>
              <tr v-for="item in blocks" :key="item.id">
                <td>{{ item.reason }}</td>
                <td>{{ item.startText }}</td>
                <td>{{ item.endText }}</td>
                <td>
                  <div class="np-ops">
                    <button type="button" class="np-link" @click="openEditBlock(item)">修改</button>
                    <button type="button" class="np-link np-link-danger" @click="openDropBlock(item)">撤销</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <Teleport to="body">
      <div v-if="dialog" class="np-modal" @click.self="dialog = ''">
        <div class="np-dialog" role="dialog" aria-modal="true" :aria-label="dialog === 'drop' ? '撤销不可工作时段' : dialog === 'revert' ? '退回任务' : '登记不可工作时段'">
          <div class="np-dialog-h">
            <span>{{ dialog === 'drop' ? '撤销不可工作时段' : dialog === 'revert' ? '退回任务' : (editingId ? '修改不可工作时段' : '登记不可工作时段') }}</span>
            <button type="button" aria-label="关闭" @click="dialog = ''">×</button>
          </div>
          <div class="np-dialog-b">
            <template v-if="dialog === 'drop'">
              确认撤销「{{ dropTarget?.reason }}」？撤销后该时段可以再次被分配。
            </template>
            <template v-else-if="dialog === 'revert'">
              确认将「{{ revertTarget?.name }}」退回{{ revertTarget?.status === 'done' ? '正在执行' : '已发布' }}？
            </template>
            <div v-else class="np-form-grid">
              <label class="np-field"><span><i class="np-req">*</i>开始时间</span><input v-model="blockForm.startText" class="np-control" placeholder="yyyy-MM-dd HH:mm" /></label>
              <label class="np-field"><span><i class="np-req">*</i>结束时间</span><input v-model="blockForm.endText" class="np-control" placeholder="yyyy-MM-dd HH:mm" /></label>
              <label class="np-field np-span-2"><span><i class="np-req">*</i>原因</span><input v-model="blockForm.reason" class="np-control" maxlength="20" placeholder="请输入" /></label>
            </div>
          </div>
          <div class="np-dialog-f">
            <button type="button" class="np-btn" @click="dialog = ''">取消</button>
            <button v-if="dialog === 'drop'" type="button" class="np-btn np-btn-danger" @click="submitDropBlock">撤销</button>
            <button v-else-if="dialog === 'revert'" type="button" class="np-btn np-btn-primary" @click="submitRevert">确定</button>
            <button v-else type="button" class="np-btn np-btn-primary" @click="submitBlock">确定</button>
          </div>
        </div>
      </div>
    </Teleport>
  </NpFrame>
</template>
