package com.amatrix.sicprojectis_backend.workflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowAssetResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowAssetService {
    private static final String BASE_PATH = "workflows/bpmn_auxiliary_tags_augmented/";
    private static final List<String> ASSET_NAMES = List.of(
            "\u9879\u76ee\u7533\u8bf7_\u8f85\u52a9\u6807\u7b7e\u7248.bpmn",
            "\u7eb5\u5411\u9879\u76ee\u5408\u540c_\u8f85\u52a9\u6807\u7b7e\u7248.bpmn",
            "\u9879\u76ee\u7ed3\u9898_\u8f85\u52a9\u6807\u7b7e\u7248.bpmn");

    private final FlowableBpmnDefinitionParser parser;

    public WorkflowAssetService(FlowableBpmnDefinitionParser parser) {
        this.parser = parser;
    }

    public List<WorkflowAssetResponse> listAssets() {
        return ASSET_NAMES.stream()
                .map(assetName -> {
                    WorkflowBpmnParseResult parseResult = parser.parse(readAssetXml(assetName));
                    return new WorkflowAssetResponse(
                            assetName,
                            BASE_PATH + assetName,
                            parseResult.processConfig().processKey(),
                            parseResult.processConfig().moduleType(),
                            parseResult.processConfig().versionNo());
                })
                .toList();
    }

    public String readAssetXml(String assetName) {
        if (!ASSET_NAMES.contains(assetName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow asset not found");
        }
        try {
            ClassPathResource resource = new ClassPathResource(BASE_PATH + assetName);
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow asset resource not found");
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read workflow asset", ex);
        }
    }
}
