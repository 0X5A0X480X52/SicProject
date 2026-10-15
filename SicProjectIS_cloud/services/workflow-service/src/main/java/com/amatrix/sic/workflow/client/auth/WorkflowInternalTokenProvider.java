package com.amatrix.sic.workflow.client.auth;

import com.amatrix.sic.workflow.client.WorkflowServiceTokenClient;
import com.amatrix.sic.workflow.config.properties.WorkflowClientProperties;

import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * 缓存 workflow-service 内部 Token，提前 60 秒刷新并提供 Bearer Header。
 */
@Component
public class WorkflowInternalTokenProvider {
    private final WorkflowServiceTokenClient client;
    private final WorkflowClientProperties properties;
    private volatile WorkflowServiceTokenClient.Token cached;
    public WorkflowInternalTokenProvider(WorkflowServiceTokenClient client,WorkflowClientProperties properties){this.client=client;this.properties=properties;}

    /**
     * 返回 workflow-service 调用 IAM、Project 和 Material 内部 API 的完整 Bearer Header。
     * <p>首次使用或距离 token 到期不足 60 秒时同步兑换并更新本实例缓存；其他调用复用现有 token。
     * IAM 业务响应失败或缺少 token 时抛出异常，不把依赖故障转换成权限允许或无数据结果。
     */
    public synchronized String authorization(){
        if(cached==null||cached.expiresAt().isBefore(Instant.now().plusSeconds(60))){
            var response=client.token(properties.bootstrapSecret(),"workflow-service");
            if(!response.success()||response.data()==null)throw new IllegalStateException("IAM did not issue a workflow-service token");
            cached=response.data();
        }
        return "Bearer "+cached.token();
    }
}
