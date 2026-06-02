package com.amatrix.sicprojectis_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class StateRecordContextView {
    private Long stateRecordId;
    private Long moduleInstanceId;
    private Long projectId;
    private String moduleType;
    private Long workflowDefinitionId;
    private Integer seq;
    private Integer roundNo;
    private String eventType;
    private String fromState;
    private String toState;
    private String fromNodeId;
    private String toNodeId;
    private String result;
    private String summary;
    private String payloadJson;
    private LocalDateTime transitionTime;
    private Long toWorkflowNodeId;
    private String toNodeName;
    private String toNodeType;
    private String toLaneName;
    private String toResponsibleActorName;
    private String toOperationMode;
    private String toRepresentedActorName;
    private Long operatorRemarkId;
    private Long operatorUserId;
    private String operatorName;
    private Long operatorRoleId;
    private String operatorRoleCode;
    private String operatorRoleName;
    private String operatorParticipantType;
    private String operatorActionType;
    private String operatorRemark;
}
