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
    List<ObjectDeletionJob> pending();
    int complete(@Param("id") long id);
    int failed(@Param("id") long id,@Param("next") Instant next,@Param("error") String error);
    Long pendingCount();
    Long failedCount();
}
