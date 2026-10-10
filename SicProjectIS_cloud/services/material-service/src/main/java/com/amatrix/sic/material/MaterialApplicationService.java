package com.amatrix.sic.material;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;

@Service
public class MaterialApplicationService{
    private final JdbcTemplate jdbc;private final MinioObjectStorage storage;private final MaterialAccessPolicy access;private final OutboxService outbox;private final TransactionTemplate transactions;
    public MaterialApplicationService(JdbcTemplate jdbc,MinioObjectStorage storage,MaterialAccessPolicy access,OutboxService outbox,TransactionTemplate transactions){this.jdbc=jdbc;this.storage=storage;this.access=access;this.outbox=outbox;this.transactions=transactions;}

    public Map<String,Object> upload(ActorContext actor,Long projectId,String typeCode,MultipartFile file){
        requireAccess(actor,projectId,"MATERIAL_WRITE");if(file==null||file.isEmpty())throw bad("File is required");
        String filename=safeName(file.getOriginalFilename());String key="projects/"+projectId+"/"+typeCode+"/"+UUID.randomUUID()+"/"+filename;
        String sha=hash(file);try(InputStream input=file.getInputStream()){storage.put(key,input,file.getSize(),file.getContentType());}catch(Exception ex){throw new IllegalStateException(ex);}
        try{return Objects.requireNonNull(transactions.execute(status->persistUpload(actor,projectId,typeCode,file,filename,key,sha)));}catch(RuntimeException ex){try{storage.remove(key);}catch(Exception ignored){}throw ex;}
    }

