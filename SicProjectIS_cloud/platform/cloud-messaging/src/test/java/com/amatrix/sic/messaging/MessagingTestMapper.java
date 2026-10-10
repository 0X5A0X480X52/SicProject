package com.amatrix.sic.messaging;

import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import com.amatrix.sic.messaging.dao.MessagingMapper;

final class MessagingTestMapper {
    static MessagingMapper create(DataSource source) {
        try {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/messaging/MessagingMapper.xml"));
            return new SqlSessionTemplate(factory.getObject()).getMapper(MessagingMapper.class);
        } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
}
