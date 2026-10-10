package com.amatrix.sic.material.dao;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.material.entity.ObjectDeletionJob;

@Mapper
public interface ObjectDeletionMapper {
    List<ObjectDeletionJob> pending();
    int complete(@Param("id") long id);
    int failed(@Param("id") long id,@Param("next") Instant next,@Param("error") String error);
    Long pendingCount();
    Long failedCount();
}
