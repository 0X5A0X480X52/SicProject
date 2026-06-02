package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.ProcessDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProcessDocumentDao {
    int insert(ProcessDocument entity);

    ProcessDocument selectById(@Param("documentId") Long documentId);

    List<ProcessDocument> selectAll();

    int updateById(ProcessDocument entity);

    int deleteById(@Param("documentId") Long documentId);
}
