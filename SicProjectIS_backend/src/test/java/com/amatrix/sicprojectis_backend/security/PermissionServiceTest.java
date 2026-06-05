package com.amatrix.sicprojectis_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PermissionServiceTest {
    @Autowired
    private PermissionService permissionService;

    @Test
    void shouldCheckRoleAndPermission() {
        assertThat(permissionService.hasRole(1L, "PROJECT_LEADER")).isTrue();
        assertThat(permissionService.hasRole(1L, "EXPERT")).isFalse();
        assertThat(permissionService.hasRole(3L, "SCIENCE_ADMIN")).isTrue();
        assertThat(permissionService.hasPermission(1L, "project:view")).isTrue();
        assertThat(permissionService.hasPermission(2L, "project:view")).isFalse();
        assertThat(permissionService.hasPermission(3L, "project:grant:expert")).isTrue();
        assertThat(permissionService.hasPermission(3L, "workflow:definition:publish")).isTrue();
    }

    @Test
    void shouldCheckProjectAccess() {
        assertThat(permissionService.canAccessProject(1L, 1L)).isTrue();
        assertThat(permissionService.canAccessProject(2L, 1L)).isFalse();
        assertThat(permissionService.canAccessProject(3L, 1L)).isTrue();
        assertThat(permissionService.canAccessProject(4L, 1L)).isTrue();
        assertThat(permissionService.canAccessProject(4L, 2L)).isFalse();
        assertThat(permissionService.canAccessProject(1L, 999L)).isFalse();
    }

    @Test
    void shouldCheckCurrentModuleNodeOperation() {
        assertThat(permissionService.canOperateModuleNode(1L, 1L)).isTrue();
        assertThat(permissionService.canOperateModuleNode(2L, 1L)).isFalse();
        assertThat(permissionService.canOperateModuleNode(1L, 999L)).isFalse();
    }
}
