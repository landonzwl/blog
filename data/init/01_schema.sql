-- P2 article table
CREATE TABLE IF NOT EXISTS t_article (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  title         VARCHAR(200) NOT NULL,
  summary       VARCHAR(500) DEFAULT NULL,
  content       MEDIUMTEXT   NOT NULL,
  cover_url     VARCHAR(500) DEFAULT NULL,
  tags          VARCHAR(200) DEFAULT NULL,
  status        TINYINT      NOT NULL DEFAULT 0,
  view_count    INT          NOT NULL DEFAULT 0,
  published_at  DATETIME     DEFAULT NULL,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status_published (status, published_at),
  KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
