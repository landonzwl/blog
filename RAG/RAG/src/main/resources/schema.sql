-- RAG 智能工单系统 表结构（MySQL 8）

CREATE TABLE IF NOT EXISTS t_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_no     VARCHAR(32)  NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    dept       VARCHAR(64)  NULL,
    phone      VARCHAR(32)  NULL,
    email      VARCHAR(128) NULL,
    role       VARCHAR(16)  NOT NULL DEFAULT 'USER',
    skills     VARCHAR(255) NULL COMMENT '处理技能标签，逗号分隔，如: 网络,硬件。仅对 ADMIN 有意义',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_emp_no (emp_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_chat_session (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(128) NOT NULL DEFAULT '新对话',
    status     VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_chat_message (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT      NOT NULL,
    role       VARCHAR(16) NOT NULL,
    content    TEXT        NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msg_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_title  VARCHAR(255) NOT NULL,
    chunk_index INT         NOT NULL DEFAULT 0,
    content    TEXT         NOT NULL,
    embedding  LONGBLOB     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chunk (doc_title, chunk_index)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_ticket (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no   VARCHAR(32)  NULL,
    session_id  BIGINT       NULL,
    user_id     BIGINT       NOT NULL,
    category    VARCHAR(32)  NOT NULL DEFAULT '其他',
    title       VARCHAR(255) NOT NULL,
    description TEXT         NULL,
    urgency     VARCHAR(16)  NOT NULL DEFAULT '普通',
    device_info VARCHAR(255) NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    assignee    VARCHAR(64)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ticket_user (user_id),
    KEY idx_ticket_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_ticket_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id  BIGINT        NOT NULL,
    action     VARCHAR(32)   NOT NULL,
    operator   VARCHAR(64)   NOT NULL DEFAULT 'system',
    detail     VARCHAR(1024) NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_log_ticket (ticket_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
