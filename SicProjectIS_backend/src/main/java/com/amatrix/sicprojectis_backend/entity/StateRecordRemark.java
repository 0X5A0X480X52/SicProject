package com.amatrix.sicprojectis_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class StateRecordRemark {
    private Long remarkId;
    private Long stateRecordId;
    private Long participantUserId;
    private Long participantRoleId;
    private String participantType;
    private String actionType;
    private String result;
    private Boolean isOperator;
    private BigDecimal score;
    private String remarkContent;
    private Boolean isFinal;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
