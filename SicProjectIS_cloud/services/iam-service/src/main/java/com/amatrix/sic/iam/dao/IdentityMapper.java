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
    /** 按用户名定位用户 ID，供登录身份读取和注册重名检查使用。 */
    Long idByUsername(@Param("username") String username);
    /** 读取用户账号、密码哈希、部门、启用状态及授权版本，供认证和身份组装使用。 */
    IdentityRow identity(@Param("id") Long id);
    /** 批量读取用户身份摘要及授权版本，按用户 ID 排序供内部身份接口使用。 */
    List<SummaryRow> summaries(@Param("ids") List<Long> ids);
    /** 批量读取用户角色编码，按用户 ID 和角色编码排序供身份摘要组装使用。 */
    List<UserRoleRow> rolesForUsers(@Param("ids") List<Long> ids);
    int insertUser(Map<String,Object> user);
    int assignRole(@Param("id") Long id,@Param("code") String code);
    /** 按角色编码读取并锁定角色行，供初始化或角色分配事务使用。 */
    Long lockRole(@Param("code") String code);
    /** 按用户 ID 读取并锁定账号行，供身份变更事务协调使用。 */
    Long lockUser(@Param("id") Long id);
    /** 按用户 ID 查询角色编码，供 JWT 和身份上下文组装使用。 */
    List<String> roles(@Param("id") Long id);
    /** 查询用户通过角色获得的去重权限编码，供 JWT 权限声明组装使用。 */
    List<String> permissions(@Param("id") Long id);
}
