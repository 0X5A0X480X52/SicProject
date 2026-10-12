package com.amatrix.sic.material.service.cleanup;

import com.amatrix.sic.material.storage.MinioObjectStorage;

import java.time.Instant;
import java.util.List;

import com.amatrix.sic.material.dao.ObjectDeletionMapper;
import com.amatrix.sic.material.entity.ObjectDeletionJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定时读取 PENDING 对象删除任务，删除 MinIO 对象后标记完成；异常保存错误、次数及指数退避时间，允许幂等重试。
 */
@Service
public final class ObjectDeletionService {
    private final ObjectDeletionMapper mapper;
    private final MinioObjectStorage storage;

    public ObjectDeletionService(ObjectDeletionMapper mapper, MinioObjectStorage storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    /**
     * 按计划时间处理待删除对象；成功标记完成，失败记录次数并安排退避，避免把对象存储等待放入材料删除事务。
     */
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
