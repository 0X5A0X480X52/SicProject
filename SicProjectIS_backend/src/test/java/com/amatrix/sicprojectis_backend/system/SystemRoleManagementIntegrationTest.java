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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SystemRoleManagementIntegrationTest {
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
    void shouldAllowSystemAdminToViewAndUpdateRoles() throws Exception {
        String systemAdminToken = login("frank", "password");

        mockMvc.perform(get("/api/admin/users/roles").header("Authorization", "Bearer " + systemAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasSize(6)))
                .andExpect(jsonPath("$.data.users[*].username", hasItem("bootstrap_admin")));

        mockMvc.perform(put("/api/admin/users/5/roles")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCodes": ["EXPERT", "PROJECT_LEADER"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roleCodes[*]", hasItem("EXPERT")))
                .andExpect(jsonPath("$.data.user.roleCodes[*]", hasItem("PROJECT_LEADER")))
                .andExpect(jsonPath("$.data.diff.added[*]", hasItem("EXPERT")))
                .andExpect(jsonPath("$.data.query.users[?(@.userId==5)].roleCodes[*]", hasItem("PROJECT_LEADER")));

        String eveToken = login("eve", "password");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + eveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCodes[*]", hasItem("EXPERT")))
                .andExpect(jsonPath("$.data.roleCodes[*]", hasItem("PROJECT_LEADER")));
    }

    @Test
    void shouldAllowSystemAdminToQueryAndDisableUser() throws Exception {
        String systemAdminToken = login("frank", "password");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .param("roleCode", "FINANCE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users", hasSize(1)))
                .andExpect(jsonPath("$.data.users[0].username").value("eve"));

        mockMvc.perform(patch("/api/admin/users/5/status")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void shouldRejectNonSystemAdminRoleManagement() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(get("/api/admin/users/roles").header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectSelfRemovalOfSystemAdminRole() throws Exception {
        String systemAdminToken = login("frank", "password");

        mockMvc.perform(put("/api/admin/users/6/roles")
                        .header("Authorization", "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCodes": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot remove your own SYSTEM_ADMIN role"));
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
