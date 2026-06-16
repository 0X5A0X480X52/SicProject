package com.amatrix.sicprojectis_backend.structured.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sicprojectis_backend.structured.entity.NoticeRecord;

@Mapper
public interface NoticeRecordDao {
    NoticeRecord selectById(@Param("noticeId") Long noticeId);
    List<NoticeRecord> selectByModuleType(@Param("moduleType") String moduleType);
    List<NoticeRecord> selectAll();
    int insert(NoticeRecord record);
    int updateById(NoticeRecord record);
    int deleteById(@Param("noticeId") Long noticeId);
}
