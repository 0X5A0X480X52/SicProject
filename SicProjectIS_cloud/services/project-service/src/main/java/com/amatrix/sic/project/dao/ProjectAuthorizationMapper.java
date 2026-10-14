package com.amatrix.sic.project.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.ProjectRecord;

/**
 * 授权中心 MyBatis 接口：读取与加锁项目、可管理项目、成员与授权查询、负责人更换、部门指派、财务候选人、成员升降级与增删、grant 幂等插入与撤销、专家访问结束、版本与生命周期更新。
 */
@Mapper
public interface ProjectAuthorizationMapper {
    /** 按项目 ID 读取 ACTIVE 项目主数据，供授权管理和范围校验使用。 */
    ProjectRecord project(@Param("id") Long id);
    /** 按项目 ID 锁定并读取 ACTIVE 项目，供成员及授权变更事务使用。 */
    ProjectRecord lockProject(@Param("id") Long id);
    /** 按全局管理开关或部门管理范围查询可管理的 ACTIVE 项目，按项目 ID 倒序返回。 */
    List<ProjectRecord> manageable(@Param("global") boolean global,@Param("department") boolean department,@Param("deptId") Long deptId);
    /** 按项目 ID 列出成员角色、职责及加入时间，供项目成员管理使用。 */
    List<Map<String,Object>> members(@Param("id") Long id);
    /** 按项目和授权角色列出授权状态、有效期和作用范围，按 ID 倒序返回，包含非 ACTIVE 记录。 */
    List<Map<String,Object>> grants(@Param("id") Long id,@Param("role") String role);
    /** 按授权角色统计状态为 ACTIVE 的记录数，供授权分布概览使用。 */
    List<Map<String,Object>> grantCounts();
    int changeLeader(@Param("id") Long id,@Param("userId") Long userId,@Param("name") String name);
    int assignDepartment(@Param("id") Long id,@Param("deptId") Long deptId,@Param("name") String name);
    /** 读取有效期内且匹配项目、模块、轮次、节点的财务办理授权用户，去重返回供后续 IAM 角色校验。 */
    List<Long> financeCandidates(@Param("projectId") Long projectId,@Param("moduleType") String moduleType,
            @Param("roundNo") Integer roundNo,@Param("node") String node);
    int demoteMember(@Param("id") Long id,@Param("userId") Long userId);
    int upsertMember(Map<String,Object> member);
    int deleteMember(@Param("id") Long id,@Param("userId") Long userId);
    /** 统计同项目、用户、角色及相同模块、轮次、节点范围的 ACTIVE 授权，供重复授权检查。 */
    int duplicateGrant(Map<String,Object> grant);
    int insertGrant(Map<String,Object> grant);
    /** 按授权 ID 和项目 ID 读取角色编码，供撤销前校验授权归属与类型。 */
    String grantRole(@Param("id") Long id,@Param("projectId") Long projectId);
    int revokeGrant(@Param("id") Long id);
    int revokeExpert(Map<String,Object> grant);
    int endExpertAccess(@Param("projectId") Long projectId,@Param("moduleType") String moduleType,
            @Param("roundNo") Integer roundNo,@Param("node") String node,@Param("experts") List<Long> experts);
    int touch(@Param("id") Long id);
    /** 按项目 ID 读取版本号，供授权变更事件和版本协调使用。 */
    Long version(@Param("id") Long id);
    int lifecycle(@Param("id") Long id,@Param("stage") String stage);
}
