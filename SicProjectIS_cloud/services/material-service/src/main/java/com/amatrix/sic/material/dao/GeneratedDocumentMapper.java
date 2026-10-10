package com.amatrix.sic.material.dao;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GeneratedDocumentMapper {
    int insert(Map<String,Object> document);
    int materialVersion(@Param("command") String command,@Param("id") Long id);
}
