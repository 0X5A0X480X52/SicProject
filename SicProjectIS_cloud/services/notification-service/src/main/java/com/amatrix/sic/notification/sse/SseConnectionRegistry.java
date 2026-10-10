package com.amatrix.sic.notification.sse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class SseConnectionRegistry{
    private final ConcurrentHashMap<Long,CopyOnWriteArraySet<SseEmitter>> connections=new ConcurrentHashMap<>();
    public SseConnectionRegistry(MeterRegistry registry){Gauge.builder("sic.sse.connections",this,SseConnectionRegistry::size).description("Currently open SSE connections on this instance").register(registry);}
    public SseEmitter subscribe(Long userId){SseEmitter emitter=new SseEmitter(30*60*1000L);connections.computeIfAbsent(userId,id->new CopyOnWriteArraySet<>()).add(emitter);Runnable cleanup=()->remove(userId,emitter);emitter.onCompletion(cleanup);emitter.onTimeout(cleanup);emitter.onError(error->cleanup.run());try{emitter.send(SseEmitter.event().name("CONNECTED").data(java.util.Map.of("connected",true)));}catch(IOException ex){cleanup.run();}return emitter;}
    public int broadcast(String eventName,Object payload){int delivered=0;for(var entry:connections.entrySet())for(SseEmitter emitter:entry.getValue())if(send(entry.getKey(),emitter,eventName,payload))delivered++;return delivered;}
    public int sendTo(Long userId,String eventName,Object payload){int delivered=0;for(SseEmitter emitter:connections.getOrDefault(userId,new CopyOnWriteArraySet<>()))if(send(userId,emitter,eventName,payload))delivered++;return delivered;}
    @Scheduled(fixedDelay=15000) public void heartbeat(){broadcast("HEARTBEAT",java.util.Map.of("timestamp",java.time.Instant.now().toString()));}
    public int size(){return connections.values().stream().mapToInt(Set::size).sum();}
    private boolean send(Long userId,SseEmitter emitter,String name,Object payload){try{emitter.send(SseEmitter.event().name(name).data(payload));return true;}catch(Exception ex){remove(userId,emitter);return false;}}
    private void remove(Long userId,SseEmitter emitter){var set=connections.get(userId);if(set==null)return;set.remove(emitter);if(set.isEmpty())connections.remove(userId,set);}
}
