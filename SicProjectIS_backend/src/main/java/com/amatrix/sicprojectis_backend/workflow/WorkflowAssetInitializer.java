package com.amatrix.sicprojectis_backend.workflow;

import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowDefinitionDao;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowAssetResponse;

@Component
public class WorkflowAssetInitializer implements ApplicationRunner {
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowAssetService workflowAssetService;

    public WorkflowAssetInitializer(
            WorkflowDefinitionDao workflowDefinitionDao,
            WorkflowDefinitionService workflowDefinitionService,
            WorkflowAssetService workflowAssetService) {
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.workflowDefinitionService = workflowDefinitionService;
        this.workflowAssetService = workflowAssetService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        List<WorkflowAssetResponse> assets = workflowAssetService.listAssets();
        for (WorkflowAssetResponse asset : assets) {
            if (workflowDefinitionDao.selectLatestActiveByModuleType(asset.moduleType()) == null) {
                workflowDefinitionService.publishAsset(asset.assetName());
            }
        }
    }
}
