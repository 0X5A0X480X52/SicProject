package com.amatrix.sic.iam.dao;

import com.amatrix.sic.iam.dto.IamDtos;

import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.amatrix.sic.contracts.UserSummary;
import com.amatrix.sic.iam.dto.IamDtos.UserAccount;
import com.amatrix.sic.iam.dao.IdentityMapper;

/**
 * 用户仓储：{@code @Repository} 包装 IdentityMapper；findByUsername/findById 在只读事务内查询用户、部门与角色权限，summaries 批量取用户摘要与角色；insertUser 无事务注解，assignRole 在读写事务内先锁角色与用户再写映射。
 */
@Repository
public class IamRepository {
    private final IdentityMapper mapper;
    public IamRepository(IdentityMapper mapper) { this.mapper=mapper; }

    @Transactional(readOnly=true)
    public UserAccount findByUsername(String username) {
        Long id=mapper.idByUsername(username);
        return id==null?null:findById(id);
    }

    /** 批量角色查询与不含凭据的用户投影分别读取，避免逐用户查询及密码字段外泄。 */
    @Transactional(readOnly=true)
    public List<UserSummary> summaries(List<Long> userIds) {
        if(userIds==null || userIds.isEmpty())return List.of();
        List<Long> ids=userIds.stream().distinct().toList();
        if(ids.size()>100 || ids.stream().anyMatch(id -> id==null || id<=0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide at most 100 positive user IDs");
        Map<Long,List<String>> roles=new LinkedHashMap<>();
        mapper.rolesForUsers(ids).forEach(role -> roles.computeIfAbsent(role.userId(),ignored -> new ArrayList<>()).add(role.code()));
        return mapper.summaries(ids).stream().map(user -> new UserSummary(user.userId(),user.username(),user.realName(),
                user.deptId(),user.deptName(),List.copyOf(roles.getOrDefault(user.userId(),List.of())),user.enabled(),user.authVersion())).toList();
    }

    @Transactional(readOnly=true)
    public UserAccount findById(Long userId) {
        var user=mapper.identity(userId);
        return user==null?null:new UserAccount(user.userId(),user.username(),user.passwordHash(),user.realName(),user.deptId(),
                user.deptName(),user.phone(),user.email(),user.enabled(),user.authVersion(),roleCodes(userId),permissionCodes(userId));
    }

    public long insertUser(String username,String passwordHash,String realName,Long deptId,String phone,String email) {
        Map<String,Object> user=new LinkedHashMap<>();
        user.put("username",username);user.put("passwordHash",passwordHash);user.put("realName",realName);
        user.put("deptId",deptId);user.put("phone",phone);user.put("email",email);
        mapper.insertUser(user);
        return ((Number)Objects.requireNonNull(user.get("id"))).longValue();
    }

    @Transactional
    public void assignRole(Long userId,String roleCode) {
        mapper.lockRole(roleCode);
        mapper.lockUser(userId);
        mapper.assignRole(userId,roleCode);
    }
    public List<String> roleCodes(Long userId) { return mapper.roles(userId); }
    public List<String> permissionCodes(Long userId) { return mapper.permissions(userId); }
}
