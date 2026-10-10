#!/bin/bash
set -euo pipefail

# Passwords are supplied by Compose; no credential is stored in this script or Git.
mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE DATABASE IF NOT EXISTS sic_iam CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS sic_project CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS sic_workflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS sic_material CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS sic_notification CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'sic_iam'@'%' IDENTIFIED BY '${IAM_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'sic_project'@'%' IDENTIFIED BY '${PROJECT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'sic_workflow'@'%' IDENTIFIED BY '${WORKFLOW_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'sic_material'@'%' IDENTIFIED BY '${MATERIAL_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'sic_notification'@'%' IDENTIFIED BY '${NOTIFICATION_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON sic_iam.* TO 'sic_iam'@'%';
GRANT ALL PRIVILEGES ON sic_project.* TO 'sic_project'@'%';
GRANT ALL PRIVILEGES ON sic_workflow.* TO 'sic_workflow'@'%';
GRANT ALL PRIVILEGES ON sic_material.* TO 'sic_material'@'%';
GRANT ALL PRIVILEGES ON sic_notification.* TO 'sic_notification'@'%';
FLUSH PRIVILEGES;
EOSQL

# Docker calls this only for a new data volume. Existing volumes are never replayed.
for domain in iam project workflow material notification; do
    table_count=$(MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot -N \
        -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sic_${domain}'")
    if [[ "${table_count}" != "0" ]]; then
        echo "Refusing full initialization of nonempty schema sic_${domain}" >&2
        exit 1
    fi
    MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot "sic_${domain}" \
        < "/sic-release/schema/${domain}.sql"
done
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot sic_iam < /sic-release/seed/iam.sql
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot sic_workflow < /sic-release/seed/workflow-assets.sql
