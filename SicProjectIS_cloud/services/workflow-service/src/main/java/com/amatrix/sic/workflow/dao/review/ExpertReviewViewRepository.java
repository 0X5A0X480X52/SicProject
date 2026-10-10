package com.amatrix.sic.workflow.dao.review;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.amatrix.sic.workflow.dao.ExpertReviewViewMapper;
import org.springframework.stereotype.Repository;

/** Assembles the public review shape after all mapper results have been closed. */
@Repository
public class ExpertReviewViewRepository {
    private final ExpertReviewViewMapper mapper;

    public ExpertReviewViewRepository(ExpertReviewViewMapper mapper) { this.mapper = mapper; }

    public Map<String,Object> batchDetail(Long batchId) {
        Map<String,Object> batch = mapper.batch(batchId);
        if (batch == null) return null;
        return assemble(List.of(batch), false).getFirst();
    }

    public List<Map<String,Object>> moduleReviews(Long moduleId) {
        return assemble(mapper.batches(moduleId), true);
    }

    private List<Map<String,Object>> assemble(List<Map<String,Object>> batches, boolean includeNode) {
        if (batches.isEmpty()) return List.of();
        List<Long> batchIds = batches.stream().map(row -> (Long)row.get("batchId")).toList();
        List<Map<String,Object>> assignments = bounded(batchIds, mapper::assignments);
        List<Long> assignmentIds = assignments.stream().map(row -> (Long)row.get("assignmentId")).toList();
        List<Map<String,Object>> scores = bounded(assignmentIds, mapper::scores);
        Map<Long,List<Map<String,Object>>> scoresByAssignment = group(scores, "assignmentId");
        Map<Long,List<Map<String,Object>>> assignmentsByBatch = new LinkedHashMap<>();
        for (Map<String,Object> assignment : assignments) {
            Long id = (Long)assignment.get("assignmentId");
            assignmentsByBatch.computeIfAbsent((Long)assignment.get("batchId"), key -> new ArrayList<>())
                    .add(linked("assignment", assignment, "scores", scoresByAssignment.getOrDefault(id,List.of())));
        }
        List<Map<String,Object>> result = new ArrayList<>();
        for (Map<String,Object> batch : batches) {
            Object configured = batch.remove("configuredNodeId");
            Map<String,Object> row = linked("batch",batch,"assignments",assignmentsByBatch.getOrDefault((Long)batch.get("batchId"),List.of()));
            if (includeNode) row.put("reviewNodeId", configured == null ? null : configured.toString()
                    .replace("ExpertAssignTask","ExpertReviewTask").replace("ExpertSummaryTask","ExpertReviewTask"));
            result.add(row);
        }
        return result;
    }

    private static List<Map<String,Object>> bounded(List<Long> ids, Function<List<Long>,List<Map<String,Object>>> fetch) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (int offset=0; offset<ids.size(); offset+=100)
            result.addAll(fetch.apply(ids.subList(offset,Math.min(offset+100,ids.size()))));
        return result;
    }

    private static Map<Long,List<Map<String,Object>>> group(List<Map<String,Object>> rows,String key) {
        Map<Long,List<Map<String,Object>>> result = new LinkedHashMap<>();
        for (Map<String,Object> row : rows) result.computeIfAbsent((Long)row.get(key), ignored -> new ArrayList<>()).add(row);
        return result;
    }

    private static Map<String,Object> linked(Object... values) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int index=0;index<values.length;index+=2) result.put(String.valueOf(values[index]),values[index+1]);
        return result;
    }
}
