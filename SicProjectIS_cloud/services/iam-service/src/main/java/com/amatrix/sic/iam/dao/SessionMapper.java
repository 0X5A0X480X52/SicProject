package com.amatrix.sic.iam.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionMapper {
    List<Long> lockedVersion(@Param("id") Long id);
}
