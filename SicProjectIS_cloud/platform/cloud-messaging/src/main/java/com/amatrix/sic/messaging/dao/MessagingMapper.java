package com.amatrix.sic.messaging.dao;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.messaging.entity.OutboxEntry;

public interface MessagingMapper {
    int insertOutbox(@Param("id")String id,@Param("type")String type,@Param("route")String route,
                     @Param("payload")String payload,@Param("now")Instant now);
    List<OutboxEntry> candidates(@Param("now")Instant now,@Param("limit")int limit);
    int claim(@Param("id")String id,@Param("owner")String owner,@Param("now")Instant now,@Param("until")Instant until);
    int published(@Param("id")String id,@Param("owner")String owner,@Param("now")Instant now);
    int failed(@Param("id")String id,@Param("owner")String owner,@Param("error")String error,@Param("next")Instant next);
    int consume(@Param("id")String id,@Param("consumer")String consumer,@Param("now")Instant now);
    long pending();
    long failedCount();
    long oldestAge();
}
