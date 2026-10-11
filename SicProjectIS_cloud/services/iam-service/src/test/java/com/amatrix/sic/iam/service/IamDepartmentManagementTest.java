package com.amatrix.sic.iam.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.amatrix.sic.iam.dao.AdminMapper;
import com.amatrix.sic.messaging.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class IamDepartmentManagementTest {
    private AdminMapper mapper;
    private IamAdminService service;

    @BeforeEach void setup() {
        mapper=mock(AdminMapper.class);
        service=new IamAdminService(mapper,mock(SessionVersionService.class),mock(OutboxService.class));
        when(mapper.userId(anyLong())).thenAnswer(i -> i.getArgument(0));
        when(mapper.globalDepartmentRoles(1L)).thenReturn(1);
        when(mapper.users(anyMap())).thenReturn(List.of());
        when(mapper.departments(any())).thenReturn(List.of());
        when(mapper.user(anyLong())).thenAnswer(i -> new com.amatrix.sic.iam.entity.AdminUserRow(
                i.getArgument(0),"leader","Leader",null,null,null,null,true,"PROJECT_LEADER,EXPERT"));
    }

    @Test void globalInitialViewWorksWithZeroDepartments() {
        var response=service.departmentMembers(1L,null);
        assertThat(response.get("canManageDepartments")).isEqualTo(true);
        assertThat(response.get("memberManagementDisabled")).isEqualTo(false);
        verify(mapper).departments(null);
        verify(mapper,never()).department(any());
    }

    @Test void unassignedDepartmentAdminGetsDisabledViewWithoutGlobalData() {
        when(mapper.userDepartment(2L)).thenReturn(null);
        var response=service.departmentMembers(2L,null);
        assertThat(response.get("memberManagementDisabled")).isEqualTo(true);
        assertThat(response.get("canManageDepartments")).isEqualTo(false);
        verify(mapper,never()).users(anyMap());
        verify(mapper,never()).departments(any());
    }

    @Test void departmentAdminCannotQueryAnotherDepartment() {
        when(mapper.userDepartment(2L)).thenReturn(3L);
        assertThatThrownBy(() -> service.departmentMembers(2L,4L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(mapper,never()).users(anyMap());
    }

    @Test void blankDepartmentCodeIsGeneratedAndBlankEditPreservesIt() {
        when(mapper.departmentId(anyString())).thenReturn(3L);
        when(mapper.department(3L)).thenReturn(Map.of("deptCode","EXISTING"));
        service.createDepartment(1L,Map.of("deptCode","", "deptName","测试部门"));
        verify(mapper).insertDepartment(matches("DEPT_[0-9a-f]{32}"),eq("测试部门"),isNull(),eq(true));
        service.updateDepartment(1L,3L,Map.of("deptCode","", "deptName","新名称"));
        verify(mapper).updateDepartment(3L,"EXISTING","新名称",null,true);
    }

    @Test void assigningOnlyDepartmentDoesNotModifyRoles() {
        when(mapper.department(3L)).thenReturn(Map.of("deptCode","TEST"));
        service.upsertMember(1L,3L,7L,null);
        verify(mapper).moveUser(7L,3L);
        verify(mapper,never()).deleteEditableRoles(anyLong());
        verify(mapper,never()).assignRole(anyLong(),anyString());
    }
}
