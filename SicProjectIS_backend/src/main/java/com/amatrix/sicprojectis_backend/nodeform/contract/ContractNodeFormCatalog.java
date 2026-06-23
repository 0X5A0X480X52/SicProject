package com.amatrix.sicprojectis_backend.nodeform.contract;

import java.util.List;

import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDataKind;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDefinition;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormModuleType;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormWriteMode;

public final class ContractNodeFormCatalog {
    private ContractNodeFormCatalog() {
    }

    public static List<NodeFormDefinition> definitions() {
        return List.of(
                form("PROJECT_APPROVAL_FORM", "ApprovalRegistrationTask", "CONTRACT_APPROVAL_REGISTERING", "批准立项", NodeFormDataKind.EXTERNAL_RESULT, NodeFormWriteMode.HISTORY_RECORD),
                form("CONTRACT_DRAFT_FORM", "FillContractTask", "CONTRACT_DRAFT", "填写合同", NodeFormDataKind.CONTRACT_DRAFT, NodeFormWriteMode.SINGLE_INSTANCE),
                form("CONTRACT_DEPT_REVIEW_FORM", "DeptReviewTask", "CONTRACT_DEPT_REVIEWING", "二级单位审核", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("CONTRACT_SCIENCE_REVIEW_FORM", "ScienceOfficeReviewTask", "CONTRACT_SCIENCE_REVIEWING", "科技处审核", NodeFormDataKind.CHECK_ITEM, NodeFormWriteMode.HISTORY_RECORD),
                form("CONTRACT_AUTHORITY_REVIEW_FORM", "AuthorityReviewTask", "CONTRACT_AUTHORITY_REVIEWING", "主管部门审核", NodeFormDataKind.EXTERNAL_RESULT, NodeFormWriteMode.HISTORY_RECORD),
                form("CONTRACT_PDF_PRINT_FORM", "PrintPdfContractTask", "CONTRACT_PDF_PRINTING", "打印合同", NodeFormDataKind.DOCUMENT, NodeFormWriteMode.READ_ONLY),
                form("CONTRACT_LEADER_SIGN_FORM", "LeaderSignTask", "CONTRACT_LEADER_SIGNING", "负责人签字", NodeFormDataKind.SEAL, NodeFormWriteMode.HISTORY_RECORD),
                form("CONTRACT_SCHOOL_SEAL_FORM", "SchoolSealTask", "CONTRACT_SCHOOL_SEALING", "学校盖章", NodeFormDataKind.SEAL, NodeFormWriteMode.HISTORY_RECORD),
                form("CONTRACT_AUTHORITY_SEAL_FORM", "AuthoritySealTask", "CONTRACT_AUTHORITY_SEALING", "主管部门盖章", NodeFormDataKind.EXTERNAL_RESULT, NodeFormWriteMode.HISTORY_RECORD),
                form("CONTRACT_ARCHIVE_FORM", "ArchiveContractTask", "CONTRACT_ARCHIVING", "合同归档", NodeFormDataKind.ARCHIVE, NodeFormWriteMode.HISTORY_RECORD));
    }

    private static NodeFormDefinition form(String code, String nodeId, String stateCode, String title,
            NodeFormDataKind kind, NodeFormWriteMode mode) {
        return new NodeFormDefinition(code, NodeFormModuleType.CONTRACT, nodeId, stateCode, title, kind, mode, true, List.of());
    }
}
