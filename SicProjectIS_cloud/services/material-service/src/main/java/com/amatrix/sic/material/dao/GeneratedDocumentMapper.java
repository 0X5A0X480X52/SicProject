package com.amatrix.sic.material.dao;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 生成单据 MyBatis 接口：以 INSERT IGNORE 幂等写入生成记录，并按 commandId 把材料版本 ID 回写到该生成单据行。
 */
@Mapper
public interface GeneratedDocumentMapper {
    int insert(Map<String,Object> document);
    int materialVersion(@Param("command") String command,@Param("id") Long id);
}
