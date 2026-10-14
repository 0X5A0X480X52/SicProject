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
    /** 按创建时间和 ID 取最多 100 条 QUEUED 通知；userScoped 为 true 时仅查指定用户，供重连补发或定时重试。 */
    List<PendingNotification> queued(@Param("userId")Long userId,@Param("userScoped")boolean userScoped);
    int delivered(@Param("eventId")String eventId,@Param("status")String status);
    int retry(@Param("eventId")String eventId);
    /** 统计待投递 QUEUED 通知数，供通知积压指标使用。 */
    long queuedCount();
    /** 计算最早 QUEUED 通知距当前的秒数，无记录时返回 0，供积压年龄指标使用。 */
    long oldestAge();
}
