package com.amatrix.sic.workflow.bootstrap;

import com.amatrix.sic.workflow.service.definition.WorkflowDefinitionModelStore;
import com.amatrix.sic.workflow.workflow.parser.FlowableBpmnDefinitionParser;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.workflow.definition.WorkflowAssetCatalog;

import java.util.Objects;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.amatrix.sic.workflow.dao.WorkflowDefinitionMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowAssetInitializer implements ApplicationRunner {
    private final WorkflowDefinitionMapper mapper;
    private final WorkflowDefinitionModelStore store;
    private final FlowableBpmnDefinitionParser parser;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Value("${sic.workflow.assets.bootstrap-if-missing:false}")
    private boolean bootstrapMissing;

    public WorkflowAssetInitializer(WorkflowDefinitionMapper mapper, FlowableBpmnDefinitionParser parser, ObjectMapper objectMapper, WorkflowDefinitionModelStore store) {
        this.mapper = mapper;
        this.store = store;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String assetName : WorkflowAssetCatalog.names()) {
            install(assetName, WorkflowAssetCatalog.read(assetName));
        }
    }

    private void install(String assetName, String bpmnXml) {
        WorkflowBpmnParseResult model = parser.parse(bpmnXml);
        if (model.nodes().isEmpty() || model.transitions().isEmpty()) {
            throw new IllegalStateException("Workflow asset has no executable model: " + assetName);
        }
        String moduleType = required(model.processConfig().moduleType(), "moduleType", assetName);
        int version = model.processConfig().versionNo() == null ? 1 : model.processConfig().versionNo();
        // An adopted database may publish a version absent from the bundled release assets.
        if (!bootstrapMissing) {
            Integer installed = mapper.installed(moduleType);
            if (installed != null && installed > 0) return;
            throw new IllegalStateException("Missing workflow release seed: " + moduleType + "; initialize SQL before startup");
        }
        boolean newerPublished = mapper.newerPublished(moduleType,version);
        Long existing = mapper.versionId(moduleType,version);
        if (existing != null) {
            Long definitionId = existing;
            Integer configured = mapper.nodeCount(definitionId);
            // Preserve installed IDs, customizations and published definitions across every restart.
            if (configured != null && configured > 0) return;
            Integer inUse = mapper.moduleCount(definitionId);
            if (inUse != null && inUse > 0)
                throw new IllegalStateException("Workflow definition is used but has no node configuration: " + moduleType);
        }
        if (!bootstrapMissing)
            throw new IllegalStateException("Missing workflow release seed: " + moduleType + "; initialize SQL before startup");
        Long definitionId;
        java.util.Map<String,Object> values=new java.util.LinkedHashMap<>();
        values.put("code",moduleType);values.put("name",Objects.requireNonNullElse(model.processConfig().processName(),moduleType));
        values.put("version",version);values.put("status",newerPublished?"INACTIVE":"ACTIVE");values.put("published",!newerPublished);
        values.put("bpmn",bpmnXml);values.put("json",write(model));
        if(existing==null){mapper.insert(values);definitionId=((Number)Objects.requireNonNull(values.get("id"))).longValue();}
        else {definitionId=existing;values.put("id",definitionId);mapper.replaceEmpty(values);}
        store.persist(definitionId, model);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize workflow model", ex);
        }
    }

    private static String required(String value, String field, String assetName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Workflow asset " + assetName + " is missing " + field);
        }
        return value;
    }
}
