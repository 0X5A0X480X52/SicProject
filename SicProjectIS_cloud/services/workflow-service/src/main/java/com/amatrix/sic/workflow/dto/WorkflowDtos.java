package com.amatrix.sic.workflow.dto;

import java.util.List;
import java.util.Map;

public final class WorkflowDtos {
    private WorkflowDtos(){}
    public record UploadWorkflowDefinitionRequest(String bpmnXml){}
    public record StartModuleRequest(String moduleType){}
    public record TransitionRequest(String eventType,Integer expectedSeq,String result,String remark,
            List<Long> materialVersionIds,String formCode,Map<String,Object> nodeFormData){}
}
