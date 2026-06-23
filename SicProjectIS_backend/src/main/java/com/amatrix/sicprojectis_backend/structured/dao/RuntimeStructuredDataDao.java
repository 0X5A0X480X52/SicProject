package com.amatrix.sicprojectis_backend.structured.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;

@Mapper
public interface RuntimeStructuredDataDao {
    int insertCheckItem(StateRecordCheckItem record);
    int updateCheckItem(StateRecordCheckItem record);
    int deleteCheckItem(@Param("checkItemId") Long checkItemId);
    List<StateRecordCheckItem> selectCheckItemsByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);
    List<StateRecordCheckItem> selectCheckItemsByStateRecordId(@Param("stateRecordId") Long stateRecordId);
    int insertExternalResult(ExternalResultRecord record);
    int updateExternalResult(ExternalResultRecord record);
    int deleteExternalResult(@Param("externalResultId") Long externalResultId);
    List<ExternalResultRecord> selectExternalResultsByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);
    ExternalResultRecord selectLatestExternalResult(@Param("moduleInstanceId") Long moduleInstanceId, @Param("resultType") String resultType);
    int insertSealRecord(SealRecord record);
    int updateSealRecord(SealRecord record);
    int deleteSealRecord(@Param("sealRecordId") Long sealRecordId);
    List<SealRecord> selectSealRecordsByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);
    int insertSubmissionRecord(SubmissionRecord record);
    int updateSubmissionRecord(SubmissionRecord record);
    int deleteSubmissionRecord(@Param("submissionId") Long submissionId);
    List<SubmissionRecord> selectSubmissionRecordsByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);
    int insertArchiveRecord(ArchiveRecord record);
    int updateArchiveRecord(ArchiveRecord record);
    int deleteArchiveRecord(@Param("archiveId") Long archiveId);
    List<ArchiveRecord> selectArchiveRecordsByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);
}
