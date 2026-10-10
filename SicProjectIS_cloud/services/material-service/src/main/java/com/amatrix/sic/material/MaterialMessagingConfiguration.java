package com.amatrix.sic.material;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaterialMessagingConfiguration{
    public static final String QUEUE="material.reservation.confirm.q";
    public static final String DOCUMENT_QUEUE="material.document-generation.q";
    @Bean Queue materialReservationConfirmQueue(){return new Queue(QUEUE,true,false,false,java.util.Map.of("x-dead-letter-exchange","sic.events.dlx","x-dead-letter-routing-key","material.reservation.confirm.dead"));}
    @Bean Binding materialReservationConfirmBinding(Queue materialReservationConfirmQueue,TopicExchange sicEventExchange){return BindingBuilder.bind(materialReservationConfirmQueue).to(sicEventExchange).with("material.reservation.confirm");}
    @Bean Queue materialDocumentGenerationQueue(){return new Queue(DOCUMENT_QUEUE,true,false,false,java.util.Map.of("x-dead-letter-exchange","sic.events.dlx","x-dead-letter-routing-key","document.generation.dead"));}
    @Bean Binding materialDocumentGenerationBinding(Queue materialDocumentGenerationQueue,TopicExchange sicEventExchange){return BindingBuilder.bind(materialDocumentGenerationQueue).to(sicEventExchange).with("document.generation.requested");}
}
