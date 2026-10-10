package com.amatrix.sic.material;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.MaterialReservationCommand;
import com.amatrix.sic.contracts.MaterialReservationResult;

@Service
public class MaterialReservationService{
    private final JdbcTemplate jdbc;public MaterialReservationService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public MaterialReservationResult reserve(MaterialReservationCommand command){
        if(command==null||command.commandId()==null||command.commandId().isBlank()||command.actorUserId()==null
                ||command.projectId()==null||command.moduleInstanceId()==null||command.expectedSeq()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reservation command fields are required");
        MaterialReservationResult existing=byCommand(command.commandId());if(existing!=null)return existing;
        List<Long> ids=command.materialVersionIds()==null?List.of():new ArrayList<>(new LinkedHashSet<>(command.materialVersionIds()));
        if(ids.isEmpty()&&!empty(command.requiredMaterialTypeCodes()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Required materials are missing");
        List<Item> items=ids.isEmpty()?List.of():loadItems(ids,true);
        if(items.size()!=ids.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"One or more material versions are unavailable");
        if(items.stream().anyMatch(item->!item.projectId().equals(command.projectId())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Material does not belong to the project");
        for(String required:command.requiredMaterialTypeCodes()==null?List.<String>of():command.requiredMaterialTypeCodes())if(items.stream().noneMatch(item->required.equals(item.typeCode())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Required material is missing: "+required);
        String id=UUID.randomUUID().toString();Instant expires=Instant.now().plus(15,ChronoUnit.MINUTES);
        jdbc.update("""
                INSERT INTO material_reservation(reservation_id,command_id,project_id,module_instance_id,expected_seq,actor_user_id,status,expires_at)
                VALUES (?,?,?,?,?,?,'RESERVED',?)
                """,id,command.commandId(),command.projectId(),command.moduleInstanceId(),command.expectedSeq(),command.actorUserId(),expires);
        for(Item item:items)jdbc.update("INSERT INTO material_reservation_item(reservation_id,material_version_id,material_type_code) VALUES (?,?,?)",id,item.versionId(),item.typeCode());
        Map<Long,String> types=new LinkedHashMap<>();for(Item item:items)types.put(item.versionId(),item.typeCode());
        return new MaterialReservationResult(id,"RESERVED",expires,ids,types);
    }

    @Transactional
    public void confirm(String reservationId){
        Integer deleted=jdbc.queryForObject("""
                SELECT COUNT(*) FROM material_reservation_item i
                  JOIN material_version v ON v.id=i.material_version_id
                 WHERE i.reservation_id=? AND v.file_status='DELETED'
                """,Integer.class,reservationId);
        if(deleted!=null&&deleted>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Reserved material is no longer available");
        int updated=jdbc.update("UPDATE material_reservation SET status='CONFIRMED' WHERE reservation_id=? AND status IN ('RESERVED','EXPIRED')",reservationId);
        if(updated==0){String status=status(reservationId);if(!"CONFIRMED".equals(status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Material reservation is released or unavailable");}
    }

    @Transactional
    public void release(String reservationId){jdbc.update("UPDATE material_reservation SET status='RELEASED' WHERE reservation_id=? AND status='RESERVED'",reservationId);}

    @Scheduled(fixedDelayString="${sic.material.reservation-cleanup-ms:60000}")
    public void cleanup(){jdbc.update("UPDATE material_reservation SET status='EXPIRED' WHERE status='RESERVED' AND expires_at<=CURRENT_TIMESTAMP");}

    private MaterialReservationResult byCommand(String commandId){List<MaterialReservationResult> rows=jdbc.query("SELECT reservation_id,status,expires_at FROM material_reservation WHERE command_id=?",(rs,row)->{String id=rs.getString(1);List<Item> items=jdbc.query("SELECT material_version_id,material_type_code FROM material_reservation_item WHERE reservation_id=? ORDER BY material_version_id",(ir,n)->new Item(ir.getLong(1),null,ir.getString(2)),id);Map<Long,String> types=new LinkedHashMap<>();items.forEach(item->types.put(item.versionId(),item.typeCode()));return new MaterialReservationResult(id,rs.getString(2),rs.getTimestamp(3).toInstant(),items.stream().map(Item::versionId).toList(),types);},commandId);return rows.isEmpty()?null:rows.getFirst();}
    private List<Item> loadItems(List<Long>ids,boolean lock){String placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));return jdbc.query("SELECT v.id,m.project_id,m.material_type_code FROM material_version v JOIN material m ON m.id=v.material_id WHERE v.id IN ("+placeholders+") AND v.file_status<>'DELETED'"+(lock?" FOR UPDATE":""),(rs,row)->new Item(rs.getLong(1),rs.getLong(2),rs.getString(3)),ids.toArray());}
    private String status(String id){return jdbc.query("SELECT status FROM material_reservation WHERE reservation_id=?",(rs,row)->rs.getString(1),id).stream().findFirst().orElse(null);}
    private static boolean empty(List<?>v){return v==null||v.isEmpty();}
    private record Item(Long versionId,Long projectId,String typeCode){}
}
