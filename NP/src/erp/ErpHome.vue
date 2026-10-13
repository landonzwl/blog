<script setup>
import { computed } from 'vue'
import { erpStore, money, refLabel, summary, tagClass, todayText } from './model'

const emit = defineEmits(['open'])
const board = computed(() => summary())

const cards = computed(() => [
  { key: 'salesOrders', label: '销售订单金额', value: money(board.value.salesAmount), hint: '全部订单合计' },
  { key: 'deliveries', label: '待发货', value: String(board.value.waitShip), hint: '发货单未出库' },
  { key: 'stock', label: '库存预警', value: String(board.value.warn), hint: '低于安全库存' },
  { key: 'approvals', label: '待审批', value: String(board.value.pending), hint: '各板块提交的审批' },
])
</script>

<template>
  <h1 class="np-page-title">工作台</h1>
  <p class="np-lead">业务日期 {{ todayText() }} · {{ erpStore.orgSites || '东区门店 / 西区门店' }}</p>
  <div v-if="erpStore.notice" class="np-alert" :class="/不能|不足|没有|只有/.test(erpStore.notice) ? 'np-alert-error' : 'np-alert-success'">
    <span>{{ erpStore.notice }}</span>
    <button type="button" aria-label="关闭提示" @click="erpStore.notice = ''">×</button>
  </div>
  <div class="np-kpis">
    <button v-for="card in cards" :key="card.key" type="button" class="np-kpi" @click="emit('open', card.key)">
      <span>{{ card.label }}</span>
      <strong>{{ card.value }}</strong>
      <small>{{ card.hint }}</small>
    </button>
  </div>
  <div class="np-home-grid">
    <section class="np-card">
      <div class="np-card-bar">
        <strong>待办</strong>
        <button type="button" class="np-link" @click="emit('open', 'approvals')">进入审批</button>
      </div>
      <div class="np-table-wrap">
        <table class="np-table">
          <thead>
            <tr>
              <th>类型</th>
              <th>标题</th>
              <th>申请人</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!board.todos.length" class="np-empty"><td colspan="4">暂无待办</td></tr>
            <tr v-for="row in board.todos" :key="row.id">
              <td>{{ row.type }}</td>
              <td>{{ row.title }}</td>
              <td>{{ row.applicant }}</td>
              <td><span :class="tagClass(row.status)">{{ row.status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="np-card">
      <div class="np-card-bar">
        <strong>公告</strong>
        <button type="button" class="np-link" @click="emit('open', 'notices')">全部公告</button>
      </div>
      <ul class="np-feed">
        <li v-for="item in board.notices" :key="item.id">
          <span>{{ item.title }}</span>
          <small>{{ item.date }}</small>
        </li>
      </ul>
    </section>
  </div>
  <section class="np-card np-gap">
    <div class="np-card-bar">
      <strong>最近销售订单</strong>
      <button type="button" class="np-link" @click="emit('open', 'salesOrders')">全部订单</button>
    </div>
    <div class="np-table-wrap">
      <table class="np-table">
        <thead>
          <tr>
            <th>订单编号</th>
            <th>客户</th>
            <th>物料</th>
            <th class="np-num">金额</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in board.orders" :key="row.id">
            <td>{{ row.code }}</td>
            <td>{{ refLabel('customers', row.customer) }}</td>
            <td>{{ refLabel('materials', row.material) }}</td>
            <td class="np-num">{{ money(row.amount) }}</td>
            <td><span :class="tagClass(row.status)">{{ row.status }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
