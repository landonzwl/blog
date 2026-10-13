<script setup>
import { onMounted, ref } from 'vue'
import ErpShell from './erp/ErpShell.vue'
import ErpHome from './erp/ErpHome.vue'
import ErpList from './erp/ErpList.vue'
import NpAudit from './erp/NpAudit.vue'
import NpAdminView from './NpAdminView.vue'
import NpObjects from './erp/NpObjects.vue'
import { loadErp, loadMeta } from './erp/api'

const active = ref('home')
const columnPreset = ref(null)
let queryToken = 0

function navigate(key) {
  columnPreset.value = null
  active.value = key
}

function openQueried(event) {
  queryToken += 1
  columnPreset.value = { page: event.page, columns: event.columns, token: queryToken }
  active.value = event.page
}

onMounted(async () => {
  await loadErp()
  await loadMeta()
})
</script>

<template>
  <ErpShell :active="active" @navigate="navigate">
    <ErpHome v-if="active === 'home'" @open="navigate" />
    <NpAdminView v-else-if="active === 'schedule' || active === 'loads'" bare :section="active === 'loads' ? 'loads' : 'tasks'" />
    <NpAudit v-else-if="active === 'audit'" />
    <NpObjects v-else-if="active === 'objects'" />
    <ErpList v-else :page-key="active" :column-preset="columnPreset" @open="openQueried" />
  </ErpShell>
</template>
