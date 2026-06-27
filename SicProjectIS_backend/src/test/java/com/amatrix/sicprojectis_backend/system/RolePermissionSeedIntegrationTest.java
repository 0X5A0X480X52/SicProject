package com.amatrix.sicprojectis_backend.system;

import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.dao.RolePermissionDao;
import com.amatrix.sicprojectis_backend.system.entity.Role;
import com.amatrix.sicprojectis_backend.system.entity.RolePermission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RolePermissionSeedIntegrationTest {
    @Autowired
    private RoleDao roleDao;

    @Autowired
    private PermissionDao permissionDao;

    @Autowired
    private RolePermissionDao rolePermissionDao;

    @Test
    void shouldProvideMinimalPhaseTwoAndThreeRolePermissionBaseline() {
        assertThat(roleDao.selectAll()).extracting(Role::getRoleCode)
                .containsExactlyInAnyOrder(
                        "SYSTEM_ADMIN",
                        "PROJECT_LEADER",
                        "SCIENCE_ADMIN",
                        "DEPT_ADMIN",
                        "EXPERT",
                        "FINANCE_ADMIN");

        assertThat(permissionDao.selectAll()).hasSize(23);

        Role systemAdmin = roleDao.selectByCode("SYSTEM_ADMIN");
        Role scienceAdmin = roleDao.selectByCode("SCIENCE_ADMIN");
        Role deptAdmin = roleDao.selectByCode("DEPT_ADMIN");
        Role financeAdmin = roleDao.selectByCode("FINANCE_ADMIN");

        assertThat(permissionDao.selectPermissionCodesByUserId(6L))
                .contains("workflow:definition:publish", "permission:manage", "project:grant:revoke");
        assertThat(permissionDao.selectPermissionCodesByUserId(3L))
                .contains("workflow:definition:view", "workflow:definition:publish", "project:grant:expert")
                .doesNotContain("permission:manage");
        assertThat(permissionDao.selectPermissionCodesByUserId(4L))
                .containsExactlyInAnyOrder("project:view", "project:grant:view", "project:grant:member", "expert:qualification:review:dept", "department:member:manage");
        assertThat(permissionDao.selectPermissionCodesByUserId(5L))
                .containsExactlyInAnyOrder("project:view");

        assertThat(rolePermissionDao.selectByRoleId(systemAdmin.getRoleId())).hasSize(23);
        assertThat(rolePermissionDao.selectByRoleId(scienceAdmin.getRoleId()))
                .extracting(RolePermission::getPermissionId)
                .hasSize(16);
        assertThat(rolePermissionDao.selectByRoleId(deptAdmin.getRoleId()))
                .extracting(RolePermission::getPermissionId)
                .hasSize(5);
        assertThat(rolePermissionDao.selectByRoleId(financeAdmin.getRoleId()))
                .extracting(RolePermission::getPermissionId)
                .hasSize(1);
    }
}
