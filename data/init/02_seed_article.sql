-- P2 seed articles
SET NAMES utf8mb4;

INSERT INTO t_article
  (title, summary, content, cover_url, tags, status, view_count, published_at, create_time, update_time, deleted)
VALUES
(
  'SpringBoot 项目中的全局异常处理与接口规范',
  '统一 Result 响应体、业务异常与全局异常处理器的落地实践。',
  '## 背景\n\n在前后端分离项目中，接口返回格式不统一会增加联调成本。\n\n## 方案\n\n使用 Result 统一包装，配合 BusinessException 与 RestControllerAdvice。\n\n## 小结\n\n规范先行，后续模块只需抛异常与返回 Result。',
  NULL,
  '后端工程,SpringBoot',
  1,
  0,
  '2025-09-01 10:00:00',
  '2025-09-01 10:00:00',
  '2025-09-01 10:00:00',
  0
),
(
  '2C2G 上的全栈上线：从静态 Nginx 到 Docker Compose 生产栈',
  '在约 2C2G 的腾讯云机器上，把静态 Nginx 站升级为 Vue3 + Spring Boot + MySQL + Redis 的可重启自愈生产栈，并给出可验证的验收闭环。',
  '## 这篇文章想证明什么\n\n不是「照着教程敲了一遍 docker run」，而是我能独立完成一次**可上线、可验收、可重启自愈**的工程交付：\n\n- 把「只能看静态页」的站点，升级为 **Vue3 SPA + Spring Boot API + MySQL + Redis** 的完整链路\n- 在约 **2C2G** 的腾讯云机器上做资源预算，而不是默认「本机多大就往上堆」\n- 用 **多阶段构建、健康检查、环境变量隔离、只暴露 80** 等约束，体现生产意识\n- 用可复现的验收命令证明系统真的活着，而不是「容器起了就算成功」\n\n线上演示可直接打开本站首页与 `/admin/ui`。仓库：https://github.com/landonzwl/blog\n\n---\n\n## 1. 问题：旧站为什么不够\n\n上线前只有静态 blog-web 监听 80，无 Compose、无后端、无数据库，无法支撑库驱动内容与编辑台。产品目标明确后，选择**替换式上线**比在旧镜像上打补丁更干净。\n\n## 2. 约束：先定边界再写编排\n\n1. 约 2G 内存：服务必须有 mem_limit，JVM 限制堆大小\n2. MySQL 5.7：与约定一致\n3. 对外只开 80：数据库与 Redis 留在容器网络\n4. 密钥不进仓库：仅保留 .env.example\n5. 重启可恢复：unless-stopped\n\n## 3. 架构\n\n```\n浏览器 :80 → Nginx(blog-web)\n  / → Vue3 SPA\n  /api/* → blog-api:8080 → MySQL / Redis\n```\n\n入口层、应用层、数据层边界清楚，方便讲清职责。\n\n## 4. 关键设计亮点\n\n- **多阶段构建**：Maven/Node 构建，运行时只留 JRE 与 Nginx\n- **健康检查依赖**：api 等 MySQL healthy，避免首次启动竞态\n- **内存预算写进编排**：Xmx 与 mem_limit 对齐 2G 机器\n- **同域反代**：省 CORS，SPA history + /api 对齐 context-path\n- **init 与 volume 分离**：空库才灌 SQL，业务数据持久化\n\n## 5. 迁移与验收\n\n停旧站 → 备份目录 → 克隆新仓 → 配 .env → compose up --build → curl 验收 → reboot 验证自愈。\n\n验收证据：`compose ps`（mysql healthy）、`/api/ping` 200、首页 Nginx 200，再点编辑台写库。\n\n## 6. 取舍与能力沉淀\n\n已达成完整站上线；HTTPS、正式鉴权、监控主动延期，避免首版失控。\n\n对应能力：全栈闭环、约束驱动设计、生产意识、迁移判断、用证据表达交付。',
  NULL,
  'Docker,生产部署,SpringBoot,Nginx,资源约束',
  1,
  0,
  '2026-10-09 16:00:00',
  '2026-10-09 16:00:00',
  '2026-10-09 16:00:00',
  0
),
(
  '我的软件工程学习路线与实习复盘',
  '从课程到实习项目的能力沉淀与反思。',
  '## 路线\n\nJava 基础 -> Spring Boot -> 数据库与缓存 -> 部署运维。\n\n## 实习收获\n\n独立完成模块编码、接口联调与文档整理。',
  NULL,
  '成长',
  1,
  0,
  '2025-06-01 10:00:00',
  '2025-06-01 10:00:00',
  '2025-06-01 10:00:00',
  0
),
(
  'RAG 知识库检索与智能工单生成思路',
  '面向运维场景的检索增强与工单草稿生成。',
  '## 思路\n\n知识库切片检索 + 大模型总结，输出结构化工单草稿。\n\n## 后续\n\n可与工单模块衔接，形成提问 -> 草稿 -> 人工确认闭环。',
  NULL,
  'AI应用,RAG',
  1,
  0,
  '2025-04-01 10:00:00',
  '2025-04-01 10:00:00',
  '2025-04-01 10:00:00',
  0
);
