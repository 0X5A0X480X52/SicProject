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
    /** 按用户名、姓名、部门、状态、角色及候选部门筛选用户，聚合部门和角色编码，按用户 ID 排序。 */
    List<AdminUserRow> users(@Param("filter") Map<String,Object> filter);
    /** 按用户 ID 查询管理端详情，包含部门名称和聚合后的角色编码。 */
    AdminUserRow user(@Param("id")Long id);
    /** 按编码列出全部角色的名称、描述和启用状态，供角色管理使用。 */
    List<Map<String,Object>> roles();
    /** 按编码列出全部权限及分组、描述，供权限配置使用。 */
    List<Map<String,Object>> permissions();
    /** 按角色聚合去重权限编码，生成角色权限矩阵，保留尚未分配权限的角色。 */
    List<Map<String,Object>> permissionMatrix();
    /** 查询部门列表；scope 非空时仅返回该部门，按部门 ID 排序供组织管理使用。 */
    List<Map<String,Object>> departments(@Param("scope")Long scope);
    /** 按部门 ID 读取编码、名称、上级部门和启用状态。 */
    Map<String,Object> department(@Param("id")Long id);
    /** 按角色编码解析角色 ID 并加行锁，供事务内角色分配或修改使用。 */
    Long roleId(@Param("code")String code);
    /** 按部门编码解析部门 ID，用于编码校验和部门定位。 */
    Long departmentId(@Param("code")String code);
    /** 检查指定用户是否存在，存在时返回用户 ID。 */
    Long userId(@Param("id")Long id);
    /** 读取用户所属部门 ID，供部门范围校验使用。 */
    Long userDepartment(@Param("id")Long id);
    /** 读取用户当前授权版本，供身份变更和会话版本协调使用。 */
    Long authVersion(@Param("id")Long id);
    /** 统计用户持有的 SYSTEM_ADMIN 或 SCIENCE_ADMIN 角色数，判断全局部门管理角色。 */
    int globalDepartmentRoles(@Param("id")Long id);
    /** 合计部门下用户数与直接子部门数，供删除部门前检查引用使用。 */
    int departmentReferences(@Param("id")Long id);
    /** 根据 kind 选择角色表或权限表，统计指定编码是否存在。 */
    int codeExists(@Param("kind")String kind,@Param("code")String code);
    /** 按角色编码集合查询角色 ID 并按 ID 顺序加行锁，供批量角色分配使用。 */
    List<Long> lockRoleCodes(@Param("codes")List<String> codes);
    /** 按角色 ID 顺序读取并锁定全部角色行，供管理员引导和角色变更串行处理。 */
    List<Long> lockAllRoles();
    /** 读取用户角色编码并排序，供身份快照和管理详情组装使用。 */
    List<String> roleCodes(@Param("id")Long id);
    /** 读取角色权限编码并排序，供权限配置修改前后比较使用。 */
    List<String> rolePermissions(@Param("id")Long id);
    /** 查询关联角色的用户 ID，供权限变更时定位需更新授权版本的用户。 */
    List<Long> usersForRole(@Param("id")Long id);
    /** 批量读取用户授权版本并按用户 ID 顺序加行锁，供授权版本更新使用。 */
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
