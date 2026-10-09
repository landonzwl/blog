-- 已有库增量迁移（空库请直接用 data/init，勿重复执行本脚本）
ALTER TABLE t_project
  ADD COLUMN app_key VARCHAR(64) DEFAULT NULL AFTER date_label;

UPDATE t_project
SET app_key = 'rbc'
WHERE deleted = 0
  AND (title LIKE '%仿真%' OR title LIKE '%iART%' OR title LIKE '%RBC%')
  AND (app_key IS NULL OR app_key = '');
