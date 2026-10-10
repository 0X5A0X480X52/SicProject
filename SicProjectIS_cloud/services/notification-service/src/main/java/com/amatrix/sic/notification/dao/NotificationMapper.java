package com.amatrix.sic.notification.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.notification.entity.PendingNotification;

@Mapper
public interface NotificationMapper {
    int insert(@Param("eventId")String eventId,@Param("userId")Long userId,@Param("payload")String payload);
    List<PendingNotification> queued(@Param("userId")Long userId,@Param("userScoped")boolean userScoped);
    int delivered(@Param("eventId")String eventId,@Param("status")String status);
    int retry(@Param("eventId")String eventId);
    long queuedCount();
    long oldestAge();
}
