package com.amatrix.sicprojectis_backend.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BootstrapAdminIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLoginBootstrapAdminWithSystemAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bootstrap_admin",
                                  "password": "Bootstrap123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.user.username").value("bootstrap_admin"))
                .andExpect(jsonPath("$.data.user.roleCodes[*]", hasItem("SYSTEM_ADMIN")))
                .andExpect(jsonPath("$.data.user.permissionCodes[*]", hasItem("project:view")))
                .andExpect(jsonPath("$.data.user.permissionCodes[*]", hasItem("workflow:definition:publish")));
    }
}
