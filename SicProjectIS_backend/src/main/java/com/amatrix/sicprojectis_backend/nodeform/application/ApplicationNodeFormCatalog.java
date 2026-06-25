package com.amatrix.sicprojectis_backend.nodeform.application;

import java.util.List;

import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDataKind;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDefinition;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormModuleType;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormWriteMode;

public final class ApplicationNodeFormCatalog {
    private ApplicationNodeFormCatalog() {
    }

    public static List<NodeFormDefinition> definitions() {
        return List.of(
                form("APPLICATION_NOTICE_FORM", "PublishNoticeTask", "APPLICATION_NOTICE_PUBLISHING", "申报通知", NodeFormDataKind.NOTICE, NodeFormWriteMode.HISTORY_RECORD),
                form("PROJECT_APPLICATION_FORM", "SubmitApplicationTask", "APPLICATION_DRAFT", "项目申请书", NodeFormDataKind.APPLICATION_DRAFT, NodeFormWriteMode.SINGLE_INSTANCE),
                form("DEPT_APPLICATION_REVIEW_FORM", "DeptReviewTask", "APPLICATION_DEPT_REVIEWING", "二级单位审核", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("DEPT_EXPERT_ASSIGN_FORM", "DeptExpertAssignTask", "APPLICATION_DEPT_EXPERT_ASSIGNING", "二级单位分配评审专家", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("DEPT_EXPERT_REVIEW_FORM", "DeptExpertReviewTask", "APPLICATION_DEPT_EXPERT_REVIEWING", "二级单位专家审核提交", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("DEPT_EXPERT_SUMMARY_FORM", "DeptExpertSummaryTask", "APPLICATION_DEPT_EXPERT_SUMMARIZING", "二级单位汇总提交专家评审结果", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("SCIENCE_INITIAL_REVIEW_FORM", "ScienceOfficeInitialReviewTask", "APPLICATION_SCIENCE_INITIAL_REVIEWING", "科技处初审", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("SCIENCE_EXPERT_ASSIGN_FORM", "ScienceExpertAssignTask", "APPLICATION_SCIENCE_EXPERT_ASSIGNING", "科技处分配评审专家", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("SCIENCE_EXPERT_REVIEW_FORM", "ScienceExpertReviewTask", "APPLICATION_SCIENCE_EXPERT_REVIEWING", "科技处专家审核提交", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("SCIENCE_EXPERT_SUMMARY_FORM", "ScienceExpertSummaryTask", "APPLICATION_SCIENCE_EXPERT_SUMMARIZING", "科技处汇总提交专家评审结果", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("APPLICATION_PUBLICITY_FORM", "ScienceOfficePublicityTask", "APPLICATION_PUBLICITY", "公示", NodeFormDataKind.PUBLICITY, NodeFormWriteMode.HISTORY_RECORD),
                form("APPLICATION_SUBMISSION_REVIEW_FORM", "ScienceOfficeSubmitTask", "APPLICATION_SCIENCE_SUBMITTING", "审核上报", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("APPLICATION_AUTHORITY_RESULT_FORM", "AuthorityReviewTask", "APPLICATION_AUTHORITY_REVIEWING", "主管部门结果", NodeFormDataKind.EXTERNAL_RESULT, NodeFormWriteMode.HISTORY_RECORD),
                form("APPLICATION_SEAL_FORM", "SignAndSealTask", "APPLICATION_SIGN_SEALING", "签字用印", NodeFormDataKind.SEAL, NodeFormWriteMode.HISTORY_RECORD),
                form("APPLICATION_FINAL_SUBMISSION_FORM", "SubmitFinalMaterialsTask", "APPLICATION_FINAL_MATERIAL_SUBMITTING", "正式报送", NodeFormDataKind.SUBMISSION, NodeFormWriteMode.HISTORY_RECORD));
    }

    private static NodeFormDefinition form(String code, String nodeId, String stateCode, String title,
            NodeFormDataKind kind, NodeFormWriteMode mode) {
        return new NodeFormDefinition(code, NodeFormModuleType.APPLICATION, nodeId, stateCode, title, kind, mode, true, List.of());
    }
}

