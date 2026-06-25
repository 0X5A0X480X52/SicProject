package com.amatrix.sicprojectis_backend.system;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class SchemaCompatibilityInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public SchemaCompatibilityInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        addColumnIfMissing("expert_review_batch", "rank_no", "INT");
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) throws SQLException {
        if (!tableExists(tableName) || columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + columnDefinition);
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String[] tablePatterns = { tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT) };
            for (String tablePattern : tablePatterns) {
                try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, tablePattern, null)) {
                    if (tables.next()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String[] tablePatterns = { tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT) };
            for (String tablePattern : tablePatterns) {
                if (columnExists(metaData, connection.getCatalog(), tablePattern, columnName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String catalog, String tablePattern, String columnName)
            throws SQLException {
        try (ResultSet columns = metaData.getColumns(catalog, null, tablePattern, null)) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
