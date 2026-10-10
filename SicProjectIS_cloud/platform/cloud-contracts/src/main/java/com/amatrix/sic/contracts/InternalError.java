package com.amatrix.sic.contracts;

public record InternalError(String code, String message, boolean retryable, String correlationId) {
}
