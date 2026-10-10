package com.amatrix.sic.project.dao;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.*;

@Mapper
public interface ProjectScopeMapper {
    List<ProjectScopeRow> projects(@Param("ids") Collection<Long> ids);
    List<Long> memberships(@Param("userId") Long userId,@Param("ids") Collection<Long> ids);
    List<ProjectGrantScopeRow> grants(@Param("userId") Long userId,@Param("ids") Collection<Long> ids);
}
