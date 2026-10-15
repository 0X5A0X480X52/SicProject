package com.amatrix.sic.material;

import com.amatrix.sic.material.service.MaterialApplicationService;
import com.amatrix.sic.material.policy.MaterialAccessPolicy;
import com.amatrix.sic.material.storage.MinioObjectStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.messaging.OutboxService;

class MaterialDeletionTransactionTest {
    private JdbcTemplate jdbc;
    private MinioObjectStorage storage;
    private OutboxService outbox;
    private MaterialApplicationService service;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:material-delete-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE material(id BIGINT PRIMARY KEY,project_id BIGINT)");
        jdbc.execute("""
                CREATE TABLE material_version(id BIGINT PRIMARY KEY,material_id BIGINT,object_key VARCHAR(1024),
                    original_filename VARCHAR(255),content_type VARCHAR(100),size_bytes BIGINT,file_status VARCHAR(32))
                """);
        jdbc.execute("CREATE TABLE generated_document(material_version_id BIGINT)");
        jdbc.execute("CREATE TABLE material_reservation(reservation_id VARCHAR(36),status VARCHAR(32))");
        jdbc.execute("CREATE TABLE material_reservation_item(reservation_id VARCHAR(36),material_version_id BIGINT)");
        jdbc.execute("""
                CREATE TABLE object_deletion_job(id BIGINT AUTO_INCREMENT PRIMARY KEY,material_version_id BIGINT UNIQUE,
                    object_key VARCHAR(1024),status VARCHAR(32),attempts INT DEFAULT 0,next_attempt_at TIMESTAMP,
                    last_error VARCHAR(1000),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,completed_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO material VALUES (1,7)");
        jdbc.update("INSERT INTO material_version VALUES (101,1,'projects/7/evidence.pdf','evidence.pdf',"
                + "'application/pdf',128,'ACTIVE')");
        storage = mock(MinioObjectStorage.class);
        outbox = mock(OutboxService.class);
        MaterialAccessPolicy access = mock(MaterialAccessPolicy.class);
        when(access.canAccess(any(), eq(7L), eq("MATERIAL_DELETE"))).thenReturn(true);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new MaterialApplicationService(MaterialTestMapper.create(jdbc.getDataSource(),com.amatrix.sic.material.dao.MaterialMapper.class,"mapper/material/MaterialMapper.xml"),storage, access, outbox, transactions);
    }

    @Test
    void outboxFailureRollsBackMetadataWithoutDeletingTheObject() {
        doThrow(new IllegalStateException("outbox unavailable")).when(outbox).enqueue(anyString(), any());

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> service.delete(actor(), 101L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outbox unavailable");

        assertThat(fileStatus()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM object_deletion_job", Integer.class)).isZero();
        verify(storage, never()).remove(anyString());
    }

    @Test
    void committedDeletionCreatesADurableCleanupJobButDoesNotDeleteInsideTheTransaction() {
        transactions.executeWithoutResult(status -> service.delete(actor(), 101L));

        assertThat(fileStatus()).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT object_key FROM object_deletion_job WHERE material_version_id=101",
                String.class)).isEqualTo("projects/7/evidence.pdf");
        verify(storage, never()).remove(anyString());
        verify(outbox).enqueue(eq("material.changed"), any());
    }

    private String fileStatus() {
        return jdbc.queryForObject("SELECT file_status FROM material_version WHERE id=101", String.class);
    }

    private static ActorContext actor() {
        return new ActorContext(42L, "owner", 3L, "Science", List.of("PROJECT_LEADER"), List.of(),
                "token", 1L);
    }
}
