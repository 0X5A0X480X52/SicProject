package com.amatrix.sicprojectis_backend.dao;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@org.mybatis.spring.boot.test.autoconfigure.MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DaoSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void allDaoBeansShouldLoadAndExecuteSelectAll() throws Exception {
        Map<String, Object> daoBeans = applicationContext.getBeansWithAnnotation(org.apache.ibatis.annotations.Mapper.class);

        assertThat(daoBeans).hasSize(33);

        for (Object daoBean : daoBeans.values()) {
            Method selectAll = AopUtils.getTargetClass(daoBean).getMethod("selectAll");
            Object result = selectAll.invoke(daoBean);
            assertThat(result).isInstanceOf(List.class);
        }
    }
}
