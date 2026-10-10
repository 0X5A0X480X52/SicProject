package com.amatrix.sic.project.entity;

public record ApplicationSagaRow(String sagaId,Long projectId,Long actorUserId,String status,String responseJson) {}
