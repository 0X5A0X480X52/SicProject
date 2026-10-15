package com.amatrix.sic.material;
import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;

public final class MaterialTestMapper {
    public static <T> T create(DataSource source,Class<T> type,String resource) {
        try {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource(resource));
            var configuration=new org.apache.ibatis.session.Configuration();configuration.setCallSettersOnNulls(true);
            factory.setConfiguration(configuration);factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject()).getMapper(type);
        } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
}
