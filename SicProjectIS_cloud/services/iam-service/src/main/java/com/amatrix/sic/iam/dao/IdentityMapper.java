package com.amatrix.sic.iam.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.iam.entity.*;

/**
 * 身份 MyBatis 接口：按用户名或 ID 取账号、批量用户摘要与角色、插入用户并分配角色，以及行锁与角色/权限查询。
 */
@Mapper
public interface IdentityMapper {
    Long idByUsername(@Param("username") String username);
    IdentityRow identity(@Param("id") Long id);
    List<SummaryRow> summaries(@Param("ids") List<Long> ids);
    List<UserRoleRow> rolesForUsers(@Param("ids") List<Long> ids);
    int insertUser(Map<String,Object> user);
    int assignRole(@Param("id") Long id,@Param("code") String code);
    Long lockRole(@Param("code") String code);
    Long lockUser(@Param("id") Long id);
    List<String> roles(@Param("id") Long id);
    List<String> permissions(@Param("id") Long id);
}
