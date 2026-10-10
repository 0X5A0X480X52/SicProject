package com.amatrix.sic.project.service;

import com.amatrix.sic.project.ProjectTestMapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProjectQueryServiceTest {
    private JdbcTemplate seed;
    private CountingDataSource countingDataSource;
    private ProjectQueryService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project-query-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        seed = new JdbcTemplate(dataSource);
        seed.execute("""
                CREATE TABLE project(
                    id BIGINT PRIMARY KEY, project_code VARCHAR(64), project_name VARCHAR(200),
                    dept_id BIGINT, dept_name_snapshot VARCHAR(100), owner_user_id BIGINT,
                    owner_name_snapshot VARCHAR(100), project_type VARCHAR(64), project_level VARCHAR(64),
                    business_status VARCHAR(64), lifecycle_status VARCHAR(32))
                """);
        seed.execute("CREATE TABLE project_member(project_id BIGINT,user_id BIGINT)");
        seed.execute("""
                CREATE TABLE project_access_grant(
                    project_id BIGINT,grantee_user_id BIGINT,grant_role_code VARCHAR(64),status VARCHAR(32),
                    effective_from TIMESTAMP,effective_to TIMESTAMP)
                """);
        loadProjects();
        countingDataSource = new CountingDataSource(dataSource);
        service = new ProjectQueryService(ProjectTestMapper.create(countingDataSource,com.amatrix.sic.project.dao.ProjectQueryMapper.class,"mapper/project/ProjectQueryMapper.xml"));
    }

    @Test
    void filtersMoreThanOneHundredProjectsInOneSqlStatement() {
        List<java.util.Map<String, Object>> projects = service.accessibleProjects(42L, null, List.of());

        assertThat(projects).hasSize(30);
        assertThat(projects).extracting(project -> project.get("projectId"))
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 30)
                        .boxed().sorted(java.util.Comparator.reverseOrder()).toList());
        assertThat(countingDataSource.preparedStatements()).isEqualTo(1);
    }

    @Test
    void appliesDepartmentAndGlobalScopesWithoutPerProjectChecks() {
        assertThat(service.accessibleProjects(42L, 9L, List.of("DEPT_ADMIN"))).hasSize(40);
        assertThat(countingDataSource.preparedStatements()).isEqualTo(1);

        countingDataSource.reset();
        assertThat(service.accessibleProjects(999L, null, List.of("SCIENCE_ADMIN"))).hasSize(150);
        assertThat(countingDataSource.preparedStatements()).isEqualTo(1);
    }

    private void loadProjects() {
        for (long id = 1; id <= 151; id++) {
            long owner = id <= 10 ? 42L : 1000L + id;
            long department = id >= 41 && id <= 50 ? 9L : 1L;
            String lifecycle = id == 151 ? "PROVISIONING" : "ACTIVE";
            seed.update("INSERT INTO project VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, "P-" + id,
                    "Project " + id, department, "Department " + department, owner, "Owner " + owner,
                    "RESEARCH", "SCHOOL", "APPLICATION", lifecycle);
            if (id >= 11 && id <= 20) seed.update("INSERT INTO project_member VALUES (?,42)", id);
            if (id >= 21 && id <= 30) {
                seed.update("INSERT INTO project_access_grant VALUES (?,42,'PROJECT_PROXY_RECORDER_ASSIGNMENT','ACTIVE',NULL,?)", id,
                        Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
            }
            if (id >= 31 && id <= 40) {
                seed.update("INSERT INTO project_access_grant VALUES (?,42,'PROJECT_PROXY_RECORDER_ASSIGNMENT','ACTIVE',NULL,?)", id,
                        Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));
            }
        }
    }

    private static final class CountingDataSource extends DelegatingDataSource {
        private final AtomicInteger preparedStatements = new AtomicInteger();

        CountingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return proxy(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return proxy(super.getConnection(username, password));
        }

        int preparedStatements() {
            return preparedStatements.get();
        }

        void reset() {
            preparedStatements.set(0);
        }

        private Connection proxy(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().startsWith("prepareStatement")) preparedStatements.incrementAndGet();
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    });
        }
    }
}
