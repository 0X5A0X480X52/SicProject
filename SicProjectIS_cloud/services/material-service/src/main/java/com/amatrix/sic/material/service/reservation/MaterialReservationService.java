package com.amatrix.sic.material.service.reservation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.material.dao.MaterialReservationMapper;
import com.amatrix.sic.material.entity.ReservationRow;
import com.amatrix.sic.material.entity.ReservationItemRow;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.MaterialReservationCommand;
import com.amatrix.sic.contracts.MaterialReservationResult;

@Service
public class MaterialReservationService{
    private final MaterialReservationMapper mapper;public MaterialReservationService(MaterialReservationMapper mapper){this.mapper=mapper;}

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MaterialReservationResult reserve(MaterialReservationCommand command){
        if(command==null||command.commandId()==null||command.commandId().isBlank()||command.actorUserId()==null
                ||command.projectId()==null||command.moduleInstanceId()==null||command.expectedSeq()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reservation command fields are required");
        MaterialReservationResult existing=byCommand(command.commandId());if(existing!=null)return existing;
        List<Long> ids=command.materialVersionIds()==null?List.of():new ArrayList<>(new LinkedHashSet<>(command.materialVersionIds()));
        if(ids.stream().anyMatch(java.util.Objects::isNull))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Material version ID is required");
        if(!ids.isEmpty())ids.sort(Long::compareTo);
        if(ids.isEmpty()&&!empty(command.requiredMaterialTypeCodes()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Required materials are missing");
        List<ReservationItemRow> items=ids.isEmpty()?List.of():loadItems(ids,true);
        existing=byCommand(command.commandId());if(existing!=null)return existing;
        if(items.size()!=ids.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"One or more material versions are unavailable");
        if(items.stream().anyMatch(item->!item.projectId().equals(command.projectId())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Material does not belong to the project");
        for(String required:command.requiredMaterialTypeCodes()==null?List.<String>of():command.requiredMaterialTypeCodes())if(items.stream().noneMatch(item->required.equals(item.typeCode())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Required material is missing: "+required);
        String id=UUID.randomUUID().toString();Instant expires=Instant.now().plus(15,ChronoUnit.MINUTES);
        try { mapper.insert(id,command,expires); }
        catch(org.springframework.dao.DuplicateKeyException conflict) {
            existing=byCommand(command.commandId());
            if(existing!=null)return existing;
            throw conflict;
        }
        for(ReservationItemRow item:items)mapper.insertItem(id,item);
        Map<Long,String> types=new LinkedHashMap<>();for(ReservationItemRow item:items)types.put(item.versionId(),item.typeCode());
        return new MaterialReservationResult(id,"RESERVED",expires,ids,types);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirm(String reservationId){
        List<ReservationItemRow> items=mapper.reservedItems(reservationId);
        List<Long> ids=items.stream().map(ReservationItemRow::versionId).toList();
        if(!ids.isEmpty()&&mapper.available(ids,true).size()!=ids.size())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Reserved material is no longer available");
        int updated=mapper.confirm(reservationId);
        if(updated==0){String status=status(reservationId);if(!"CONFIRMED".equals(status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Material reservation is released or unavailable");}
    }

    @Transactional
    public void release(String reservationId){mapper.release(reservationId);}

    @Scheduled(fixedDelayString="${sic.material.reservation-cleanup-ms:60000}")
    public void cleanup(){mapper.expire();}

    private MaterialReservationResult byCommand(String commandId){
        ReservationRow reservation=mapper.byCommand(commandId);
        if(reservation==null)return null;
        List<ReservationItemRow> items=mapper.reservedItems(reservation.id());
        Map<Long,String> types=new LinkedHashMap<>();items.forEach(item->types.put(item.versionId(),item.typeCode()));
        return new MaterialReservationResult(reservation.id(),reservation.status(),reservation.expires(),items.stream().map(ReservationItemRow::versionId).toList(),types);
    }
    private List<ReservationItemRow> loadItems(List<Long>ids,boolean lock) { return mapper.available(ids,lock); }
    private String status(String id) { return mapper.status(id); }
    private static boolean empty(List<?>v){return v==null||v.isEmpty();}
}
