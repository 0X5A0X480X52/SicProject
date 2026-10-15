package com.amatrix.sic.project.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProjectAccessPolicyTest {
    private JdbcTemplate jdbc;
    private ProjectAccessPolicy policy;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project-policy;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE project(id BIGINT PRIMARY KEY,version BIGINT,owner_user_id BIGINT,"
                + "owner_name_snapshot VARCHAR(100),dept_id BIGINT,lifecycle_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE project_member(project_id BIGINT,user_id BIGINT)");
        jdbc.execute("CREATE TABLE project_access_grant(project_id BIGINT,grantee_user_id BIGINT,"
                + "grant_role_code VARCHAR(64),module_type VARCHAR(64),round_no INT,task_node_id VARCHAR(64),"
                + "status VARCHAR(32),effective_from TIMESTAMP,effective_to TIMESTAMP)");
        jdbc.update("INSERT INTO project VALUES (1,7,10,'Owner',20,'ACTIVE'),(2,2,10,'Owner',20,'FAILED')");
        jdbc.update("INSERT INTO project_member VALUES (1,30)");
        jdbc.update("INSERT INTO project_access_grant(project_id,grantee_user_id,grant_role_code,status) VALUES "
                + "(1,40,'PROJECT_MODULE_EXPERT_ASSIGNMENT','ACTIVE'),"
                + "(1,50,'PROJECT_FINANCE_HANDLER_ASSIGNMENT','ACTIVE'),"
                + "(1,60,'PROJECT_PROXY_RECORDER_ASSIGNMENT','ACTIVE')");
        var factory=new org.mybatis.spring.SqlSessionFactoryBean();factory.setDataSource(dataSource);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/project/ProjectScopeMapper.xml"));
        factory.afterPropertiesSet();
        policy=new ProjectAccessPolicy(new org.mybatis.spring.SqlSessionTemplate(factory.getObject())
                .getMapper(com.amatrix.sic.project.dao.ProjectScopeMapper.class));
    }

    @Test
    void enforcesGlobalDepartmentOwnerMemberAndGrantMatrix() {
        assertAllowed(70L, null, List.of("SYSTEM_ADMIN"), "READ");
        assertAllowed(71L, null, List.of("SCIENCE_ADMIN"), "START_CONTRACT");
        assertAllowed(10L, null, List.of("PROJECT_LEADER"), "WRITE_WORKFLOW_DATA");
        assertAllowed(21L, 20L, List.of("DEPT_ADMIN"), "MATERIAL_WRITE");
        assertAllowed(30L, null, List.of(), "MATERIAL_DELETE");
        assertAllowed(40L, null, List.of("EXPERT"), "READ");
        assertDenied(40L, null, List.of("EXPERT"), "WRITE_WORKFLOW_DATA", "PROJECT_OPERATION_DENIED");
        assertAllowed(50L, null, List.of("FINANCE_ADMIN"), "MATERIAL_WRITE");
        assertAllowed(60L, null, List.of(), "WRITE_WORKFLOW_DATA");
        assertDenied(10L, null, List.of("PROJECT_LEADER"), "START_CONTRACT",
                "WORKFLOW_START_REQUIRES_SCIENCE_ADMIN");
    }

    @Test
    void rejectsInactiveMissingAndInvalidSubjects() {
        assertThat(policy.decide(10L, null, List.of("SYSTEM_ADMIN"), 2L, "READ").reasonCode())
                .isEqualTo("PROJECT_NOT_ACTIVE");
        assertThat(policy.decide(10L, null, List.of(), 99L, "READ").allowed()).isFalse();
        assertThat(policy.decide(null, null, List.of(), 1L, "READ").reasonCode())
                .isEqualTo("INVALID_ACTOR_OR_PROJECT");
    }

    private void assertAllowed(Long userId, Long deptId, List<String> roles, String operation) {
        var decision = policy.decide(userId, deptId, roles, 1L, operation);
        assertThat(decision.allowed()).as(operation + " for user " + userId).isTrue();
        assertThat(decision.projectVersion()).isEqualTo(7);
    }

    private void assertDenied(Long userId, Long deptId, List<String> roles, String operation, String reason) {
        var decision = policy.decide(userId, deptId, roles, 1L, operation);
        assertThat(decision.allowed()).as(operation + " for user " + userId).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(reason);
    }
}
