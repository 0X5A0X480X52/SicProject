package com.amatrix.sic.iam.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.iam.dao.AdminMapper;
import com.amatrix.sic.iam.entity.AdminUserRow;

@Service
public class IamAdminService {
    private static final List<String> EDITABLE_DEPARTMENT_ROLES=List.of("PROJECT_LEADER","EXPERT","FINANCE_ADMIN");
    private final AdminMapper mapper;
    private final SessionVersionService versions;
    private final OutboxService outbox;
    public IamAdminService(AdminMapper mapper,SessionVersionService versions,OutboxService outbox) {
        this.mapper=mapper;this.versions=versions;this.outbox=outbox;
    }

    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public Map<String,Object> queryUsers(String username,String realName,Long deptId,Boolean enabled,String roleCode) {
        var filter=linked("username",like(username),"realName",like(realName),"deptId",deptId,"enabled",enabled,
                "roleCode",hasText(roleCode)?roleCode.trim():null);
        return linked("roles",roles(),"users",mapper.users(filter).stream().map(row -> userMap(row,false)).toList());
    }
    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public Map<String,Object> userDetail(Long userId) {
        var user=mapper.user(userId);
        if(user==null)throw status(HttpStatus.NOT_FOUND,"User not found");
        return userMap(user,true);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public Map<String,Object> updateRoles(Long operatorId,Long userId,List<String> requested) {
        requireUser(userId);
        mapper.lockAllRoles();
        List<String> normalized=distinct(requested);
        validateCodes("app_role",normalized,"Unknown role code");
        mapper.lockedVersions(List.of(userId));
        List<String> before=mapper.roleCodes(userId);
        if(Objects.equals(operatorId,userId) && before.contains("SYSTEM_ADMIN") && !normalized.contains("SYSTEM_ADMIN"))
            throw status(HttpStatus.BAD_REQUEST,"You cannot remove your own SYSTEM_ADMIN role");
        mapper.deleteUserRoles(userId);
        normalized.forEach(code -> mapper.assignRole(userId,code));
        invalidateUsers(List.of(userId));
        emit("IAM_USER_ROLES_CHANGED",userId,operatorId,linked("userId",userId,"before",before,"after",normalized));
        return linked("user",userDetail(userId),"diff",diff(before,normalized),"query",queryUsers(null,null,null,null,null));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public Map<String,Object> updateStatus(Long operatorId,Long userId,boolean enabled) {
        requireUser(userId);
        if(Objects.equals(operatorId,userId) && !enabled)throw status(HttpStatus.BAD_REQUEST,"You cannot disable your own account");
        mapper.updateStatus(userId,enabled);cacheVersion(userId);
        emit("IAM_USER_STATUS_CHANGED",userId,operatorId,linked("userId",userId,"enabled",enabled));
        return userDetail(userId);
    }
    @PreAuthorize("hasAuthority('PERM_PERMISSION_MANAGE')")
    public List<Map<String,Object>> permissions() { return mapper.permissions(); }
    @PreAuthorize("hasAuthority('PERM_PERMISSION_MANAGE')")
    public Map<String,Object> permissionMatrix() {
        var matrix=mapper.permissionMatrix();
        matrix.forEach(row -> row.put("permissionCodes",split((String)row.get("permissionCodes"))));
        return linked("roles",roles(),"permissions",permissions(),"matrix",matrix);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_PERMISSION_MANAGE')")
    public Map<String,Object> updateRolePermissions(Long operatorId,String roleCode,List<String> requested) {
        Long roleId=mapper.roleId(roleCode);
        if(roleId==null)throw status(HttpStatus.NOT_FOUND,"Role not found");
        List<String> normalized=distinct(requested);
        validateCodes("app_permission",normalized,"Unknown permission code");
        List<String> before=mapper.rolePermissions(roleId);
        mapper.deleteRolePermissions(roleId);
        normalized.forEach(code -> mapper.assignPermission(roleId,code));
        List<Long> affected=mapper.usersForRole(roleId);
        invalidateUsers(affected);
        emit("IAM_ROLE_PERMISSIONS_CHANGED",roleId,operatorId,linked("roleCode",roleCode,"before",before,"after",normalized,"affectedUsers",affected));
        return linked("roleCode",roleCode,"diff",diff(before,normalized),"matrix",permissionMatrix());
    }
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public List<Map<String,Object>> departments(Long operatorId) { return mapper.departments(departmentScope(operatorId)); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String,Object> createDepartment(Long operatorId,Map<String,Object> request) {
        requireGlobalDepartmentManager(operatorId);
        String code=required(request.get("deptCode"),"Department code is required");
        String name=required(request.get("deptName"),"Department name is required");
        mapper.insertDepartment(code,name,asLong(request.get("parentDeptId")),!Boolean.FALSE.equals(request.get("enabled")));
        Long id=mapper.departmentId(code);
        emit("IAM_DEPARTMENT_CHANGED",id,operatorId,linked("departmentId",id,"operation","CREATE"));
        return department(id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String,Object> updateDepartment(Long operatorId,Long deptId,Map<String,Object> request) {
        requireGlobalDepartmentManager(operatorId);requireDepartment(deptId);
        String code=required(request.get("deptCode"),"Department code is required");
        String name=required(request.get("deptName"),"Department name is required");
        Long parent=asLong(request.get("parentDeptId"));
        if(Objects.equals(parent,deptId))throw status(HttpStatus.BAD_REQUEST,"Department cannot be its own parent");
        mapper.updateDepartment(deptId,code,name,parent,!Boolean.FALSE.equals(request.get("enabled")));
        emit("IAM_DEPARTMENT_CHANGED",deptId,operatorId,linked("departmentId",deptId,"operation","UPDATE"));
        return department(deptId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public void deleteDepartment(Long operatorId,Long deptId) {
        requireGlobalDepartmentManager(operatorId);requireDepartment(deptId);
        if(mapper.departmentReferences(deptId)>0)throw status(HttpStatus.CONFLICT,"Department is still referenced");
        mapper.deleteDepartment(deptId);
        emit("IAM_DEPARTMENT_CHANGED",deptId,operatorId,linked("departmentId",deptId,"operation","DELETE"));
    }
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String,Object> departmentMembers(Long operatorId,Long deptId) {
        Long scope=departmentScope(operatorId),selected=scope==null?deptId:scope;
        if(selected==null)throw status(HttpStatus.BAD_REQUEST,"Department is required");
        requireDepartment(selected);
        return linked("departments",departments(operatorId),"assignableRoleCodes",EDITABLE_DEPARTMENT_ROLES,
                "members",usersForDepartment(selected,null),"canManageDepartments",scope==null,"memberManagementDisabled",false,"disabledReason",null);
    }
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String,Object> candidates(Long operatorId,String keyword) {
        Long scope=departmentScope(operatorId);
        return linked("users",mapper.users(linked("candidateDept",scope,"keyword",like(keyword))).stream().map(this::memberMap).toList());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String,Object> upsertMember(Long operatorId,Long deptId,Long userId,List<String> roles) {
        requireManagedDepartment(operatorId,deptId);requireDepartment(deptId);requireUser(userId);
        Long currentDept=mapper.userDepartment(userId);
        if(!isGlobalDepartmentManager(operatorId) && currentDept!=null && !Objects.equals(currentDept,deptId))
            throw status(HttpStatus.FORBIDDEN,"Department administrators cannot move users from another department");
        replaceEditableRoles(userId,roles);mapper.moveUser(userId,deptId);cacheVersion(userId);
        emit("IAM_USER_CHANGED",userId,operatorId,linked("userId",userId,"deptId",deptId));
        return member(userId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String,Object> updateMemberRoles(Long operatorId,Long userId,List<String> roles) {
        requireUser(userId);requireManagedDepartment(operatorId,mapper.userDepartment(userId));
        replaceEditableRoles(userId,roles);invalidateUsers(List.of(userId));
        emit("IAM_USER_ROLES_CHANGED",userId,operatorId,linked("userId",userId,"roles",roles));
        return member(userId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String,Object> removeMember(Long operatorId,Long userId) {
        requireUser(userId);requireManagedDepartment(operatorId,mapper.userDepartment(userId));
        mapper.moveUser(userId,null);cacheVersion(userId);
        emit("IAM_USER_CHANGED",userId,operatorId,linked("userId",userId,"deptId",null));
        return member(userId);
    }
    private List<Map<String,Object>> usersForDepartment(Long deptId,String keyword) {
        return mapper.users(linked("deptId",deptId,"keyword",like(keyword))).stream().map(this::memberMap).toList();
    }
    private Map<String,Object> member(Long userId) {
        var user=mapper.user(userId);
        if(user==null)throw status(HttpStatus.NOT_FOUND,"User not found");
        return memberMap(user);
    }
    private void replaceEditableRoles(Long userId,List<String> requested) {
        mapper.lockAllRoles();
        List<String> normalized=distinct(requested);
        if(!EDITABLE_DEPARTMENT_ROLES.containsAll(normalized))throw status(HttpStatus.BAD_REQUEST,"Role is not assignable by department management");
        validateCodes("app_role",normalized,"Unknown role code");mapper.lockedVersions(List.of(userId));
        mapper.deleteEditableRoles(userId);normalized.forEach(code -> mapper.assignRole(userId,code));
    }
    private List<Map<String,Object>> roles() { return mapper.roles(); }
    private Map<String,Object> department(Long id) {
        var row=mapper.department(id);
        if(row==null)throw status(HttpStatus.NOT_FOUND,"Department not found");
        return row;
    }
    private Long departmentScope(Long operatorId) {
        requireUser(operatorId);
        if(isGlobalDepartmentManager(operatorId))return null;
        Long dept=mapper.userDepartment(operatorId);
        if(dept==null)throw status(HttpStatus.FORBIDDEN,"Department administrator has no department scope");
        return dept;
    }
    private boolean isGlobalDepartmentManager(Long id) { return mapper.globalDepartmentRoles(id)>0; }
    private void requireGlobalDepartmentManager(Long id) {
        if(!isGlobalDepartmentManager(id))throw status(HttpStatus.FORBIDDEN,"Only global administrators can change department structure");
    }
    private void requireManagedDepartment(Long operatorId,Long deptId) {
        if(isGlobalDepartmentManager(operatorId))return;
        if(deptId==null || !Objects.equals(departmentScope(operatorId),deptId))
            throw status(HttpStatus.FORBIDDEN,"Department administrators can only manage their own department");
    }
    private Map<String,Object> userMap(AdminUserRow user,boolean detail) {
        var row=linked("userId",user.userId(),"username",user.username(),"realName",user.realName(),"deptId",user.deptId(),
                "deptName",user.deptName(),"enabled",user.enabled(),"roleCodes",split(user.roleCodes()),"canEditRoles",true,"canToggleStatus",true);
        if(detail) { row.put("phone",user.phone());row.put("email",user.email()); }
        return row;
    }
    private Map<String,Object> memberMap(AdminUserRow user) {
        return linked("userId",user.userId(),"username",user.username(),"realName",user.realName(),"deptId",user.deptId(),"deptName",user.deptName(),
                "phone",user.phone(),"email",user.email(),"enabled",user.enabled(),"roleCodes",split(user.roleCodes()),"editableRoleCodes",EDITABLE_DEPARTMENT_ROLES);
    }
    private void invalidateUsers(Collection<Long> values) {
        List<Long> ids=values.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if(ids.isEmpty())return;
        var before=mapper.lockedVersions(ids);mapper.incrementVersions(ids);
        before.forEach(user -> versions.publishAfterCommit(user.userId(),user.version()+1));
    }
    private void cacheVersion(Long id) {
        Long version=mapper.authVersion(id);
        if(version!=null)versions.publishAfterCommit(id,version);
    }
    private void validateCodes(String kind,List<String> codes,String message) {
        for(String code:codes)if(mapper.codeExists(kind,code)==0)throw status(HttpStatus.BAD_REQUEST,message+": "+code);
        if(kind.equals("app_role") && !codes.isEmpty())mapper.lockRoleCodes(codes);
    }
    private void requireUser(Long id) { if(mapper.userId(id)==null)throw status(HttpStatus.NOT_FOUND,"User not found"); }
    private void requireDepartment(Long id) { if(mapper.department(id)==null)throw status(HttpStatus.NOT_FOUND,"Department not found"); }
    private void emit(String type,Object id,Long actor,Map<String,Object> payload) {
        outbox.enqueue("iam.changed",EventEnvelope.create(type,"IAM",String.valueOf(id),null,null,actor,payload));
    }
    private static Map<String,Object> diff(List<String> before,List<String> after) {
        return linked("added",after.stream().filter(value -> !before.contains(value)).toList(),"removed",before.stream().filter(value -> !after.contains(value)).toList());
    }
    private static List<String> distinct(List<String> values) {
        return values==null?List.of():values.stream().filter(IamAdminService::hasText).map(String::trim).distinct().sorted().toList();
    }
    private static List<String> split(String value) { return hasText(value)?Arrays.asList(value.split(",")):List.of(); }
    private static String like(String value) { return hasText(value)?"%"+value.trim()+"%":null; }
    private static boolean hasText(String value) { return value!=null && !value.isBlank(); }
    private static String required(Object value,String message) {
        String text=value==null?null:value.toString().trim();
        if(!hasText(text))throw status(HttpStatus.BAD_REQUEST,message);
        return text;
    }
    private static Long asLong(Object value) { return value==null?null:value instanceof Number n?n.longValue():Long.valueOf(value.toString()); }
    private static ResponseStatusException status(HttpStatus code,String message) { return new ResponseStatusException(code,message); }
    private static Map<String,Object> linked(Object... values) {
        var map=new LinkedHashMap<String,Object>();
        for(int i=0;i<values.length;i+=2)map.put((String)values[i],values[i+1]);
        return map;
    }
}
