# RAG 智能工单系统

> 2024.07 ~ 2024.08 · 个人项目 · 部署于腾讯云 2C2G 服务器

面向运维故障报修场景的大模型应用原型。**用"一个 AI 对话入口"取代传统的表单工单**：

- 自然语言问答：用户用大白话描述故障，AI 结合企业知识库（RAG）给出解决步骤，能解决的当场解决；
- 自动工单生成：AI 判断需要人工处理时，自动抽取对话中的信息**预填写工单**，用户在聊天中的卡片里二次补充确认后提交；
- 实时反馈闭环：工单被受理/处理/完成时，状态实时推送回聊天窗口；用户可在对话中追问进度、补充信息、评价满意度（不满意自动打回）；
- 受限工具权限：AI 通过 Function Calling 调用后端 API，但所有工具以**当前会话用户身份**执行，只能操作本人工单。

## 架构

```
浏览器（聊天窗口 / 管理后台）
   │  POST 发消息 + SSE 事件流
   ▼
SpringBoot 单体服务
   ├─ 会话管理    会话 / 消息 / 历史上下文
   ├─ RAG 模块    切片 → Embedding API → 向量存 MySQL(LONGBLOB) → JVM 内存暴力余弦 Top-K
   ├─ Agent 编排  意图路由 / Function Calling / 建单决策 / 多轮工具循环
   ├─ 工单状态机  DRAFT→OPEN→PROCESSING→RESOLVED→CLOSED（含打回/取消）
   └─ Tool API    create/query/note/feedback（用户级鉴权，越权拒绝）
   ▼
MySQL（用户/会话/向量/工单/流水）        外部 API：Chat 大模型 + Embedding 模型
```

**为什么不用独立向量数据库？** 知识库规模为万级 chunk，2C2G 内存预算下（MySQL ≈400M + JVM ≈700M）没有余量再跑 Milvus/Qdrant。向量以 LONGBLOB 存 MySQL、全量加载进 JVM 做暴力余弦检索，单次毫秒级，零新增组件；规模增长后可平滑替换为 Qdrant（接口已收敛在 `RagService`）。

## 技术栈

SpringBoot 3.2 · MyBatis-Plus · MySQL 8 · SSE · OpenAI 兼容 API（DeepSeek / DashScope）· 原生 JS 前端

## 快速启动

### 方式一：Docker Compose（推荐，与服务器部署一致）

```bash
# 1. 配置 API Key（对话 + 向量，可不同厂商）
export AI_API_KEY=sk-xxxxxx            # 默认 DeepSeek
export AI_EMBEDDING_API_KEY=sk-xxxxxx  # 默认阿里云 DashScope

# 2. 一键启动 MySQL + 应用
docker compose up -d --build

# 3. 访问
#    聊天助手   http://localhost:8080/index.html
#    管理后台   http://localhost:8080/admin.html
```

### 方式二：本地 IDE 运行

1. 准备 MySQL 8，建库 `rag_ticket`（或依赖连接串中 `createDatabaseIfNotExist=true` 自动建库）；
2. 在 `src/main/resources/application.yml` 或环境变量中配置 `AI_API_KEY`、`AI_EMBEDDING_API_KEY`；
3. 启动 `RagApplication`，建表与种子数据由 `schema.sql` / `data.sql` 自动执行。

### 首次使用（重要）

1. 用 **A0001** 登录管理后台 → 知识库页 → 点击 **「向量补齐」**（为种子知识生成 embedding，只需一次）；
2. 回到聊天页，用 **U1001** 登录即可体验完整流程。

## 演示账号

| 工号 | 姓名 | 角色 |
|---|---|---|
| A0001 | 张文龙 | 管理员（工单处理 + 知识库管理） |
| U1001 | 李雷 | 普通用户 |
| U1002 | 韩梅梅 | 普通用户 |

## 一个完整的体验闭环

1. 用户（U1001）：「办公室网口连不上网，3 楼 A12 工位」→ AI 结合知识库给排查步骤；
2. 用户：「都试过了，还是不行」→ AI 判断需人工处理，调用 `create_ticket` 生成**预填草稿卡片**（类别/标题/描述/紧急度已从对话抽取）；
3. 用户在卡片中补充设备信息，点击确认 → 生成工单号 `GD20260801103045-1234`；
4. 管理员在后台接单处理 → 用户聊天窗口实时收到「处理中（处理人：张文龙）」；
5. 处理完成 → 用户回复「解决了，谢谢」→ AI 调用反馈工具 → 工单关闭；不满意则自动打回重处理。

## 模型配置（全部走 API，服务器不部署模型）

| 环境变量 | 说明 | 默认 |
|---|---|---|
| `AI_API_KEY` | 对话模型 Key（必填） | - |
| `AI_BASE_URL` | 对话模型地址 | `https://api.deepseek.com/v1` |
| `AI_CHAT_MODEL` | 对话模型 | `deepseek-chat` |
| `AI_EMBEDDING_API_KEY` | 向量模型 Key | 同 `AI_API_KEY` |
| `AI_EMBEDDING_BASE_URL` | 向量模型地址 | DashScope 兼容模式 |
| `AI_EMBEDDING_MODEL` | 向量模型 | `text-embedding-v3` |

任意 OpenAI 兼容服务（智谱 / 通义 / Kimi / OpenAI）均可通过上述变量切换。

## 安全说明（原型边界）

- 登录为工号免密（演示用），生产应替换为 SSO / JWT；
- AI 工具权限最小化：只能查询/操作当前用户自己的工单，模型无法越权；
- 工单必须由用户确认提交，AI 不能代替用户做最终决定。

## 服务器部署（腾讯云 2C2G）

```bash
docker compose up -d --build   # 总内存占用约 1.2G：app≈700M + mysql≈400M
```

JVM 已通过 `JAVA_OPTS=-Xmx640m` 限制，MySQL buffer pool 限制 256M，适配小内存机器。
