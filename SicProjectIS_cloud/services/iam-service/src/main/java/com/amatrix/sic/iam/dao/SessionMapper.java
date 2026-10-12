package com.amatrix.sic.iam.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会话版本 MyBatis 接口：按用户加行锁读取当前授权版本，供提交后发布栅栏使用。
 */
@Mapper
public interface SessionMapper {
    List<Long> lockedVersion(@Param("id") Long id);
}
