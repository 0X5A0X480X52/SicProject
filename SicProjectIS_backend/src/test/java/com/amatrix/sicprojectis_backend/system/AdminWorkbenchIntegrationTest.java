package com.amatrix.sicprojectis_backend.system;

import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminWorkbenchIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserDao appUserDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void encodePasswords() {
        for (AppUser user : appUserDao.selectAll()) {
            user.setPasswordHash(passwordEncoder.encode(
                    "bootstrap_admin".equals(user.getUsername()) ? "Bootstrap123456" : "password"));
            user.setEnabled(true);
            appUserDao.updateById(user);
        }
    }

    @Test
    void shouldAllowSystemAdminToManageRolePermissionMatrixAndViewAudit() throws Exception {
        String systemAdminToken = login("frank", "password");

        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPermissions").value(19));

        mockMvc.perform(get("/api/admin/roles/permissions").header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasSize(19)))
                .andExpect(jsonPath("$.data.matrix[?(@.roleCode=='SYSTEM_ADMIN')].permissionCodes[*]", hasItem("user:manage")));

        mockMvc.perform(put("/api/admin/roles/EXPERT/permissions")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes": ["project:view", "audit:read"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diff.added[*]", hasItem("audit:read")));

        String expertToken = login("bob", "password");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expertToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionCodes[*]", hasItem("audit:read")));

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logs[*].scopeType", hasItem("ROLE_PERMISSION")));
    }

    @Test
    void shouldRestrictAuditAndRolePermissionMatrixForScienceAdmin() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(get("/api/admin/roles/permissions").header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logs").exists());
    }

    @Test
    void shouldExposeProjectAuthorizationIndexForDeptAdmin() throws Exception {
        String deptAdminToken = login("diana", "password");

        mockMvc.perform(get("/api/admin/projects/authorizations").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects", hasSize(1)))
                .andExpect(jsonPath("$.data.projects[0].projectId").value(1));
    }

    private String login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
