package com.amatrix.sicprojectis_backend.workbench;

import com.amatrix.sicprojectis_backend.security.JwtTokenService;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowWorkbenchIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserDao appUserDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private WorkflowNodeDao workflowNodeDao;

    @Autowired
    private TaskInstanceDao taskInstanceDao;

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
    void shouldUseDatabaseRolesForDeptAdminWorkbenchAccessEvenWhenJwtRolesAreStale() throws Exception {
        makeCurrentTaskDeptAdminCandidate();
        String staleDeptAdminToken = jwtTokenService.issueToken(4L, "diana", List.of()).token();

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + staleDeptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].projectId").value(1));

        mockMvc.perform(get("/api/workflow-workbench/items").header("Authorization", "Bearer " + staleDeptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[*].projectId", hasItem(1)))
                .andExpect(jsonPath("$.data[0].candidateRoleCode").value("DEPT_ADMIN"))
                .andExpect(jsonPath("$.data[0].todo").value(true))
                .andExpect(jsonPath("$.data[0].canOperate").value(true));
    }

    private void makeCurrentTaskDeptAdminCandidate() {
        WorkflowNode node = workflowNodeDao.selectById(1L);
        node.setCandidateRoleCode("DEPT_ADMIN");
        workflowNodeDao.updateById(node);

        TaskInstance task = taskInstanceDao.selectById(1L);
        task.setAssigneeUserId(null);
        task.setCandidateRoleCode("DEPT_ADMIN");
        taskInstanceDao.updateById(task);
    }
}
