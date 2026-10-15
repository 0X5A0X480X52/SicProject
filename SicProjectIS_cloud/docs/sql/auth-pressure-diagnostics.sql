-- Read-only diagnostics for MySQL 8.4. Run during the incident and preserve timestamps.
-- Requires access to Performance Schema / InnoDB diagnostics; no application writes.
-- Counters are cumulative: compare consecutive snapshots, not just absolute values.
-- Statement text can contain business values: redact it before sharing externally.

SELECT NOW(6) AS captured_at, VERSION() AS mysql_version;

SHOW GLOBAL STATUS WHERE Variable_name IN (
  'Threads_connected', 'Threads_running', 'Max_used_connections',
  'Aborted_connects', 'Connection_errors_max_connections',
  'Innodb_row_lock_current_waits', 'Innodb_row_lock_waits',
  'Innodb_row_lock_time', 'Innodb_deadlocks',
  'Slow_queries', 'Questions'
);

SHOW GLOBAL VARIABLES WHERE Variable_name IN (
  'max_connections', 'innodb_lock_wait_timeout', 'transaction_isolation',
  'slow_query_log', 'long_query_time', 'performance_schema'
);

-- Waiting transaction -> blocking transaction, table and index.
SELECT NOW(6) AS captured_at,
       w.REQUESTING_ENGINE_TRANSACTION_ID AS waiting_trx,
       wt.PROCESSLIST_ID AS waiting_connection,
       w.BLOCKING_ENGINE_TRANSACTION_ID AS blocking_trx,
       bt.PROCESSLIST_ID AS blocking_connection,
       requested.OBJECT_SCHEMA AS table_schema,
       requested.OBJECT_NAME AS table_name,
       requested.INDEX_NAME AS index_name,
       requested.LOCK_TYPE AS waiting_lock_type,
       requested.LOCK_MODE AS waiting_lock_mode,
       blocking.LOCK_MODE AS blocking_lock_mode
FROM performance_schema.data_lock_waits w
LEFT JOIN performance_schema.data_locks requested
  ON requested.ENGINE=w.ENGINE
 AND requested.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
LEFT JOIN performance_schema.data_locks blocking
  ON blocking.ENGINE=w.ENGINE
 AND blocking.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID
LEFT JOIN performance_schema.threads wt ON wt.THREAD_ID=w.REQUESTING_THREAD_ID
LEFT JOIN performance_schema.threads bt ON bt.THREAD_ID=w.BLOCKING_THREAD_ID
ORDER BY waiting_trx, blocking_trx
LIMIT 100;

-- Long-lived transactions may have no current SQL while the application waits on RPC.
SELECT NOW(6) AS captured_at, trx_id, trx_mysql_thread_id, trx_state,
       trx_started, TIMESTAMPDIFF(SECOND,trx_started,NOW()) AS transaction_age_seconds,
       trx_wait_started, trx_rows_locked, trx_rows_modified, trx_query
FROM information_schema.innodb_trx
ORDER BY trx_started
LIMIT 50;

-- Active statements, excluding this diagnostic connection.
SELECT NOW(6) AS captured_at, PROCESSLIST_ID AS connection_id,
       PROCESSLIST_USER AS connection_user, PROCESSLIST_DB AS database_name,
       PROCESSLIST_COMMAND AS command, PROCESSLIST_TIME AS state_age_seconds,
       PROCESSLIST_STATE AS state, LEFT(PROCESSLIST_INFO,1000) AS statement_text
FROM performance_schema.threads
WHERE TYPE='FOREGROUND'
  AND PROCESSLIST_COMMAND <> 'Sleep'
  AND PROCESSLIST_ID <> CONNECTION_ID()
ORDER BY PROCESSLIST_TIME DESC
LIMIT 50;

-- Expensive normalized statement digests; timers are picoseconds.
SELECT NOW(6) AS captured_at, SCHEMA_NAME, DIGEST,
       LEFT(DIGEST_TEXT,1000) AS normalized_statement, COUNT_STAR AS executions,
       ROUND(SUM_TIMER_WAIT/1000000000000,3) AS total_seconds,
       ROUND(AVG_TIMER_WAIT/1000000000,3) AS average_ms,
       SUM_ROWS_EXAMINED AS rows_examined, SUM_ROWS_SENT AS rows_sent,
       SUM_ERRORS AS errors, SUM_WARNINGS AS warnings
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME IS NOT NULL
  AND SCHEMA_NAME NOT IN ('mysql','sys','performance_schema','information_schema')
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 30;

-- Latest detected deadlock and InnoDB internal status; this is a snapshot.
SHOW ENGINE INNODB STATUS;

-- Also collect per-service Hikari active/pending/acquire metrics and thread dumps.
-- EXPLAIN hot SELECTs separately with representative IDs and current statistics.
-- Do not run EXPLAIN ANALYZE on write statements or execute writes from this file.
