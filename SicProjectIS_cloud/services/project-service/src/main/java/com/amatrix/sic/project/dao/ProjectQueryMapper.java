package com.amatrix.sic.project.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectQueryMapper {
    List<Map<String,Object>> accessible(@Param("userId") Long userId,@Param("deptId") Long deptId,
        @Param("global") boolean global,@Param("department") boolean department,
        @Param("expert") boolean expert,@Param("finance") boolean finance);
}
