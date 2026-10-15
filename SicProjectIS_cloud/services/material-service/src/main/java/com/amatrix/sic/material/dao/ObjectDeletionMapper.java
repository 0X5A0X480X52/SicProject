package com.amatrix.sic.material.dao;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.material.entity.ObjectDeletionJob;

/**
 * 对象删除任务 MyBatis 接口：取 PENDING 任务、标记完成或失败退避，以及待处理与失败计数。
 */
@Mapper
public interface ObjectDeletionMapper {
    /** 按创建时间取最多 100 条已到重试时间的 PENDING 对象删除任务，供清理任务执行。 */
    List<ObjectDeletionJob> pending();
    int complete(@Param("id") long id);
    int failed(@Param("id") long id,@Param("next") Instant next,@Param("error") String error);
    /** 统计尚未完成的 PENDING 对象删除任务数，供积压指标使用。 */
    Long pendingCount();
    /** 统计仍为 PENDING 且已有失败尝试的任务数，供删除失败积压指标使用。 */
    Long failedCount();
}
