package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StateRecordCheckItem {
    private Long checkItemId;
    private Long stateRecordId;
    private Long moduleInstanceId;
    private String nodeId;
    private String stateCode;
    private String itemCode;
    private String itemName;
    private String itemType;
    private String itemValue;
    private String itemResult;
    private Boolean required;
    private Boolean passed;
    private String remark;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
