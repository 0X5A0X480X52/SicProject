package com.amatrix.sic.workflow.entity;

public record ExpertBatch(Long id, Long moduleId, Long workflowNodeId, int roundNo, boolean removeHighestLowest, java.math.BigDecimal passScore, java.math.BigDecimal recommendScore, String status) {}
