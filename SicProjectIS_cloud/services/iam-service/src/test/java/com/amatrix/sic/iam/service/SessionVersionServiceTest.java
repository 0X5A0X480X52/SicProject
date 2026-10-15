package com.amatrix.sic.iam.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SessionVersionServiceTest {
    StringRedisTemplate redis;
    ValueOperations<String,String> values;
    JdbcTemplate jdbc;
    TransactionTemplate transactions;
    SessionVersionService service;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        redis=mock(StringRedisTemplate.class); values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var source=new DriverManagerDataSource("jdbc:h2:mem:session-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source); jdbc.execute("CREATE TABLE app_user(id BIGINT PRIMARY KEY,auth_version BIGINT)");
        jdbc.update("INSERT INTO app_user VALUES(1,7)");
        var manager=new DataSourceTransactionManager(source);
        transactions=new TransactionTemplate(manager); service=new SessionVersionService(redis,mock(com.amatrix.sic.iam.dao.SessionMapper.class),manager);
    }

    @Test void publicationRunsOnlyAfterDbCommitAndFencesBeforeCommit() {
        when(values.setIfAbsent(eq("sic:jwt:pending:1"),anyString())).thenReturn(true);
        when(redis.execute(eq(SessionVersionService.PUBLISH),eq(List.of("sic:jwt:version:1","sic:jwt:pending:1")),eq("8"),anyString()))
                .thenAnswer(invocation -> {
                    assertThat(jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=1",Long.class)).isEqualTo(8);
                    return 1L;
                });
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=8 WHERE id=1");
            service.publishAfterCommit(1L,8);
            verifyNoInteractions(values);
        });
        verify(values).setIfAbsent(eq("sic:jwt:pending:1"),anyString());
        verify(redis).execute(eq(SessionVersionService.PUBLISH),anyList(),eq("8"),anyString());
    }

    @Test void inabilityToFenceRollsBackTheDatabaseChange() {
        when(values.setIfAbsent(anyString(),anyString())).thenReturn(false);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=8 WHERE id=1");service.publishAfterCommit(1L,8);
        })).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=1",Long.class)).isEqualTo(7);
        verify(redis,never()).execute(eq(SessionVersionService.PUBLISH),anyList(),anyString(),anyString());
    }

    @Test void publicationFailureReportsFailureButKeepsCommittedVersionAndFence() {
        when(values.setIfAbsent(anyString(),anyString())).thenReturn(true);
        when(redis.execute(eq(SessionVersionService.PUBLISH),anyList(),anyString(),anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=8 WHERE id=1");service.publishAfterCommit(1L,8);
        })).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=1",Long.class)).isEqualTo(8);
        verify(redis,never()).delete(anyString());
    }

    @Test void staleLoginAndPendingRevocationCannotIssueAUsableSession() {
        for (Long result : List.of(-1L,-2L)) {
            when(redis.execute(eq(SessionVersionService.LOGIN),anyList(),eq("7"))).thenReturn(result);
            assertThatThrownBy(() -> service.publishLogin(1L,7)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }
}
