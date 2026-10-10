package com.amatrix.sic.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ObjectDeletionServiceTest {
    private JdbcTemplate jdbc;
    private MinioObjectStorage storage;
    private ObjectDeletionService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:object-delete-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE object_deletion_job(
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,material_version_id BIGINT UNIQUE,object_key VARCHAR(1024),
                    status VARCHAR(32),attempts INT DEFAULT 0,next_attempt_at TIMESTAMP,last_error VARCHAR(1000),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,completed_at TIMESTAMP)
                """);
        storage = mock(MinioObjectStorage.class);
        service = new ObjectDeletionService(jdbc, storage);
    }

    @Test
    void removesPendingObjectAndMarksJobComplete() {
        insert(101L, "projects/7/report.pdf");

        service.deletePendingObjects();

        verify(storage).remove("projects/7/report.pdf");
        assertThat(value("status", 101L)).isEqualTo("COMPLETED");
        assertThat(value("completed_at", 101L)).isNotNull();
    }

    @Test
    void recordsFailureAndCanIdempotentlyRetryLater() {
        insert(102L, "projects/7/evidence.pdf");
        doThrow(new IllegalStateException("MinIO unavailable")).doNothing()
                .when(storage).remove("projects/7/evidence.pdf");

        service.deletePendingObjects();

        assertThat(value("status", 102L)).isEqualTo("PENDING");
        assertThat(value("attempts", 102L)).isEqualTo(1);
        assertThat(value("last_error", 102L)).isEqualTo("MinIO unavailable");
        assertThat(value("next_attempt_at", 102L)).isNotNull();

        jdbc.update("UPDATE object_deletion_job SET next_attempt_at=NULL WHERE material_version_id=102");
        service.deletePendingObjects();

        verify(storage, times(2)).remove("projects/7/evidence.pdf");
        assertThat(value("status", 102L)).isEqualTo("COMPLETED");
    }

    private void insert(Long versionId, String key) {
        jdbc.update("INSERT INTO object_deletion_job(material_version_id,object_key,status) VALUES (?,?,'PENDING')",
                versionId, key);
    }

    private Object value(String column, Long versionId) {
        return jdbc.queryForObject("SELECT " + column + " FROM object_deletion_job WHERE material_version_id=?",
                Object.class, versionId);
    }
}
