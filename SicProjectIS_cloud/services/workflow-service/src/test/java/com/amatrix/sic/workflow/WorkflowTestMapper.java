package com.amatrix.sic.workflow;

import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;

public final class WorkflowTestMapper {
    private WorkflowTestMapper() {}
    public static <T> T create(DataSource source, Class<T> type, String... resources) {
        try {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(java.util.Arrays.stream(resources).map(ClassPathResource::new)
                    .toArray(ClassPathResource[]::new));
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setCallSettersOnNulls(true);
            factory.setConfiguration(configuration);
            factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject()).getMapper(type);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
