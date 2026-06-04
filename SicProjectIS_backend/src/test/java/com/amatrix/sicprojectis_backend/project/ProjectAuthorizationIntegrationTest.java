package com.amatrix.sicprojectis_backend.project;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectAuthorizationIntegrationTest {
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
    void shouldListAccessibleProjectsByRole() throws Exception {
        String scienceAdminToken = login("carol", "password");
        String deptAdminToken = login("diana", "password");

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].projectId").value(1));
    }

    @Test
    void shouldAllowScienceAdminToManageMembersLeaderAndExperts() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(put("/api/projects/1/members")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 5,
                                  "responsibility": "Data analysis"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[*].user.userId", hasItem(5)));

        mockMvc.perform(put("/api/projects/1/leader")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 5,
                                  "reason": "Leadership transition"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.leaderUserId").value(5))
                .andExpect(jsonPath("$.data.leader.username").value("eve"));

        mockMvc.perform(post("/api/projects/1/expert-grants")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 2,
                                  "moduleType": "APPLICATION",
                                  "roundNo": 1,
                                  "taskNodeId": "expert_review",
                                  "reason": "Assign reviewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expertGrants", hasSize(1)))
                .andExpect(jsonPath("$.data.expertGrants[0].grantee.username").value("bob"))
                .andExpect(jsonPath("$.data.expertGrants[0].grantRoleCode").value("PROJECT_MODULE_EXPERT_ASSIGNMENT"))
                .andExpect(jsonPath("$.data.capabilities.canManageFinance").value(true))
                .andExpect(jsonPath("$.data.capabilities.canManageProxy").value(true));

        String expertToken = login("bob", "password");
        mockMvc.perform(get("/api/projects/1/authorization").header("Authorization", "Bearer " + expertToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.projectId").value(1));

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + login("eve", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].projectId", hasItem(1)));
    }

    @Test
    void shouldAllowScienceAdminToManageFinanceAndProxyGrants() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(post("/api/projects/1/finance-grants")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 5,
                                  "reason": "Finance support"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financeGrants[0].grantee.username").value("eve"));

        mockMvc.perform(post("/api/projects/1/proxy-grants")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 4,
                                  "reason": "Proxy record support"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proxyGrants[0].grantee.username").value("diana"));
    }

    @Test
    void shouldSupportBatchGrantAndBatchRevoke() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(post("/api/projects/1/proxy-grants/batch")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [2, 6],
                                  "reason": "Batch assign proxy operators"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(2))
                .andExpect(jsonPath("$.data.diff.added", hasSize(2)))
                .andExpect(jsonPath("$.data.detail.proxyGrants", hasSize(2)));

        mockMvc.perform(post("/api/projects/1/grants/revoke-batch")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grantIds": [1, 2],
                                  "reason": "Batch revoke reviewers"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(2))
                .andExpect(jsonPath("$.data.diff.removed", hasSize(2)))
                .andExpect(jsonPath("$.data.detail.proxyGrants", hasSize(0)));
    }

    @Test
    void shouldSupportBatchMemberAssignment() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(put("/api/projects/1/members/batch")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [2, 5],
                                  "responsibility": "Batch onboarding"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(2))
                .andExpect(jsonPath("$.data.detail.members[*].user.userId", hasItem(2)))
                .andExpect(jsonPath("$.data.detail.members[*].user.userId", hasItem(5)));
    }

    @Test
    void shouldRestrictExpertAssignmentToScienceOrSystemAdmin() throws Exception {
        String deptAdminToken = login("diana", "password");

        mockMvc.perform(post("/api/projects/1/expert-grants")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 2,
                                  "moduleType": "APPLICATION",
                                  "reason": "Should fail"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/projects/1/finance-grants")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 5,
                                  "reason": "Should fail"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRevokeExpertGrant() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(post("/api/projects/1/expert-grants")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 2,
                                  "moduleType": "APPLICATION",
                                  "roundNo": 1,
                                  "taskNodeId": "expert_review",
                                  "reason": "Assign reviewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expertGrants[0].projectRoleGrantId", not(blankOrNullString())));

        mockMvc.perform(post("/api/projects/1/grants/1/revoke")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Review completed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expertGrants", hasSize(0)));
    }

    @Test
    void shouldRemoveMember() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(put("/api/projects/1/members")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 2,
                                  "responsibility": "External reviewer support"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/1/members/2")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[*].user.userId", not(hasItem(2))));
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
