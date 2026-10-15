package com.amatrix.sic.notification.dao;

import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import com.amatrix.sic.notification.dao.NotificationMapper;

public final class NotificationTestMapper {
    public static NotificationMapper create(DataSource source) {
        try {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/notification/NotificationMapper.xml"));
            return new SqlSessionTemplate(factory.getObject()).getMapper(NotificationMapper.class);
        } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
}
