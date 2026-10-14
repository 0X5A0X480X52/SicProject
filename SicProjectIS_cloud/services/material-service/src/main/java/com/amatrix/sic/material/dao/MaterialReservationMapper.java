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
    /** 按 commandId 查询已有预留 ID、状态和到期时间，供预留命令幂等复用。 */
    ReservationRow byCommand(@Param("id") String id);
    /** 按预留 ID 列出材料版本和类型编码，按版本 ID 排序供预留结果恢复使用。 */
    List<ReservationItemRow> reservedItems(@Param("id") String id);
    /** 批量读取未删除版本的项目和类型，按版本 ID 排序；lock 为 true 时加行锁供预留校验。 */
    List<ReservationItemRow> available(@Param("ids") List<Long> ids,@Param("lock") boolean lock);
    int insert(@Param("id") String id,@Param("command") MaterialReservationCommand command,@Param("expires") Instant expires);
    int insertItem(@Param("id") String id,@Param("item") ReservationItemRow item);
    /** 统计预留中已删除的材料版本数，供迟到确认前检查引用是否仍可用。 */
    Integer deleted(@Param("id") String id);
    int confirm(@Param("id") String id);
    /** 按预留 ID 读取当前状态，供确认、释放及幂等处理使用。 */
    String status(@Param("id") String id);
    int release(@Param("id") String id);
    int expire();
}
