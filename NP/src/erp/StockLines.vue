<script setup>
import RefSelect from './RefSelect.vue'
import { erpStore } from './model'

const props = defineProps({
  kind: { type: String, required: true },
  lines: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:lines'])

function blank() {
  return { material: [], warehouse: [], qty: 1, actual: 0 }
}

function update(next) {
  emit('update:lines', next)
}

function patch(index, key, value) {
  const next = props.lines.map((line, i) => (i === index ? { ...line, [key]: value } : line))
  if (props.kind === 'count' && (key === 'material' || key === 'warehouse')) {
    next[index].actual = book(next[index])
  }
  update(next)
}

function add() {
  update(props.lines.concat([blank()]))
}

function remove(index) {
  const next = props.lines.filter((_, i) => i !== index)
  update(next.length ? next : [blank()])
}

function first(value) {
  if (Array.isArray(value)) return value.find((item) => item) || ''
  return value || ''
}

function book(line) {
  const material = first(line.material)
  const warehouse = first(line.warehouse)
  const rows = (erpStore.tables.stock || []).filter((item) => first(item.material) === material && first(item.warehouse) === warehouse)
  return rows.reduce((max, item) => Math.max(max, Number(item.qty) || 0), 0)
}
</script>

<template>
  <div class="np-lines">
    <table>
      <thead>
        <tr>
          <th v-if="kind !== 'transfer'">仓库</th>
          <th>物料</th>
          <th v-if="kind === 'count'">账面</th>
          <th>{{ kind === 'count' ? '实盘' : '数量' }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(line, index) in lines" :key="index">
          <td v-if="kind !== 'transfer'">
            <RefSelect :model-value="line.warehouse" multiple table="warehouses" placeholder="仓库" @update:model-value="patch(index, 'warehouse', $event)" />
          </td>
          <td>
            <RefSelect :model-value="line.material" multiple table="materials" placeholder="物料" @update:model-value="patch(index, 'material', $event)" />
          </td>
          <td v-if="kind === 'count'">{{ book(line) }}</td>
          <td v-if="kind === 'count'">
            <input class="np-control" type="number" :value="line.actual" @input="patch(index, 'actual', Number($event.target.value))" />
          </td>
          <td v-else>
            <input class="np-control" type="number" :value="line.qty" @input="patch(index, 'qty', Number($event.target.value))" />
          </td>
          <td>
            <button type="button" class="np-link np-link-danger" @click="remove(index)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <button type="button" class="np-link" @click="add">添加一行</button>
  </div>
</template>
