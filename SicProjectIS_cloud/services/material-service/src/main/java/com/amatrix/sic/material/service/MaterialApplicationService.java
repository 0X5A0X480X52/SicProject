package com.amatrix.sic.material.service;

import com.amatrix.sic.material.policy.MaterialAccessPolicy;
import com.amatrix.sic.material.storage.MinioObjectStorage;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import com.amatrix.sic.material.dao.MaterialMapper;
import com.amatrix.sic.material.entity.MaterialTypeRow;
import com.amatrix.sic.material.entity.MaterialVersionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;

/**
 * 向 Project 校验读写权限；上传 MinIO 并事务保存版本/SHA-256/事件，失败尝试清理对象；列表/下载；禁止删除生成单据及已预留/确认版本，其他删除建立持久清理任务。
 * 对象存储写入与数据库事务分开执行；失败补偿和持久化删除任务用于处理两者之间的失败窗口。
 */
@Service
public class MaterialApplicationService{
    private final MaterialMapper mapper;private final MinioObjectStorage storage;private final MaterialAccessPolicy access;private final OutboxService outbox;private final TransactionTemplate transactions;
    public MaterialApplicationService(MaterialMapper mapper,MinioObjectStorage storage,MaterialAccessPolicy access,OutboxService outbox,TransactionTemplate transactions){this.mapper=mapper;this.storage=storage;this.access=access;this.outbox=outbox;this.transactions=new TransactionTemplate(transactions.getTransactionManager(),transactions);this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);}

    /**
     * 先校验项目写权限并上传对象，再在数据库事务中持久化版本及事件；数据库失败时尝试删除刚上传的对象。
     */
    public Map<String,Object> upload(ActorContext actor,Long projectId,String typeCode,MultipartFile file){
        requireAccess(actor,projectId,"MATERIAL_WRITE");if(file==null||file.isEmpty())throw bad("File is required");
        String filename=safeName(file.getOriginalFilename());String key="projects/"+projectId+"/"+typeCode+"/"+UUID.randomUUID()+"/"+filename;
        String sha=hash(file);try(InputStream input=file.getInputStream()){storage.put(key,input,file.getSize(),file.getContentType());}catch(Exception ex){throw new IllegalStateException(ex);}
        try{return Objects.requireNonNull(transactions.execute(status->persistUpload(actor,projectId,typeCode,file,filename,key,sha)));}catch(RuntimeException ex){try{storage.remove(key);}catch(Exception ignored){}throw ex;}
    }

    protected Map<String,Object> persistUpload(ActorContext actor,Long projectId,String typeCode,MultipartFile file,String filename,String key,String sha){
        mapper.ensureType(typeCode);
        MaterialTypeRow type=type(typeCode);
        Long materialId=mapper.material(projectId,typeCode,false);
        if(materialId==null) {
            var material=linked("project",projectId,"code",typeCode,"title",filename,"actor",actor.userId());
            mapper.insertMaterial(material);materialId=((Number)Objects.requireNonNull(material.get("id"))).longValue();
        }
        final Long mid=materialId;Integer max=mapper.maximumVersion(mid);int version=(max==null?0:max)+1;
        mapper.supersede(mid);
        var insert=linked("material",mid,"number",version,"filename",filename,"contentType",file.getContentType(),
                "size",file.getSize(),"sha256",sha,"key",key,"actor",actor.userId());
        mapper.insertVersion(insert);
        Long versionId=((Number)Objects.requireNonNull(insert.get("id"))).longValue();outbox.enqueue("material.changed",EventEnvelope.create("MATERIAL_VERSION_UPLOADED","MATERIAL",mid.toString(),null,null,actor.userId(),Map.of("projectId",projectId,"materialVersionId",versionId,"materialTypeCode",typeCode)));
        Map<String,Object> context=context(mid,versionId,type);return linked("version",versionMap(context),"context",context);
    }

    public List<Map<String,Object>> list(ActorContext actor,Long projectId) {
        requireAccess(actor,projectId,"MATERIAL_READ");return mapper.contexts(projectId,null,null).stream().map(this::present).toList();
    }

    public Download download(ActorContext actor,Long versionId){MaterialVersionRow version=version(versionId);requireAccess(actor,version.projectId(),"MATERIAL_READ");return new Download(new InputStreamResource(storage.get(version.objectKey())),version.filename(),version.contentType(),version.size());}

    public void delete(ActorContext actor,Long versionId){
        MaterialVersionRow observed=version(versionId);requireAccess(actor,observed.projectId(),"MATERIAL_DELETE");
        transactions.executeWithoutResult(status -> deleteLocked(actor,versionId));
    }

    /**
     * 锁定版本后复核引用和预留状态；业务删除与对象删除任务同事务入库，实际 MinIO 删除由独立清理任务处理。
     */
    private void deleteLocked(ActorContext actor,Long versionId){
        MaterialVersionRow version=mapper.version(versionId,true);
        if(version==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Material version not found");
        Integer generated=mapper.generated(versionId);
        if(generated!=null && generated>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Generated workflow documents cannot be deleted");
        Integer reservations=mapper.reserved(versionId);
        if(reservations!=null && reservations>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Material version is reserved by workflow");
        mapper.deleteVersion(versionId);mapper.scheduleDeletion(versionId,version.objectKey());
        outbox.enqueue("material.changed",EventEnvelope.create("MATERIAL_VERSION_DELETED","MATERIAL_VERSION",versionId.toString(),null,null,actor.userId(),Map.of("projectId",version.projectId(),"materialVersionId",versionId)));
    }

    private Map<String,Object> context(Long materialId,Long versionId,MaterialTypeRow type) {
        return present(mapper.contexts(null,materialId,versionId).getFirst());
    }
    private Map<String,Object> present(Map<String,Object> row) {
        row.put("fileUrl","/api/material-versions/"+row.get("materialVersionId")+"/download");
        row.put("uploadedByName",null);return row;
    }
    private Map<String,Object> versionMap(Map<String,Object> c){return linked("materialVersionId",c.get("materialVersionId"),"materialId",c.get("materialId"),"versionNo",c.get("versionNo"),"fileName",c.get("fileName"),"fileUrl",c.get("fileUrl"),"fileHash",c.get("fileHash"),"uploadedBy",c.get("uploadedBy"),"uploadedAt",c.get("uploadedAt"),"isCurrent",c.get("isCurrent"));}
    private MaterialTypeRow type(String code) { return mapper.type(code); }
    private MaterialVersionRow version(Long id) {
        var version=mapper.version(id,false);
        if(version==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Material version not found");
        return version;
    }
    private void requireAccess(ActorContext actor,Long projectId,String operation){if(!access.canAccess(actor,projectId,operation))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Project material access denied");}
    private static String hash(MultipartFile file){try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(DigestInputStream in=new DigestInputStream(file.getInputStream(),digest)){in.transferTo(OutputStream.nullOutputStream());}return HexFormat.of().formatHex(digest.digest());}catch(Exception ex){throw new IllegalStateException("Unable to hash material",ex);}}
    private static String safeName(String name){String v=name==null||name.isBlank()?"upload.bin":name.replace('\\','/');v=v.substring(v.lastIndexOf('/')+1);return v.replaceAll("[\\r\\n]","_");}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static Map<String,Object> linked(Object...p){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<p.length;i+=2)m.put(String.valueOf(p[i]),p[i+1]);return m;}
    public record Download(InputStreamResource resource,String filename,String contentType,long size){}
}
