-- 仅对已使用旧版 graph_build_task 建表语句的数据库执行一次。
ALTER TABLE `graph_build_task`
    DROP INDEX `idx_graph_due`,
    DROP COLUMN `attempts`,
    DROP COLUMN `next_retry_at`;
