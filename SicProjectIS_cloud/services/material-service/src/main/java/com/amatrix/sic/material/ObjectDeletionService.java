package com.amatrix.sic.material;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Durable, idempotent cleanup of objects whose metadata deletion has already committed. */
@Service
public final class ObjectDeletionService {
    private final JdbcTemplate jdbc;
    private final MinioObjectStorage storage;

    public ObjectDeletionService(JdbcTemplate jdbc, MinioObjectStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${sic.material.object-cleanup-ms:5000}")
    public void deletePendingObjects() {
        List<Job> jobs = jdbc.query("""
                SELECT id,object_key,attempts FROM object_deletion_job
                 WHERE status='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP)
                 ORDER BY created_at LIMIT 100
                """, (rs, row) -> new Job(rs.getLong("id"), rs.getString("object_key"), rs.getInt("attempts")));
        for (Job job : jobs) delete(job);
    }

    private void delete(Job job) {
        try {
            storage.remove(job.objectKey());
            jdbc.update("""
                    UPDATE object_deletion_job SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,last_error=NULL
                     WHERE id=? AND status='PENDING'
                    """, job.id());
        } catch (RuntimeException ex) {
            long delaySeconds = Math.min(300, 1L << Math.min(job.attempts(), 8));
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (message.length() > 1000) message = message.substring(0, 1000);
            jdbc.update("""
                    UPDATE object_deletion_job SET attempts=attempts+1,next_attempt_at=?,last_error=?
                     WHERE id=? AND status='PENDING'
                    """, Timestamp.from(Instant.now().plusSeconds(delaySeconds)), message, job.id());
        }
    }

    private record Job(long id, String objectKey, int attempts) {
    }
}
