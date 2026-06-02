package com.amatrix.sicprojectis_backend.document.dao;

import com.amatrix.sicprojectis_backend.document.entity.ProcessDocumentFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProcessDocumentFileDao {
    int insert(ProcessDocumentFile entity);

    ProcessDocumentFile selectById(@Param("documentFileId") Long documentFileId);

    List<ProcessDocumentFile> selectAll();

    int updateById(ProcessDocumentFile entity);

    int deleteById(@Param("documentFileId") Long documentFileId);
}
