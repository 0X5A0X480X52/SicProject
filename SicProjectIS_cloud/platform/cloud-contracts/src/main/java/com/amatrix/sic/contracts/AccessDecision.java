package com.amatrix.sic.contracts;

public record AccessDecision(boolean allowed, long projectVersion, String reasonCode) {
    public static AccessDecision allow(long version) {
        return new AccessDecision(true, version, "ALLOWED");
    }

    public static AccessDecision deny(long version, String reasonCode) {
        return new AccessDecision(false, version, reasonCode);
    }
}
