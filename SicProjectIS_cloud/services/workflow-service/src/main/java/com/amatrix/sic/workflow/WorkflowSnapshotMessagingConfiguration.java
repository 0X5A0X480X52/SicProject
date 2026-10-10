package com.amatrix.sic.workflow;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowSnapshotMessagingConfiguration {
    static final String IAM_QUEUE = "workflow.iam-snapshot.q";
    static final String PROJECT_LIFECYCLE_QUEUE = "workflow.project-lifecycle.q";
    static final String EXPERT_REVIEW_QUEUE = "workflow.expert-review-completed.q";
    static final String PROJECT_AUTHORIZATION_QUEUE = "workflow.project-authorization.q";

    @Bean
    Queue workflowIamSnapshotQueue() {
        return new Queue(IAM_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", "sic.events.dlx",
                "x-dead-letter-routing-key", "workflow.iam-snapshot.dead"));
    }

    @Bean
    Binding workflowIamSnapshotBinding(Queue workflowIamSnapshotQueue, TopicExchange sicEventExchange) {
        return BindingBuilder.bind(workflowIamSnapshotQueue).to(sicEventExchange).with("iam.changed");
    }

    @Bean
    Queue workflowProjectLifecycleQueue() {
        return new Queue(PROJECT_LIFECYCLE_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", "sic.events.dlx",
                "x-dead-letter-routing-key", "workflow.project-lifecycle.dead"));
    }

    @Bean
    Binding workflowProjectLifecycleBinding(Queue workflowProjectLifecycleQueue, TopicExchange sicEventExchange) {
        return BindingBuilder.bind(workflowProjectLifecycleQueue).to(sicEventExchange)
                .with("workflow.project-lifecycle-changed");
    }

    @Bean
    Queue workflowExpertReviewQueue() {
        return new Queue(EXPERT_REVIEW_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", "sic.events.dlx",
                "x-dead-letter-routing-key", "workflow.expert-review-completed.dead"));
    }

    @Bean
    Binding workflowExpertReviewBinding(Queue workflowExpertReviewQueue, TopicExchange sicEventExchange) {
        return BindingBuilder.bind(workflowExpertReviewQueue).to(sicEventExchange)
                .with("workflow.expert-review-completed");
    }

    @Bean
    Queue workflowProjectAuthorizationQueue() {
        return new Queue(PROJECT_AUTHORIZATION_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", "sic.events.dlx",
                "x-dead-letter-routing-key", "workflow.project-authorization.dead"));
    }

    @Bean
    Binding workflowProjectAuthorizationBinding(Queue workflowProjectAuthorizationQueue,
            TopicExchange sicEventExchange) {
        return BindingBuilder.bind(workflowProjectAuthorizationQueue).to(sicEventExchange)
                .with("project.authorization");
    }
}
