package com.amatrix.sic.iam.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会话版本 MyBatis 接口：按用户加行锁读取当前授权版本，供提交后发布栅栏使用。
 */
@Mapper
public interface SessionMapper {
    /** 读取用户授权版本并加行锁，供数据库版本与 Redis 会话状态协调使用。 */
    List<Long> lockedVersion(@Param("id") Long id);
}
