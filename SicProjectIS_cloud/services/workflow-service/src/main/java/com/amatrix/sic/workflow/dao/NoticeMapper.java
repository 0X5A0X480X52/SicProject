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
    /** 按可选模块类型查公告，以发布时间（为空用创建时间）和 ID 倒序返回供后续可见性过滤。 */
    List<Map<String,Object>> list(@Param("module") String module);
    /** 按公告 ID 读取完整记录，供详情及修改前校验使用。 */
    Map<String,Object> find(@Param("id") Long id);
    int insert(Map<String,Object> values);
    int update(Map<String,Object> values);
}
