package com.amatrix.sic.material.messaging.consumer;

import com.amatrix.sic.material.service.reservation.MaterialReservationService;
import com.amatrix.sic.material.config.MaterialMessagingConfiguration;

import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 去重消费材料确认命令，读取 reservationId 并确认预留。
 */
@Component
public class MaterialReservationEventConsumer{
    private final ObjectMapper mapper;private final ConsumedEventGuard guard;private final MaterialReservationService service;
    public MaterialReservationEventConsumer(ObjectMapper mapper,ConsumedEventGuard guard,MaterialReservationService service){this.mapper=mapper;this.guard=guard;this.service=service;}
    @RabbitListener(queues=MaterialMessagingConfiguration.QUEUE)
    @Transactional
    public void consume(String json)throws Exception{EventEnvelope<Map<String,Object>> event=mapper.readValue(json,new TypeReference<>(){});if(!guard.begin(event.eventId(),"material-reservation-confirm"))return;Object id=event.payload().get("reservationId");if(id==null)throw new IllegalArgumentException("reservationId is missing");service.confirm(String.valueOf(id));}
}
