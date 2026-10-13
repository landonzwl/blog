CREATE TABLE IF NOT EXISTS np_erp_doc (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  doc_type VARCHAR(32) NOT NULL,
  sort_no INT NOT NULL,
  origin VARCHAR(16) NOT NULL DEFAULT 'business',
  payload LONGTEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS np_erp_state (
  id INT NOT NULL PRIMARY KEY,
  next_seq INT NOT NULL,
  notice VARCHAR(255) NOT NULL,
  version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS np_record (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  entity_code VARCHAR(32) NOT NULL,
  sort_no INT NOT NULL,
  origin VARCHAR(16) NOT NULL DEFAULT 'business',
  KEY idx_np_record_entity (entity_code, sort_no)
);

CREATE TABLE IF NOT EXISTS np_cell (
  record_id VARCHAR(32) NOT NULL,
  field_key VARCHAR(64) NOT NULL,
  value_text TEXT NULL,
  value_num DECIMAL(18, 4) NULL,
  PRIMARY KEY (record_id, field_key),
  CONSTRAINT fk_np_cell_record FOREIGN KEY (record_id) REFERENCES np_record (id)
);

CREATE TABLE IF NOT EXISTS np_meta_entity (
  code VARCHAR(32) NOT NULL PRIMARY KEY,
  title VARCHAR(64) NOT NULL,
  module_name VARCHAR(32) NOT NULL,
  code_prefix VARCHAR(8) NOT NULL DEFAULT '',
  create_status VARCHAR(32) NOT NULL,
  saved_text VARCHAR(64) NOT NULL,
  builtin TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS np_meta_field (
  entity_code VARCHAR(32) NOT NULL,
  field_key VARCHAR(64) NOT NULL,
  label VARCHAR(64) NOT NULL,
  data_type VARCHAR(16) NOT NULL,
  required_flag TINYINT NOT NULL DEFAULT 0,
  sort_no INT NOT NULL,
  options_text VARCHAR(255) NOT NULL DEFAULT '',
  PRIMARY KEY (entity_code, field_key)
);

CREATE TABLE IF NOT EXISTS np_meta_action (
  entity_code VARCHAR(32) NOT NULL,
  action_key VARCHAR(32) NOT NULL,
  label VARCHAR(32) NOT NULL,
  when_status VARCHAR(64) NOT NULL DEFAULT '',
  target_status VARCHAR(32) NOT NULL DEFAULT '',
  PRIMARY KEY (entity_code, action_key)
);

CREATE TABLE IF NOT EXISTS np_meta_retired (
  entity_code VARCHAR(32) NOT NULL,
  field_key VARCHAR(64) NOT NULL,
  PRIMARY KEY (entity_code, field_key)
);

CREATE TABLE IF NOT EXISTS np_org (
  id INT NOT NULL PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  sites VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS np_file (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  record_id VARCHAR(32) NOT NULL,
  file_name VARCHAR(128) NOT NULL,
  mime VARCHAR(128) NOT NULL,
  content LONGBLOB NOT NULL,
  KEY idx_np_file_record (record_id)
);
