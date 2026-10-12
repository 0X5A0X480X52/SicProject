package com.amatrix.sic.iam.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.iam.entity.*;

/**
 * 管理端 MyBatis 接口：用户筛选与详情、角色权限矩阵、部门树与成员、角色和部门 ID 解析、授权版本锁定与递增，以及用户角色/权限/部门的增删改。
 */
@Mapper
public interface AdminMapper {
    List<AdminUserRow> users(@Param("filter") Map<String,Object> filter);
    AdminUserRow user(@Param("id")Long id);
    List<Map<String,Object>> roles();
    List<Map<String,Object>> permissions();
    List<Map<String,Object>> permissionMatrix();
    List<Map<String,Object>> departments(@Param("scope")Long scope);
    Map<String,Object> department(@Param("id")Long id);
    Long roleId(@Param("code")String code);
    Long departmentId(@Param("code")String code);
    Long userId(@Param("id")Long id);
    Long userDepartment(@Param("id")Long id);
    Long authVersion(@Param("id")Long id);
    int globalDepartmentRoles(@Param("id")Long id);
    int departmentReferences(@Param("id")Long id);
    int codeExists(@Param("kind")String kind,@Param("code")String code);
    List<Long> lockRoleCodes(@Param("codes")List<String> codes);
    List<Long> lockAllRoles();
    List<String> roleCodes(@Param("id")Long id);
    List<String> rolePermissions(@Param("id")Long id);
    List<Long> usersForRole(@Param("id")Long id);
    List<UserVersionRow> lockedVersions(@Param("ids")List<Long> ids);
    int incrementVersions(@Param("ids")List<Long> ids);
    int deleteUserRoles(@Param("id")Long id);
    int assignRole(@Param("id")Long id,@Param("code")String code);
    int deleteEditableRoles(@Param("id")Long id);
    int updateStatus(@Param("id")Long id,@Param("enabled")boolean enabled);
    int deleteRolePermissions(@Param("id")Long id);
    int assignPermission(@Param("id")Long id,@Param("code")String code);
    int insertDepartment(@Param("code")String code,@Param("name")String name,@Param("parent")Long parent,@Param("enabled")boolean enabled);
    int updateDepartment(@Param("id")Long id,@Param("code")String code,@Param("name")String name,@Param("parent")Long parent,@Param("enabled")boolean enabled);
    int deleteDepartment(@Param("id")Long id);
    int moveUser(@Param("id")Long id,@Param("department")Long department);
}
