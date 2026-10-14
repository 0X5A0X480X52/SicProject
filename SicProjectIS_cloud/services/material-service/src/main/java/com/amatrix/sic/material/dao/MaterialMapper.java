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
    /** 按类型编码读取材料配置及文件类型、大小限制，供上传校验使用。 */
    MaterialTypeRow type(@Param("code") String code);
    /** 按项目和材料类型取最早的材料 ID；lock 为 true 时加行锁，供版本创建事务使用。 */
    Long material(@Param("project") Long project,@Param("code") String code,@Param("lock") boolean lock);
    int insertMaterial(Map<String,Object> material);
    /** 读取材料最大版本号，无版本时返回 0；使用锁读取供新版本号计算。 */
    Integer maximumVersion(@Param("id") Long id);
    int supersede(@Param("id") Long id);
    int insertVersion(Map<String,Object> version);
    /** 按可选项目、材料或版本条件查询未删除版本，补充类型、哈希和上传信息并标记当前版本。 */
    List<Map<String,Object>> contexts(@Param("project") Long project,@Param("material") Long material,@Param("version") Long version);
    /** 查询未删除版本的对象键、文件名、类型、大小和项目；lock 为 true 时加行锁。 */
    MaterialVersionRow version(@Param("id") Long id,@Param("lock") boolean lock);
    /** 统计版本关联的生成单据记录，供删除前判断是否为受保护的生成文件。 */
    Integer generated(@Param("id") Long id);
    /** 统计版本处于 RESERVED 或 CONFIRMED 的引用预留数，供删除前检查占用使用。 */
    Integer reserved(@Param("id") Long id);
    int deleteVersion(@Param("id") Long id);
    int scheduleDeletion(@Param("id") Long id,@Param("key") String key);
}
