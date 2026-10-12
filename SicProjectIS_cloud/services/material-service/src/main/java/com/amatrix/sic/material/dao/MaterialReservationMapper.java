package com.amatrix.sic.material.dao;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.material.entity.*;
import com.amatrix.sic.contracts.MaterialReservationCommand;

/**
 * 材料预留 MyBatis 接口：按 commandId 幂等查询、预留与明细插入、可选版本加锁、确认/释放/过期清理及状态读取。
 */
@Mapper
public interface MaterialReservationMapper {
    ReservationRow byCommand(@Param("id") String id);
    List<ReservationItemRow> reservedItems(@Param("id") String id);
    List<ReservationItemRow> available(@Param("ids") List<Long> ids,@Param("lock") boolean lock);
    int insert(@Param("id") String id,@Param("command") MaterialReservationCommand command,@Param("expires") Instant expires);
    int insertItem(@Param("id") String id,@Param("item") ReservationItemRow item);
    Integer deleted(@Param("id") String id);
    int confirm(@Param("id") String id);
    String status(@Param("id") String id);
    int release(@Param("id") String id);
    int expire();
}
