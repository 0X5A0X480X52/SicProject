package com.amatrix.sic.project.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.UserSnapshotRow;
import com.amatrix.sic.contracts.UserSummary;

@Mapper
public interface UserSnapshotMapper {
    UserSnapshotRow user(@Param("id") Long id);
    List<UserSnapshotRow> users(@Param("ids") List<Long> ids);
    List<UserSnapshotRow> enabled();
    int upsert(@Param("user") UserSummary user,@Param("rolesJson") String rolesJson);
}
