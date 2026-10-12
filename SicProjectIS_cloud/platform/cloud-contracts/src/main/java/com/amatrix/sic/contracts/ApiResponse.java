package com.amatrix.sic.contracts;

/**
 * 业务 API 与 Feign 共用的 JSON 信封。
 * @param success 业务处理是否成功；Feign 调用方仍需同时处理非 2xx HTTP 与反序列化失败
 * @param data 成功数据；Void 或错误响应通常为 null，不能在 success=false 时继续使用
 * @param message 可读结果说明；当前成功工厂固定为 OK，不作为稳定的程序分支代码
 */
public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK");
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
