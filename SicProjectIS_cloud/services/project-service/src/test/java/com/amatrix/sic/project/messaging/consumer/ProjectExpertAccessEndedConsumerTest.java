package com.amatrix.sic.project.messaging.consumer;

import com.amatrix.sic.project.ProjectTestMapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.databind.ObjectMapper;

class ProjectExpertAccessEndedConsumerTest {
    private JdbcTemplate jdbc;
    private ObjectMapper mapper;
    private ProjectExpertAccessEndedConsumer consumer;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:expert-access-ended;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE consumed_event(event_id VARCHAR(36) PRIMARY KEY,consumer_name VARCHAR(100),consumed_at TIMESTAMP)");
        jdbc.execute("""
                CREATE TABLE project(id BIGINT PRIMARY KEY,version BIGINT,updated_at TIMESTAMP,
                  project_code VARCHAR(64),project_name VARCHAR(128),dept_id BIGINT,dept_name_snapshot VARCHAR(128),
                  owner_user_id BIGINT,owner_name_snapshot VARCHAR(128),project_type VARCHAR(64),project_level VARCHAR(64),
                  business_status VARCHAR(64),lifecycle_status VARCHAR(32) DEFAULT 'ACTIVE')
                """);
        jdbc.execute("""
                CREATE TABLE project_access_grant(id BIGINT PRIMARY KEY,project_id BIGINT,grantee_user_id BIGINT,
                  grant_role_code VARCHAR(64),module_type VARCHAR(64),round_no INT,task_node_id VARCHAR(128),
                  status VARCHAR(32),effective_to TIMESTAMP)
                """);
        jdbc.update("INSERT INTO project(id,version,updated_at,owner_user_id) VALUES (1,1,CURRENT_TIMESTAMP,2)");
        jdbc.update("INSERT INTO project_access_grant VALUES (1,1,2,'PROJECT_MODULE_EXPERT_ASSIGNMENT','APPLICATION',1,'ScienceExpertReviewTask','ACTIVE',NULL)");
        jdbc.update("INSERT INTO project_access_grant VALUES (2,1,7,'PROJECT_MODULE_EXPERT_ASSIGNMENT','APPLICATION',1,'ScienceExpertReviewTask','ACTIVE',NULL)");
        jdbc.update("INSERT INTO project_access_grant VALUES (3,1,8,'PROJECT_MODULE_EXPERT_ASSIGNMENT','APPLICATION',1,'OtherExpertReviewTask','ACTIVE',NULL)");
        mapper = new ObjectMapper();
        org.mybatis.spring.SqlSessionFactoryBean factory = new org.mybatis.spring.SqlSessionFactoryBean();
        factory.setDataSource(jdbc.getDataSource());
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/messaging/MessagingMapper.xml"));
        try {
            var sessions = new org.mybatis.spring.SqlSessionTemplate(factory.getObject());
            consumer = new ProjectExpertAccessEndedConsumer(mapper,
                    new ConsumedEventGuard(sessions.getMapper(com.amatrix.sic.messaging.dao.MessagingMapper.class)),
                    ProjectTestMapper.create(jdbc.getDataSource(),com.amatrix.sic.project.dao.ProjectAuthorizationMapper.class,"mapper/project/ProjectAuthorizationMapper.xml"));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    @Test
    void fallsBackToBatchExpertsWhenLegacyNodeReferenceCannotBeResolved() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", 1L);
        payload.put("moduleType", "APPLICATION");
        payload.put("roundNo", 1);
        payload.put("taskNodeId", null);
        payload.put("expertUserIds", List.of(2L, 7L));
        var event = EventEnvelope.create("EXPERT_ACCESS_ENDED", "EXPERT_REVIEW_BATCH", "10",
                null, null, 0L, payload);

        consumer.consume(mapper.writeValueAsString(event));

        assertThat(status(1)).isEqualTo("REVOKED");
        assertThat(status(2)).isEqualTo("REVOKED");
        assertThat(status(3)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM project WHERE id=1", Long.class)).isEqualTo(2L);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM project_access_grant WHERE id=?", String.class, id);
    }
}
