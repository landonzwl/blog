CREATE TABLE IF NOT EXISTS np_dept (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS np_staff (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  dept_id VARCHAR(32) NOT NULL,
  skill VARCHAR(255) NOT NULL,
  max_minutes INT NOT NULL,
  CONSTRAINT fk_np_staff_dept FOREIGN KEY (dept_id) REFERENCES np_dept (id)
);

CREATE TABLE IF NOT EXISTS np_task (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  dept_id VARCHAR(32) NOT NULL,
  start_ms BIGINT NOT NULL,
  end_ms BIGINT NOT NULL,
  start_text VARCHAR(20) NOT NULL,
  end_text VARCHAR(20) NOT NULL,
  headcount INT NOT NULL,
  skill VARCHAR(32) NOT NULL,
  priority INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  origin VARCHAR(16) NOT NULL DEFAULT 'live',
  CONSTRAINT fk_np_task_dept FOREIGN KEY (dept_id) REFERENCES np_dept (id)
);

CREATE TABLE IF NOT EXISTS np_assignment (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  staff_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  source VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  CONSTRAINT fk_np_assignment_staff FOREIGN KEY (staff_id) REFERENCES np_staff (id),
  CONSTRAINT fk_np_assignment_task FOREIGN KEY (task_id) REFERENCES np_task (id)
);

CREATE TABLE IF NOT EXISTS np_block (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  staff_id VARCHAR(32) NOT NULL,
  start_ms BIGINT NOT NULL,
  end_ms BIGINT NOT NULL,
  start_text VARCHAR(20) NOT NULL,
  end_text VARCHAR(20) NOT NULL,
  reason VARCHAR(20) NOT NULL,
  CONSTRAINT fk_np_block_staff FOREIGN KEY (staff_id) REFERENCES np_staff (id)
);

CREATE TABLE IF NOT EXISTS np_gap (
  pos INT NOT NULL PRIMARY KEY,
  message VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS np_skill (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(32) NOT NULL,
  UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS np_post (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(32) NOT NULL,
  UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS np_staff_skill (
  staff_id VARCHAR(32) NOT NULL,
  skill VARCHAR(32) NOT NULL,
  PRIMARY KEY (staff_id, skill),
  CONSTRAINT fk_np_staff_skill_staff FOREIGN KEY (staff_id) REFERENCES np_staff (id)
);

CREATE TABLE IF NOT EXISTS np_state (
  id INT NOT NULL PRIMARY KEY,
  next_seq INT NOT NULL,
  admin_notice VARCHAR(255) NOT NULL,
  staff_notice VARCHAR(255) NOT NULL,
  variance DECIMAL(12, 2) NOT NULL,
  current_staff_id VARCHAR(32) NOT NULL,
  version INT NOT NULL DEFAULT 0
);
