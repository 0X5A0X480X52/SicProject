package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.amatrix.sicprojectis_backend.document.dao.ProcessDocumentDao;
import com.amatrix.sicprojectis_backend.document.entity.ProcessDocument;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDocumentConfigDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProcessDocumentGenerationService {
    private final WorkflowNodeDocumentConfigDao documentConfigDao;
    private final ProcessDocumentDao processDocumentDao;
    private final ObjectMapper objectMapper;

    public ProcessDocumentGenerationService(WorkflowNodeDocumentConfigDao documentConfigDao,
            ProcessDocumentDao processDocumentDao, ObjectMapper objectMapper) {
        this.documentConfigDao = documentConfigDao;
        this.processDocumentDao = processDocumentDao;
        this.objectMapper = objectMapper;
    }

    public void generateForNodeComplete(ProjectModuleInstance module, WorkflowNode completedNode, ModuleStateRecord stateRecord) {
        generate(module, completedNode, stateRecord, "ON_NODE_COMPLETE", "AFTER_APPROVE");
    }

    public void generateForProcessEnd(ProjectModuleInstance module, WorkflowNode endNode, ModuleStateRecord stateRecord) {
        generate(module, endNode, stateRecord, "ON_PROCESS_END");
    }

    private void generate(ProjectModuleInstance module, WorkflowNode node, ModuleStateRecord stateRecord, String... timings) {
        if (node == null || node.getWorkflowNodeId() == null) {
            return;
        }
        for (var config : documentConfigDao.selectByWorkflowNodeId(node.getWorkflowNodeId())) {
            if (Boolean.FALSE.equals(config.getEnabled()) || !matches(config.getGenerateTiming(), timings)) {
                continue;
            }
            if (alreadyGenerated(module.getModuleInstanceId(), stateRecord.getStateRecordId(), config.getDocumentTypeCode())) {
                continue;
            }
            ProcessDocument document = new ProcessDocument();
            document.setModuleInstanceId(module.getModuleInstanceId());
            document.setGeneratedStateRecordId(stateRecord.getStateRecordId());
            document.setDocumentTypeCode(config.getDocumentTypeCode());
            document.setDocumentNo("DOC-" + module.getModuleInstanceId() + "-" + stateRecord.getStateRecordId() + "-"
                    + config.getDocumentTypeCode());
            document.setDocumentTitle(config.getDocumentTypeName());
            document.setDocumentStatus("GENERATED");
            document.setSnapshotJson(snapshot(module, node, stateRecord, config.getTemplateCode()));
            document.setGeneratedAt(LocalDateTime.now());
            processDocumentDao.insert(document);
        }
    }

    private boolean alreadyGenerated(Long moduleInstanceId, Long stateRecordId, String documentTypeCode) {
        return processDocumentDao.selectAll().stream()
                .anyMatch(document -> java.util.Objects.equals(document.getModuleInstanceId(), moduleInstanceId)
                        && java.util.Objects.equals(document.getGeneratedStateRecordId(), stateRecordId)
                        && java.util.Objects.equals(document.getDocumentTypeCode(), documentTypeCode));
    }

    private boolean matches(String actual, String... expected) {
        if (actual == null) {
            return false;
        }
        String normalized = actual.toUpperCase(Locale.ROOT);
        for (String item : expected) {
            if (normalized.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private String snapshot(ProjectModuleInstance module, WorkflowNode node, ModuleStateRecord stateRecord, String templateCode) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("moduleInstanceId", module.getModuleInstanceId());
        value.put("projectId", module.getProjectId());
        value.put("moduleType", module.getModuleType());
        value.put("stateRecordId", stateRecord.getStateRecordId());
        value.put("stateCode", stateRecord.getToState());
        value.put("nodeId", node.getNodeId());
        value.put("templateCode", templateCode);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create process document snapshot", ex);
        }
    }
}
