package com.amatrix.sic.material.service.cleanup;

import com.amatrix.sic.material.storage.MinioObjectStorage;

import java.time.Instant;
import java.util.List;

import com.amatrix.sic.material.dao.ObjectDeletionMapper;
import com.amatrix.sic.material.entity.ObjectDeletionJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Durable, idempotent cleanup of objects whose metadata deletion has already committed. */
@Service
public final class ObjectDeletionService {
    private final ObjectDeletionMapper mapper;
    private final MinioObjectStorage storage;

    public ObjectDeletionService(ObjectDeletionMapper mapper, MinioObjectStorage storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${sic.material.object-cleanup-ms:5000}")
    public void deletePendingObjects() {
        List<ObjectDeletionJob> jobs=mapper.pending();
        for (ObjectDeletionJob job : jobs) delete(job);
    }

    private void delete(ObjectDeletionJob job) {
        try {
            storage.remove(job.objectKey());
            mapper.complete(job.id());
        } catch (RuntimeException ex) {
            long delaySeconds = Math.min(300, 1L << Math.min(job.attempts(), 8));
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (message.length() > 1000) message = message.substring(0, 1000);
            mapper.failed(job.id(),Instant.now().plusSeconds(delaySeconds),message);
        }
    }

}
