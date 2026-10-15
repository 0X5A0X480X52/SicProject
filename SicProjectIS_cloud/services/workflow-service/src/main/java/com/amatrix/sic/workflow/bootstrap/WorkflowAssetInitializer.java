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

/**
 * 启动校验内置 BPMN 所需的已安装定义，显式启用 bootstrap-if-missing 时才补齐缺失资产。
 * 默认依赖完整 SQL 种子；已有配置、在用定义及自定义发布版本不会在重启时被内置资产覆盖。
 */
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

    /**
     * 遍历发行包中的工作流资产；默认检查 SQL 已安装版本，显式启用 bootstrap-if-missing 时才补齐缺失资产。已配置或在用定义的 ID 和自定义内容保留。
     */
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
        // 接管的数据库可能发布了发行包之外的版本；默认只校验已安装的 SQL 资产。
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
            // 重启保留已有 ID、节点自定义配置和发布状态，避免替换仍被实例引用的定义。
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
