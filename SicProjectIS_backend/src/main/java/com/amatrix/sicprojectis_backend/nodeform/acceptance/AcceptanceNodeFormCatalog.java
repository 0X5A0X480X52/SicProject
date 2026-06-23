package com.amatrix.sicprojectis_backend.nodeform.acceptance;

import java.util.List;

import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDataKind;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDefinition;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormModuleType;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormWriteMode;

public final class AcceptanceNodeFormCatalog {
    private AcceptanceNodeFormCatalog() {
    }

    public static List<NodeFormDefinition> definitions() {
        return List.of(
                form("ACCEPTANCE_NOTICE_FORM", "PublishAcceptanceNoticeTask", "ACCEPTANCE_NOTICE_PUBLISHING", "结题通知", NodeFormDataKind.NOTICE, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_NOTIFY_LEADER_FORM", "DeptNotifyLeaderTask", "ACCEPTANCE_DEPT_NOTIFYING", "通知负责人", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("FINANCIAL_SETTLEMENT_FORM", "FinancialSettlementTask", "ACCEPTANCE_FINANCIAL_SETTLEMENT", "经费决算", NodeFormDataKind.FINANCIAL_SETTLEMENT, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_APPLICATION_FORM", "SubmitAcceptanceMaterialsTask", "ACCEPTANCE_MATERIAL_DRAFT", "结题申请", NodeFormDataKind.ACCEPTANCE_DRAFT, NodeFormWriteMode.SINGLE_INSTANCE),
                form("ACCEPTANCE_REPORT_FORM", "SubmitAcceptanceMaterialsTask", "ACCEPTANCE_MATERIAL_DRAFT", "结题报告", NodeFormDataKind.ACCEPTANCE_DRAFT, NodeFormWriteMode.SINGLE_INSTANCE),
                form("ACHIEVEMENT_LIST_FORM", "SubmitAcceptanceMaterialsTask", "ACCEPTANCE_MATERIAL_DRAFT", "成果清单", NodeFormDataKind.ACHIEVEMENT, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_DEPT_REVIEW_FORM", "DeptReviewTask", "ACCEPTANCE_DEPT_REVIEWING", "二级单位审核", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_SCIENCE_REVIEW_FORM", "ScienceOfficeReviewTask", "ACCEPTANCE_SCIENCE_REVIEWING", "科技处审核", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_AUTHORITY_REVIEW_FORM", "AuthorityReviewTask", "ACCEPTANCE_AUTHORITY_REVIEWING", "主管部门审核", NodeFormDataKind.EXTERNAL_RESULT, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_SEAL_FORM", "SignSealAcceptanceTask", "ACCEPTANCE_SIGN_SEALING", "签字用印", NodeFormDataKind.SEAL, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_FINAL_SUBMISSION_FORM", "SubmitFinalMaterialsTask", "ACCEPTANCE_FINAL_MATERIAL_SUBMITTING", "最终报送", NodeFormDataKind.SUBMISSION, NodeFormWriteMode.HISTORY_RECORD),
                form("EXPERT_ACCEPTANCE_REVIEW_FORM", "ExpertReviewTask", "ACCEPTANCE_EXPERT_REVIEWING", "专家评审", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("EXPERT_ACCEPTANCE_REVIEW_SUMMARY_FORM", "ExpertReviewTask", "ACCEPTANCE_EXPERT_REVIEWING", "专家评审汇总", NodeFormDataKind.EXPERT_REVIEW, NodeFormWriteMode.HISTORY_RECORD),
                form("ACCEPTANCE_CERTIFICATE_DOCUMENT", "IssueCertificateTask", "ACCEPTANCE_CERTIFICATE_ISSUING", "验收证书", NodeFormDataKind.DOCUMENT, NodeFormWriteMode.READ_ONLY),
                form("ACCEPTANCE_SUMMARY_FORM", "IssueCertificateTask", "ACCEPTANCE_CERTIFICATE_ISSUING", "结题汇总单", NodeFormDataKind.DOCUMENT, NodeFormWriteMode.READ_ONLY),
                form("ACCEPTANCE_FAIL_DOCUMENT", "IssueFailFileTask", "ACCEPTANCE_FAIL_FILE_ISSUING", "不通过文件", NodeFormDataKind.DOCUMENT, NodeFormWriteMode.READ_ONLY),
                form("SURPLUS_FUNDS_RETURN_FORM", "ReturnSurplusFundsTask", "ACCEPTANCE_SURPLUS_FUNDS_RETURNING", "结余退还", NodeFormDataKind.SURPLUS_RETURN, NodeFormWriteMode.HISTORY_RECORD));
    }

    private static NodeFormDefinition form(String code, String nodeId, String stateCode, String title,
            NodeFormDataKind kind, NodeFormWriteMode mode) {
        return new NodeFormDefinition(code, NodeFormModuleType.ACCEPTANCE, nodeId, stateCode, title, kind, mode, true, List.of());
    }
}
