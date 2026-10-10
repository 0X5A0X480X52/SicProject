package com.amatrix.sic.project.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectSnapshotMessagingConfiguration {
    public static final String IAM_QUEUE = "project.iam-snapshot.q";
    public static final String WORKFLOW_LIFECYCLE_QUEUE = "project.workflow-lifecycle.q";
    public static final String EXPERT_ACCESS_ENDED_QUEUE = "project.expert-access-ended.q";

    @Bean
    Queue projectIamSnapshotQueue() {
        return new Queue(IAM_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", "sic.events.dlx",
                "x-dead-letter-routing-key", "project.iam-snapshot.dead"));
    }

    @Bean
    Binding projectIamSnapshotBinding(Queue projectIamSnapshotQueue, TopicExchange sicEventExchange) {
        return BindingBuilder.bind(projectIamSnapshotQueue).to(sicEventExchange).with("iam.changed");
    }

    @Bean
    Queue projectWorkflowLifecycleQueue() {
        return new Queue(WORKFLOW_LIFECYCLE_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", "sic.events.dlx",
                "x-dead-letter-routing-key", "project.workflow-lifecycle.dead"));
    }

    @Bean
    Binding projectWorkflowLifecycleBinding(Queue projectWorkflowLifecycleQueue, TopicExchange sicEventExchange) {
        return BindingBuilder.bind(projectWorkflowLifecycleQueue).to(sicEventExchange)
                .with("workflow.project-lifecycle-changed");
    }

    @Bean
    Queue projectExpertAccessEndedQueue() {
        return new Queue(EXPERT_ACCESS_ENDED_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", "sic.events.dlx",
                "x-dead-letter-routing-key", "project.expert-access-ended.dead"));
    }

    @Bean
    Binding projectExpertAccessEndedBinding(Queue projectExpertAccessEndedQueue, TopicExchange sicEventExchange) {
        return BindingBuilder.bind(projectExpertAccessEndedQueue).to(sicEventExchange)
                .with("workflow.expert-access-ended");
    }
}
