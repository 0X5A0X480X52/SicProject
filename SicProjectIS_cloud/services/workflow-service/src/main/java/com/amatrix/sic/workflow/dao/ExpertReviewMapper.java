package com.amatrix.sic.workflow.dao;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.*;
@Mapper
public interface ExpertReviewMapper {
    ExpertBatch batch(@Param("id") Long id,@Param("lock") boolean lock);
    ExpertAssignment lockAssignment(@Param("id") Long id);
    Long assignmentBatch(@Param("id") Long id);
    ExpertModule lockModule(@Param("id") Long id);
    int nodeMatches(@Param("id") Long id,@Param("definition") Long definition,@Param("node") String node);
    String nodeCode(@Param("id") Long id,@Param("definition") Long definition);
    ExpertGrant grant(@Param("node") Long node,@Param("module") Long module);
    int matchingAssignments(@Param("module") Long module,@Param("round") int round,@Param("node") Long node,@Param("expert") Long expert);
    int assigned(@Param("batch") Long batch,@Param("expert") Long expert);
    List<BigDecimal> scores(@Param("id") Long id);
    int assignedCount(@Param("id") Long id);
    int submittedCount(@Param("id") Long id);
    int insertBatch(Map<String,Object> values);
    int insertAssignment(@Param("batch") Long batch,@Param("expert") Long expert,@Param("name") String name,@Param("org") String org,@Param("title") String title);
    int insertScore(Map<String,Object> values);
    int submit(Map<String,Object> values);
    int aggregate(Map<String,Object> values);
    int deleteScores(@Param("id") Long id);
    int deleteAssignment(@Param("id") Long id);
}
