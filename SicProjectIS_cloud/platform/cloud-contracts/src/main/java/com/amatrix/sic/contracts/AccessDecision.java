package com.amatrix.sic.contracts;

/**
 * Project 单项权限判定结果。
 * @param allowed 当前身份与操作范围是否获准；false 是正常业务拒绝，不等同于调用异常
 * @param projectVersion 判定时观察到的项目授权版本，项目不存在时批量结果可为 0
 * @param reasonCode ALLOWED 或稳定的拒绝原因代码，供诊断和界面提示映射，不作为新授权依据
 */
public record AccessDecision(boolean allowed, long projectVersion, String reasonCode) {
    public static AccessDecision allow(long version) {
        return new AccessDecision(true, version, "ALLOWED");
    }

    public static AccessDecision deny(long version, String reasonCode) {
        return new AccessDecision(false, version, reasonCode);
    }
}
