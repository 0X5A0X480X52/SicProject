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

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DepartmentManagementIntegrationTest {
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
    void shouldAllowSystemAdminToManageDepartmentsAndMembers() throws Exception {
        String systemAdminToken = login("frank", "password");

        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deptCode": "MED",
                                  "deptName": "Medical Department",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptId").value(3))
                .andExpect(jsonPath("$.data.deptName").value("Medical Department"));

        mockMvc.perform(put("/api/admin/departments/3")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deptCode": "MED2",
                                  "deptName": "Medical Sciences",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptCode").value("MED2"))
                .andExpect(jsonPath("$.data.deptName").value("Medical Sciences"));

        mockMvc.perform(put("/api/admin/departments/3/members/1")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCodes": ["PROJECT_LEADER", "EXPERT"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptId").value(3))
                .andExpect(jsonPath("$.data.roleCodes[*]", hasItem("PROJECT_LEADER")))
                .andExpect(jsonPath("$.data.roleCodes[*]", not(hasItem("EXPERT"))));

        mockMvc.perform(delete("/api/admin/departments/members/1")
                        .header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptId").doesNotExist())
                .andExpect(jsonPath("$.data.deptName").doesNotExist())
                .andExpect(jsonPath("$.data.roleCodes[*]", hasItem("PROJECT_LEADER")));

        mockMvc.perform(delete("/api/admin/departments/3")
                        .header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].deptCode", not(hasItem("MED2"))));
    }

    @Test
    void shouldDisableDepartmentMemberActionsForDeptAdminWithoutDepartment() throws Exception {
        AppUser diana = appUserDao.selectByUsername("diana");
        diana.setDeptId(null);
        appUserDao.updateById(diana);

        String deptAdminToken = login("diana", "password");

        mockMvc.perform(get("/api/admin/departments/members")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberManagementDisabled").value(true))
                .andExpect(jsonPath("$.data.disabledReason").value("请联系系统管理员分配部门"))
                .andExpect(jsonPath("$.data.members").isArray());

        mockMvc.perform(get("/api/admin/departments/candidates")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .param("keyword", "alice"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("请联系系统管理员分配部门"));

        mockMvc.perform(put("/api/admin/departments/1/members/1")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCodes": ["PROJECT_LEADER"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("请联系系统管理员分配部门"));
    }

    @Test
    void shouldShowNoDepartmentExpertApplicationButOnlySystemAdminCanReviewIt() throws Exception {
        AppUser alice = appUserDao.selectByUsername("alice");
        alice.setDeptId(null);
        appUserDao.updateById(alice);
        AppUser diana = appUserDao.selectByUsername("diana");
        diana.setDeptId(null);
        appUserDao.updateById(diana);

        String aliceToken = login("alice", "password");
        mockMvc.perform(post("/api/expert-qualification/applications")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "specialty": "AI Safety",
                                  "professionalTitle": "Associate Professor",
                                  "applicationReason": "Long-term review experience"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING_DEPT_REVIEW"))
                .andExpect(jsonPath("$.data.applicantDeptId").doesNotExist());

        String deptAdminToken = login("diana", "password");
        mockMvc.perform(get("/api/admin/expert-qualification/applications")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applications[*].applicationId", hasItem(1)));

        mockMvc.perform(post("/api/admin/expert-qualification/applications/1/dept-review")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "opinion": "dept approved"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only review applications from your department"));

        String systemAdminToken = login("frank", "password");
        mockMvc.perform(post("/api/admin/expert-qualification/applications/1/dept-review")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "opinion": "system dept approval"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_SCIENCE_REVIEW"));

        mockMvc.perform(post("/api/admin/expert-qualification/applications/1/science-review")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "opinion": "system science approval"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCodes[*]", hasItem("EXPERT")));
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