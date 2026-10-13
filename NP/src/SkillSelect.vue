<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { loadErp } from './erp/api'
import { erpStore } from './erp/model'
import { createPost, createSkill, npState, removePost, removeSkill } from './npState'

const props = defineProps({
  modelValue: { type: [String, Array], default: '' },
  multiple: { type: Boolean, default: false },
  kind: { type: String, default: 'skill' },
  skills: { type: Array, default: () => [] },
  posts: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const draft = ref('')
const saving = ref(false)
const error = ref('')
const pending = ref(null)
const root = ref(null)
const menuStyle = ref({})

const rows = computed(() => (props.kind === 'post' ? props.posts : props.skills) || [])
const noun = computed(() => (props.kind === 'post' ? '岗位' : '技能'))

const selectedIds = computed(() => {
  if (props.multiple) {
    if (Array.isArray(props.modelValue)) return props.modelValue.map((item) => String(item)).filter(Boolean)
    return String(props.modelValue || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean)
  }
  return props.modelValue ? [String(props.modelValue)] : []
})

const label = computed(() => {
  const names = selectedIds.value.map((id) => rows.value.find((item) => item.id === id)?.name || id)
  return names.join('、')
})

function place() {
  const rect = root.value?.getBoundingClientRect()
  if (!rect) return
  const gap = 4
  const below = window.innerHeight - rect.bottom - gap - 8
  const above = rect.top - gap - 8
  const openUp = below < 160 && above > below
  const height = 220
  menuStyle.value = {
    top: openUp ? `${Math.max(8, rect.top - gap - height)}px` : `${rect.bottom + gap}px`,
    left: `${rect.left}px`,
    width: `${Math.max(rect.width, 220)}px`,
  }
}

watch(() => rows.value.map((item) => item.id).join(','), () => {
  if (!rows.value.length) return
  const known = new Set(rows.value.map((item) => item.id))
  if (props.multiple) {
    const next = selectedIds.value.filter((id) => known.has(id))
    if (next.length !== selectedIds.value.length) emit('update:modelValue', next)
    return
  }
  if (props.modelValue && !known.has(String(props.modelValue))) emit('update:modelValue', '')
})

function toggle() {
  open.value = !open.value
  if (open.value) place()
}

function picked(id) {
  return selectedIds.value.includes(id)
}

function choose(id) {
  if (props.multiple) {
    const next = picked(id) ? selectedIds.value.filter((item) => item !== id) : selectedIds.value.concat(id)
    emit('update:modelValue', next)
    return
  }
  emit('update:modelValue', id)
  open.value = false
}

async function create() {
  const name = draft.value.trim().slice(0, 32)
  error.value = ''
  if (!name || saving.value) {
    error.value = name ? '' : `请输入${noun.value}名称`
    return
  }
  saving.value = true
  const data = props.kind === 'post' ? await createPost(name) : await createSkill(name)
  saving.value = false
  if (!data?.id) {
    error.value = npState.notice || `${noun.value}没有保存`
    return
  }
  draft.value = ''
  if (props.kind === 'post') erpStore.posts = data.posts || erpStore.posts
  else erpStore.skills = data.skills || erpStore.skills
  if (props.multiple) {
    if (!picked(data.id)) emit('update:modelValue', selectedIds.value.concat(data.id))
    open.value = true
    place()
    return
  }
  emit('update:modelValue', data.id)
  open.value = false
}

function drop(id) {
  if (props.multiple) emit('update:modelValue', selectedIds.value.filter((item) => item !== id))
  else if (String(props.modelValue) === id) emit('update:modelValue', '')
}

async function finishDelete(data, id) {
  pending.value = null
  if (props.kind === 'post') {
    if (Array.isArray(data.posts)) erpStore.posts = data.posts
  } else if (Array.isArray(data.skills)) erpStore.skills = data.skills
  drop(id)
  await loadErp()
  place()
}

async function askRemove(skill) {
  error.value = ''
  const data = props.kind === 'post' ? await removePost(skill.id, false) : await removeSkill(skill.id, false)
  if (!data) {
    error.value = npState.notice || `${noun.value}没有删除`
    return
  }
  if (data.deleted) {
    await finishDelete(data, skill.id)
    return
  }
  pending.value = { id: skill.id, name: data.name || skill.name, callers: data.callers || [] }
  place()
}

async function confirmRemove() {
  if (!pending.value) return
  const id = pending.value.id
  const data = props.kind === 'post' ? await removePost(id, true) : await removeSkill(id, true)
  if (!data?.deleted) {
    error.value = npState.notice || `${noun.value}没有删除`
    return
  }
  await finishDelete(data, id)
}

function onDoc(event) {
  if (!open.value) return
  const target = event.target
  if (root.value?.contains(target) || target?.closest?.('.np-skill-menu')) return
  open.value = false
}

document.addEventListener('mousedown', onDoc)
onBeforeUnmount(() => document.removeEventListener('mousedown', onDoc))
</script>

<template>
  <div ref="root" class="np-skill">
    <button type="button" class="np-control np-skill-btn" @click="toggle">{{ label || '请选择' }}</button>
    <Teleport to="body">
      <div v-if="open" class="np-skill-menu" :style="menuStyle">
        <div class="np-skill-list">
          <div v-for="skill in rows" :key="skill.id" class="np-skill-row" :class="{ 'is-on': picked(skill.id) }">
            <button type="button" class="np-skill-item" @click="choose(skill.id)">{{ skill.name }}</button>
            <button type="button" class="np-skill-x" :aria-label="`删除${noun}`" @click.stop="askRemove(skill)">×</button>
          </div>
        </div>
        <div v-if="pending" class="np-skill-tip">
          <p>{{ pending.name }}已被使用。确认后会先去掉这些引用，再删除该{{ noun }}。</p>
          <ul>
            <li v-for="caller in pending.callers" :key="caller">{{ caller }}</li>
          </ul>
          <div>
            <button type="button" class="np-btn" @click="pending = null">取消</button>
            <button type="button" class="np-btn np-btn-primary" @click="confirmRemove">确认删除</button>
          </div>
        </div>
        <div class="np-skill-add">
          <input v-model="draft" class="np-control" :placeholder="`新${noun}`" @keydown.enter.prevent="create" />
          <button type="button" class="np-btn np-btn-primary" :disabled="saving" @click="create">新建</button>
        </div>
        <p v-if="error" class="np-skill-error">{{ error }}</p>
      </div>
    </Teleport>
  </div>
</template>
