<script>
let closeOpen = null
</script>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { createOption, optionDocs } from './api'
import { refLabel } from './model'

const props = defineProps({
  modelValue: { type: [String, Array], default: '' },
  multiple: { type: Boolean, default: true },
  table: { type: String, required: true },
  status: { type: String, default: '' },
  clearable: { type: Boolean, default: false },
  creatable: { type: Boolean, default: false },
  exclude: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
})
const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const rows = ref([])
const root = ref(null)
const menuStyle = ref({})
const draft = ref('')
const creating = ref(false)
const createError = ref('')

const selectedIds = computed(() => {
  if (Array.isArray(props.modelValue)) return props.modelValue.map((item) => String(item)).filter(Boolean)
  return String(props.modelValue || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean)
})

const choices = computed(() => {
  const hidden = new Set(props.exclude || [])
  const items = rows.value.filter((item) => {
    if (!item.id || hidden.has(item.id)) return false
    if (props.status && item.status && item.status !== props.status && !selectedIds.value.includes(item.id)) return false
    return true
  })
  selectedIds.value.forEach((id) => {
    if (!items.some((item) => item.id === id)) items.push({ id, label: nameOf(id) })
  })
  return items
})

const label = computed(() => selectedIds.value.map((id) => nameOf(id)).join('、'))

function nameOf(id) {
  return rows.value.find((item) => item.id === id)?.label || refLabel(props.table, id) || id
}

function place() {
  const rect = root.value?.getBoundingClientRect()
  if (!rect) return
  const gap = 4
  const height = 240
  const below = window.innerHeight - rect.bottom - gap - 8
  const above = rect.top - gap - 8
  const openUp = below < 180 && above > below
  menuStyle.value = {
    top: openUp ? `${Math.max(8, rect.top - gap - height)}px` : `${rect.bottom + gap}px`,
    left: `${rect.left}px`,
    width: `${Math.max(rect.width, 220)}px`,
  }
}

async function load() {
  const data = await optionDocs(props.table)
  rows.value = Array.isArray(data) ? data : []
}

function shut() {
  open.value = false
  if (closeOpen === shut) closeOpen = null
}

async function toggle() {
  if (open.value) {
    shut()
    return
  }
  if (closeOpen) closeOpen()
  open.value = true
  closeOpen = shut
  await load()
  place()
}

function picked(id) {
  return selectedIds.value.includes(id)
}

function choose(id) {
  const next = picked(id) ? selectedIds.value.filter((item) => item !== id) : selectedIds.value.concat(id)
  emit('update:modelValue', next)
}

function clear() {
  emit('update:modelValue', [])
}

async function createNew() {
  const name = draft.value.trim()
  createError.value = ''
  if (!name || creating.value) {
    if (!name) createError.value = '请填写名称'
    return
  }
  creating.value = true
  const data = await createOption(props.table, name)
  creating.value = false
  if (!data?.id) {
    createError.value = data?.notice || '没有新建'
    return
  }
  draft.value = ''
  await load()
  if (!picked(data.id)) choose(data.id)
}

function onDoc(event) {
  if (!open.value) return
  const target = event.target
  if (root.value?.contains(target) || target?.closest?.('.np-ref-menu')) return
  shut()
}

onMounted(load)
document.addEventListener('mousedown', onDoc)
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDoc)
  if (closeOpen === shut) closeOpen = null
})
</script>

<template>
  <div ref="root" class="np-ref">
    <button type="button" class="np-control np-ref-btn" @click="toggle">{{ label || placeholder }}</button>
    <Teleport to="body">
      <div v-if="open" class="np-ref-menu" :style="menuStyle">
        <div class="np-ref-list">
          <button v-if="clearable" type="button" class="np-ref-item" @click="clear">{{ placeholder }}</button>
          <button
            v-for="item in choices"
            :key="item.id"
            type="button"
            class="np-ref-item"
            :class="{ 'is-on': picked(item.id) }"
            @click="choose(item.id)"
          >
            <i class="np-ref-mark">{{ picked(item.id) ? '✓' : '' }}</i>
            {{ item.label || item.id }}
          </button>
          <p v-if="!choices.length" class="np-ref-empty">暂无可选档案</p>
        </div>
        <div v-if="creatable" class="np-ref-create">
          <input v-model="draft" class="np-control" placeholder="新建" @keydown.enter.prevent="createNew" />
          <button type="button" class="np-btn np-btn-primary" :disabled="creating" @click="createNew">新建</button>
        </div>
        <p v-if="creatable && createError" class="np-ref-error">{{ createError }}</p>
      </div>
    </Teleport>
  </div>
</template>
