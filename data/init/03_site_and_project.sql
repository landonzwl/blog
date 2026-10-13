-- Site copy (homepage editable fields) + featured projects

CREATE TABLE IF NOT EXISTS t_site_content (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  content_key   VARCHAR(100) NOT NULL,
  content_value TEXT         NOT NULL,
  label         VARCHAR(100) NOT NULL,
  group_name    VARCHAR(50)  NOT NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_key (content_key),
  KEY idx_group_sort (group_name, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_project (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  title         VARCHAR(200) NOT NULL,
  summary       VARCHAR(500) DEFAULT NULL,
  content       MEDIUMTEXT   NOT NULL,
  cover_url     VARCHAR(500) DEFAULT NULL,
  tag           VARCHAR(100) DEFAULT NULL,
  date_label    VARCHAR(100) DEFAULT NULL,
  app_key       VARCHAR(64)  DEFAULT NULL,
  status        TINYINT      NOT NULL DEFAULT 0,
  sort_order    INT          NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET NAMES utf8mb4;

INSERT INTO t_site_content
  (content_key, content_value, label, group_name, sort_order, create_time, update_time, deleted)
VALUES
('site.brand', '张文龙', '站点品牌名', 'nav', 0, NOW(), NOW(), 0),
('nav.projects', '作品', '导航-作品', 'nav', 1, NOW(), NOW(), 0),
('nav.articles', '文章', '导航-文章', 'nav', 2, NOW(), NOW(), 0),
('nav.about', '关于', '导航-关于', 'nav', 3, NOW(), NOW(), 0),
('nav.contact', '联系', '导航-联系', 'nav', 4, NOW(), NOW(), 0),
('hero.eyebrow', 'Software Engineering Portfolio', 'Hero 眉题', 'hero', 1, NOW(), NOW(), 0),
('hero.title', '张文龙', 'Hero 主标题', 'hero', 2, NOW(), NOW(), 0),
('hero.subtitle', '山东科技大学软件工程专业 2027 届本科生\nJava 后端 / Vue3 前端 / 运维部署', 'Hero 副标题', 'hero', 3, NOW(), NOW(), 0),
('hero.desc', '专注于企业级 Web 应用开发与系统部署，熟悉 SpringBoot、MySQL、Redis、Docker 与腾讯云运维。相信扎实的代码与清晰的文档同样重要。', 'Hero 描述', 'hero', 4, NOW(), NOW(), 0),
('hero.cta_primary', '查看作品', 'Hero 主按钮', 'hero', 5, NOW(), NOW(), 0),
('hero.cta_secondary', '联系我', 'Hero 次按钮', 'hero', 6, NOW(), NOW(), 0),
('about.eyebrow', 'About', '关于-眉题', 'about', 1, NOW(), NOW(), 0),
('about.title', '关于我', '关于-标题', 'about', 2, NOW(), NOW(), 0),
('about.lead', '我是张文龙，一名来自山东济南的软件工程专业本科生。', '关于-导语', 'about', 3, NOW(), NOW(), 0),
('about.p1', '在校期间系统学习了 Java、SpringBoot、MyBatis-Plus、MySQL、Redis 等后端技术，也熟悉 Vue3 + TypeScript 前端开发。课外接触了 UDP/Socket 网络报文处理、RAG 大模型应用开发，以及 Docker 容器编排与 Linux 基础运维。', '关于-段落1', 'about', 4, NOW(), NOW(), 0),
('about.p2', '参与过企业实习与实验室横向课题，能够独立完成业务模块编码、接口实现、数据库设计、前后端联调自测与系统部署。同时有两年团委办公室干事经历，具备跨部门沟通与文档整理能力，做事踏实严谨，愿意服从团队岗位安排。', '关于-段落2', 'about', 5, NOW(), NOW(), 0),
('projects.eyebrow', 'Selected Work', '作品-眉题', 'projects', 1, NOW(), NOW(), 0),
('projects.title', '精选作品', '作品-标题', 'projects', 2, NOW(), NOW(), 0),
('projects.sub', '企业实习、个人项目与实验室课题中的实践成果。', '作品-副文案', 'projects', 3, NOW(), NOW(), 0),
('articles.eyebrow', 'Writing', '文章-眉题', 'articles', 1, NOW(), NOW(), 0),
('articles.title', '近期文章', '文章-标题', 'articles', 2, NOW(), NOW(), 0),
('contact.eyebrow', 'Contact', '联系-眉题', 'contact', 1, NOW(), NOW(), 0),
('contact.title', '联系我', '联系-标题', 'contact', 2, NOW(), NOW(), 0),
('contact.desc', '欢迎交流技术问题、实习机会或项目合作。我会尽快回复邮件。', '联系-描述', 'contact', 3, NOW(), NOW(), 0),
('contact.email_label', '邮箱', '联系-邮箱标签', 'contact', 4, NOW(), NOW(), 0),
('contact.email', '1938463598@qq.com', '联系-邮箱', 'contact', 5, NOW(), NOW(), 0),
('contact.phone_label', '电话', '联系-电话标签', 'contact', 6, NOW(), NOW(), 0),
('contact.phone', '195-8897-8742', '联系-电话', 'contact', 7, NOW(), NOW(), 0),
('contact.location_label', '所在地', '联系-地点标签', 'contact', 8, NOW(), NOW(), 0),
('contact.location', '山东 / 济南', '联系-地点', 'contact', 9, NOW(), NOW(), 0),
('site.footer', '© 2025 张文龙. All rights reserved.', '页脚文案', 'site', 1, NOW(), NOW(), 0);

INSERT INTO t_project
  (title, summary, content, cover_url, tag, date_label, app_key, status, sort_order, create_time, update_time, deleted)
VALUES
(
  'RAG 智能工单系统',
  '面向运维故障报修场景的大模型应用原型，支持自然语言问答、知识库检索与自动工单生成，部署于腾讯云服务器。',
  '作品详情页占位内容，后续再完善技术细节与截图。',
  '/assets/image_0_r53wpn.jpg',
  '个人项目',
  '2024.07 ~ 2024.08',
  NULL,
  1,
  1,
  NOW(), NOW(), 0
),
(
  '轨道交通 iART 仿真平台',
  'CTCS-3 级列控系统联合仿真平台，参与 RBC 无线闭塞中心节点开发，实现消息按位编解码、闭塞区间定位与行车许可计算。',
  '定制展示页由前端 showcase「rbc」承载。',
  '/assets/image_1_r53wpn.jpg',
  '实验室课题',
  '2025.07 ~ 2025.08',
  'rbc',
  1,
  2,
  NOW(), NOW(), 0
),
(
  '企业管理系统',
  '覆盖销售、采购、库存、生产、财务、人事与排班。单据和审批存在 MySQL，排班由 CP-SAT 求解，打开作品即可试用。',
  '定制展示页由前端 showcase「np」承载。',
  '/assets/image_2_r53wpn.jpg',
  '校内实践',
  '2024.09 ~ 2026.10',
  'np',
  1,
  3,
  NOW(), NOW(), 0
);
