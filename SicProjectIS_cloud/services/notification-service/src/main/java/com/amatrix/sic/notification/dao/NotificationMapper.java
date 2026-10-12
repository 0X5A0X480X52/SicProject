package com.amatrix.sic.notification.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.notification.entity.PendingNotification;

/**
 * 通知 MyBatis 接口：插入待投递记录、按用户取 QUEUED（区分定向与广播）、按 eventId 标记投递或重试，以及待投递数与最老年龄计数。
 */
@Mapper
public interface NotificationMapper {
    int insert(@Param("eventId")String eventId,@Param("userId")Long userId,@Param("payload")String payload);
    List<PendingNotification> queued(@Param("userId")Long userId,@Param("userScoped")boolean userScoped);
    int delivered(@Param("eventId")String eventId,@Param("status")String status);
    int retry(@Param("eventId")String eventId);
    long queuedCount();
    long oldestAge();
}