    protected Map<String,Object> persistUpload(ActorContext actor,Long projectId,String typeCode,MultipartFile file,String filename,String key,String sha){
        jdbc.update("INSERT INTO material_type(code,name,enabled) VALUES (?,?,TRUE) ON DUPLICATE KEY UPDATE code=VALUES(code)",typeCode,typeCode);
        TypeRecord type=type(typeCode);
        Long materialId=jdbc.query("SELECT id FROM material WHERE project_id=? AND material_type_code=? ORDER BY id LIMIT 1",(rs,row)->rs.getLong(1),projectId,typeCode).stream().findFirst().orElse(null);
        if(materialId==null){GeneratedKeyHolder holder=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO material(project_id,material_type_code,title,created_by) VALUES (?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,projectId);ps.setString(2,typeCode);ps.setString(3,filename);ps.setLong(4,actor.userId());return ps;},holder);materialId=Objects.requireNonNull(holder.getKey()).longValue();}
        final Long mid=materialId;Integer max=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM material_version WHERE material_id=? FOR UPDATE",Integer.class,mid);int version=(max==null?0:max)+1;
        jdbc.update("UPDATE material_version SET file_status='SUPERSEDED' WHERE material_id=? AND file_status='ACTIVE'",mid);
        GeneratedKeyHolder holder=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement ps=c.prepareStatement("""
                INSERT INTO material_version(material_id,version_no,original_filename,content_type,size_bytes,sha256,object_key,file_status,uploaded_by)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',?)
                """,Statement.RETURN_GENERATED_KEYS);ps.setLong(1,mid);ps.setInt(2,version);ps.setString(3,filename);ps.setString(4,file.getContentType());ps.setLong(5,file.getSize());ps.setString(6,sha);ps.setString(7,key);ps.setLong(8,actor.userId());return ps;},holder);
        Long versionId=Objects.requireNonNull(holder.getKey()).longValue();outbox.enqueue("material.changed",EventEnvelope.create("MATERIAL_VERSION_UPLOADED","MATERIAL",mid.toString(),null,null,actor.userId(),Map.of("projectId",projectId,"materialVersionId",versionId,"materialTypeCode",typeCode)));
        Map<String,Object> context=context(mid,versionId,type);return linked("version",versionMap(context),"context",context);
    }

    public List<Map<String,Object>> list(ActorContext actor,Long projectId){requireAccess(actor,projectId,"MATERIAL_READ");return jdbc.query("""
            SELECT m.id material_id,m.project_id,t.id type_id,t.code,t.name,t.module_type,t.allowed_file_types,t.max_file_size_mb,
              v.id version_id,v.version_no,v.original_filename,v.sha256,v.uploaded_by,v.created_at,v.file_status
              FROM material m JOIN material_type t ON t.code=m.material_type_code JOIN material_version v ON v.material_id=m.id
             WHERE m.project_id=? AND v.file_status<>'DELETED' ORDER BY m.id,v.version_no DESC
            """,(rs,row)->contextRow(rs),projectId);}

    public Download download(ActorContext actor,Long versionId){VersionRecord version=version(versionId);requireAccess(actor,version.projectId(),"MATERIAL_READ");return new Download(new InputStreamResource(storage.get(version.objectKey())),version.filename(),version.contentType(),version.size());}

    @Transactional
    public void delete(ActorContext actor,Long versionId){VersionRecord version=version(versionId);requireAccess(actor,version.projectId(),"MATERIAL_DELETE");Integer generated=jdbc.queryForObject("SELECT COUNT(*) FROM generated_document WHERE material_version_id=?",Integer.class,versionId);if(generated!=null&&generated>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Generated workflow documents cannot be deleted");Integer reservations=jdbc.queryForObject("""
            SELECT COUNT(*) FROM material_reservation_item i JOIN material_reservation r ON r.reservation_id=i.reservation_id
             WHERE i.material_version_id=? AND r.status IN ('RESERVED','CONFIRMED')
            """,Integer.class,versionId);if(reservations!=null&&reservations>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Material version is reserved by workflow");
        jdbc.update("UPDATE material_version SET file_status='DELETED' WHERE id=?",versionId);
        jdbc.update("INSERT INTO object_deletion_job(material_version_id,object_key,status) VALUES (?,?,'PENDING')",
                versionId,version.objectKey());
        outbox.enqueue("material.changed",EventEnvelope.create("MATERIAL_VERSION_DELETED","MATERIAL_VERSION",versionId.toString(),null,null,actor.userId(),Map.of("projectId",version.projectId(),"materialVersionId",versionId)));
    }

    private Map<String,Object> context(Long materialId,Long versionId,TypeRecord type){return jdbc.query("""
            SELECT m.id material_id,m.project_id,v.id version_id,v.version_no,v.original_filename,v.sha256,v.uploaded_by,v.created_at,v.file_status
              FROM material m JOIN material_version v ON v.material_id=m.id WHERE m.id=? AND v.id=?
            """,(rs,row)->linked("materialId",materialId,"projectId",rs.getLong("project_id"),"materialTypeId",type.id(),"materialTypeCode",type.code(),"materialTypeName",type.name(),"moduleType",type.moduleType(),"allowedFileTypes",type.allowed(),"maxFileSizeMb",type.maxMb(),"materialVersionId",versionId,"versionNo",rs.getInt("version_no"),"fileName",rs.getString("original_filename"),"fileUrl","/api/material-versions/"+versionId+"/download","fileHash",rs.getString("sha256"),"uploadedBy",rs.getLong("uploaded_by"),"uploadedByName",null,"uploadedAt",rs.getObject("created_at",LocalDateTime.class),"isCurrent","ACTIVE".equals(rs.getString("file_status"))),materialId,versionId).getFirst();}
    private Map<String,Object> contextRow(java.sql.ResultSet rs)throws java.sql.SQLException{return linked("materialId",rs.getLong("material_id"),"projectId",rs.getLong("project_id"),"materialTypeId",rs.getLong("type_id"),"materialTypeCode",rs.getString("code"),"materialTypeName",rs.getString("name"),"moduleType",rs.getString("module_type"),"allowedFileTypes",rs.getString("allowed_file_types"),"maxFileSizeMb",rs.getInt("max_file_size_mb"),"materialVersionId",rs.getLong("version_id"),"versionNo",rs.getInt("version_no"),"fileName",rs.getString("original_filename"),"fileUrl","/api/material-versions/"+rs.getLong("version_id")+"/download","fileHash",rs.getString("sha256"),"uploadedBy",rs.getLong("uploaded_by"),"uploadedByName",null,"uploadedAt",rs.getObject("created_at",LocalDateTime.class),"isCurrent","ACTIVE".equals(rs.getString("file_status")));}
    private Map<String,Object> versionMap(Map<String,Object> c){return linked("materialVersionId",c.get("materialVersionId"),"materialId",c.get("materialId"),"versionNo",c.get("versionNo"),"fileName",c.get("fileName"),"fileUrl",c.get("fileUrl"),"fileHash",c.get("fileHash"),"uploadedBy",c.get("uploadedBy"),"uploadedAt",c.get("uploadedAt"),"isCurrent",c.get("isCurrent"));}
    private TypeRecord type(String code){return jdbc.query("SELECT id,code,name,module_type,allowed_file_types,max_file_size_mb FROM material_type WHERE code=?",(rs,row)->new TypeRecord(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6)),code).getFirst();}
    private VersionRecord version(Long id){List<VersionRecord> rows=jdbc.query("""
            SELECT v.object_key,v.original_filename,v.content_type,v.size_bytes,m.project_id FROM material_version v JOIN material m ON m.id=v.material_id WHERE v.id=? AND v.file_status<>'DELETED'
            """,(rs,row)->new VersionRecord(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5)),id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Material version not found");return rows.getFirst();}
    private void requireAccess(ActorContext actor,Long projectId,String operation){if(!access.canAccess(actor,projectId,operation))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Project material access denied");}
    private static String hash(MultipartFile file){try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(DigestInputStream in=new DigestInputStream(file.getInputStream(),digest)){in.transferTo(OutputStream.nullOutputStream());}return HexFormat.of().formatHex(digest.digest());}catch(Exception ex){throw new IllegalStateException("Unable to hash material",ex);}}
    private static String safeName(String name){String v=name==null||name.isBlank()?"upload.bin":name.replace('\\','/');v=v.substring(v.lastIndexOf('/')+1);return v.replaceAll("[\\r\\n]","_");}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static Map<String,Object> linked(Object...p){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<p.length;i+=2)m.put(String.valueOf(p[i]),p[i+1]);return m;}
    public record Download(InputStreamResource resource,String filename,String contentType,long size){}
    private record TypeRecord(Long id,String code,String name,String moduleType,String allowed,int maxMb){}
    private record VersionRecord(String objectKey,String filename,String contentType,long size,Long projectId){}
}
