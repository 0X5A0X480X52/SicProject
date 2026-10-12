package com.amatrix.sic.workflow.dao;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/**
 * 公告 MyBatis 接口：公告写入、按全部/部门/用户范围查询与维护。
 */
@Mapper
public interface NoticeMapper {
    List<Map<String,Object>> list(@Param("module") String module);
    Map<String,Object> find(@Param("id") Long id);
    int insert(Map<String,Object> values);
    int update(Map<String,Object> values);
}
