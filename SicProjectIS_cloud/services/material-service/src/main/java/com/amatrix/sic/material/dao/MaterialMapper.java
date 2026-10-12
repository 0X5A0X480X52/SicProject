package com.amatrix.sic.material.dao;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.material.entity.*;

/**
 * 材料 MyBatis 接口：材料类型的确保与生成及查询、材料行加锁获取、版本号递增与旧版本 SUPERSEDED、版本插入/加锁读取/删除，以及生成单据与预留占用判定。
 */
@Mapper
public interface MaterialMapper {
    int ensureType(@Param("code") String code);
    int generatedType(@Param("code") String code,@Param("name") String name,@Param("module") String module);
    MaterialTypeRow type(@Param("code") String code);
    Long material(@Param("project") Long project,@Param("code") String code,@Param("lock") boolean lock);
    int insertMaterial(Map<String,Object> material);
    Integer maximumVersion(@Param("id") Long id);
    int supersede(@Param("id") Long id);
    int insertVersion(Map<String,Object> version);
    List<Map<String,Object>> contexts(@Param("project") Long project,@Param("material") Long material,@Param("version") Long version);
    MaterialVersionRow version(@Param("id") Long id,@Param("lock") boolean lock);
    Integer generated(@Param("id") Long id);
    Integer reserved(@Param("id") Long id);
    int deleteVersion(@Param("id") Long id);
    int scheduleDeletion(@Param("id") Long id,@Param("key") String key);
}
