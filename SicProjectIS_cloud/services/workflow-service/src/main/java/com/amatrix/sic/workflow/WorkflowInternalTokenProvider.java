package com.amatrix.sic.workflow;

import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class WorkflowInternalTokenProvider {
    private final WorkflowServiceTokenClient client;
    private final WorkflowClientProperties properties;
    private volatile WorkflowServiceTokenClient.Token cached;
    public WorkflowInternalTokenProvider(WorkflowServiceTokenClient client,WorkflowClientProperties properties){this.client=client;this.properties=properties;}
    public synchronized String authorization(){
        if(cached==null||cached.expiresAt().isBefore(Instant.now().plusSeconds(60))){
            var response=client.token(properties.bootstrapSecret(),"workflow-service");
            if(!response.success()||response.data()==null)throw new IllegalStateException("IAM did not issue a workflow-service token");
            cached=response.data();
        }
        return "Bearer "+cached.token();
    }
}
