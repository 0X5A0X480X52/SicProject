package com.amatrix.sic.integration;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import com.amatrix.sic.iam.service.SessionVersionService;

@Testcontainers(disabledWithoutDocker=true)
class SessionRedisIntegrationTest {
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("sic_sessions").withUsername("sic").withPassword("session-test-password");
    @Container static final GenericContainer<?> redisServer=new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);
    LettuceConnectionFactory factory;
    FaultingRedis redis;
    JdbcTemplate jdbc;
    TransactionTemplate transactions;
    SessionVersionService versions;

    @BeforeEach void setup() throws Exception {
        factory=new LettuceConnectionFactory(redisServer.getHost(),redisServer.getMappedPort(6379));
        factory.afterPropertiesSet(); factory.start();
        redis=new FaultingRedis(factory);
        try(var connection=redis.getConnectionFactory().getConnection()) { connection.serverCommands().flushDb(); }
        var source=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_user(id BIGINT PRIMARY KEY,auth_version BIGINT NOT NULL)");
        jdbc.update("DELETE FROM app_user"); jdbc.update("INSERT INTO app_user VALUES(1,1)");
        var manager=new DataSourceTransactionManager(source);
        transactions=new TransactionTemplate(manager);
        var sessions=new org.mybatis.spring.SqlSessionFactoryBean();
        sessions.setDataSource(source);
        sessions.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/iam/SessionMapper.xml"));
        sessions.afterPropertiesSet();
        var mapper=new org.mybatis.spring.SqlSessionTemplate(sessions.getObject()).getMapper(com.amatrix.sic.iam.dao.SessionMapper.class);
        versions=new SessionVersionService(redis,mapper,manager);
        versions.publishLogin(1L,1);
    }
    @AfterEach void close() { factory.destroy(); }

    @Test void publicationFailureKeepsAccessFencedUntilAuthoritativeDbRecovery() {
        redis.failPublish.set(true);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=2 WHERE id=1");
            versions.publishAfterCommit(1L,2);
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM app_user",Long.class)).isEqualTo(2);
        assertThat(redis.hasKey("sic:jwt:pending:1")).isTrue();
        assertThat(redis.opsForValue().get("sic:jwt:version:1")).isEqualTo("1");
        assertThatThrownBy(() -> versions.publishLogin(1L,1))
                .isInstanceOfSatisfying(ResponseStatusException.class,ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        redis.failPublish.set(false); versions.recover();
        assertThat(redis.hasKey("sic:jwt:pending:1")).isFalse();
        assertThat(redis.opsForValue().get("sic:jwt:version:1")).isEqualTo("2");
        assertThatThrownBy(() -> versions.publishLogin(1L,1))
                .isInstanceOfSatisfying(ResponseStatusException.class,ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }

    @Test void dbRollbackReleasesOnlyItsOwnFenceAndPreservesTheCommittedVersion() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=2 WHERE id=1");
            versions.publishAfterCommit(1L,2);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    assertThat(redis.hasKey("sic:jwt:pending:1")).isTrue();
                    throw new IllegalStateException("Injected database transaction rollback");
                }
            });
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM app_user",Long.class)).isEqualTo(1);
        assertThat(redis.hasKey("sic:jwt:pending:1")).isFalse();
        assertThat(redis.opsForValue().get("sic:jwt:version:1")).isEqualTo("1");
        versions.publishLogin(1L,1);
    }

    @Test void concurrentStaleLoginsCannotLowerThePublishedVersion() throws Exception {
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=2 WHERE id=1"); versions.publishAfterCommit(1L,2);
        });
        try(var workers=Executors.newFixedThreadPool(8)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<80;i++) futures.add(workers.submit(() -> {
                assertThatThrownBy(() -> versions.publishLogin(1L,1))
                    .isInstanceOfSatisfying(ResponseStatusException.class,ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
            }));
            for(var future:futures)future.get(10,TimeUnit.SECONDS);
        }
        assertThat(redis.opsForValue().get("sic:jwt:version:1")).isEqualTo("2");
    }

    @Test void bulkPublicationRejectsOldSessionsAndKeepsForeignFencesOnRollback() {
        for(long id=2;id<=205;id++)jdbc.update("INSERT INTO app_user VALUES(?,1)",id);
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=2");
            for(long id=1;id<=205;id++)versions.publishAfterCommit(id,2);
        });
        for(long id=1;id<=205;id++) {
            assertThat(redis.opsForValue().get("sic:jwt:version:"+id)).isEqualTo("2");
            assertThat(redis.hasKey("sic:jwt:pending:"+id)).isFalse();
        }
        redis.opsForValue().set("sic:jwt:pending:150","foreign-owner");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET auth_version=3");
            for(long id=1;id<=205;id++)versions.publishAfterCommit(id,3);
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE auth_version=2",Integer.class)).isEqualTo(205);
        assertThat(redis.opsForValue().get("sic:jwt:pending:150")).isEqualTo("foreign-owner");
        assertThat(redis.hasKey("sic:jwt:pending:1")).isFalse();
        assertThat(redis.opsForValue().get("sic:jwt:version:1")).isEqualTo("2");
    }

    static class FaultingRedis extends StringRedisTemplate {
        final AtomicBoolean failPublish=new AtomicBoolean();
        FaultingRedis(LettuceConnectionFactory factory) { super(factory); }
        @Override public <T> T execute(RedisScript<T> script,List<String> keys,Object... args) {
            if(failPublish.get() && script.getScriptAsString().contains("redis.call('DEL',KEYS[2])"))
                throw new org.springframework.data.redis.RedisConnectionFailureException("Injected publication outage");
            return super.execute(script,keys,args);
        }
    }
}
