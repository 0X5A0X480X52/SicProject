package com.amatrix.sic.workflow;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.security.CanOperateModule;
import com.amatrix.sic.security.CanAccessModule;
import com.amatrix.sic.security.CanReadNodeForm;
import com.amatrix.sic.contracts.ActorContext;

import tools.jackson.databind.ObjectMapper;

@Service
public class NodeFormService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ExpertReviewViewRepository expertReviewViews;
    private final WorkflowOperationPolicy operationPolicy;
    @Autowired public NodeFormService(JdbcTemplate jdbc,ObjectMapper objectMapper,ExpertReviewViewRepository expertReviewViews,WorkflowOperationPolicy operationPolicy){this.jdbc=jdbc;this.objectMapper=objectMapper;this.expertReviewViews=expertReviewViews;this.operationPolicy=operationPolicy;}
    NodeFormService(JdbcTemplate jdbc,ObjectMapper objectMapper,ExpertReviewViewRepository expertReviewViews){this(jdbc,objectMapper,expertReviewViews,null);}

    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> definitions(){return List.of(
            definition("APPLICATION_NOTICE_FORM","APPLICATION","PublishNoticeTask","APPLICATION_NOTICE_PUBLISHING","Application notice","NOTICE","HISTORY_RECORD"),
            definition("PROJECT_APPLICATION_FORM","APPLICATION","SubmitApplicationTask","APPLICATION_DRAFT","Project application","APPLICATION_DRAFT","SINGLE_INSTANCE"),
            definition("DEPT_APPLICATION_REVIEW_FORM","APPLICATION","DeptReviewTask","APPLICATION_DEPT_REVIEWING","Department review","CHECK_ITEM","HISTORY_RECORD"),
            definition("DEPT_EXPERT_ASSIGN_FORM","APPLICATION","DeptExpertAssignTask","APPLICATION_DEPT_EXPERT_ASSIGNING","Department expert assignment","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("DEPT_EXPERT_REVIEW_FORM","APPLICATION","DeptExpertReviewTask","APPLICATION_DEPT_EXPERT_REVIEWING","Department expert review","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("DEPT_EXPERT_SUMMARY_FORM","APPLICATION","DeptExpertSummaryTask","APPLICATION_DEPT_EXPERT_SUMMARIZING","Department expert summary","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("SCIENCE_INITIAL_REVIEW_FORM","APPLICATION","ScienceOfficeInitialReviewTask","APPLICATION_SCIENCE_INITIAL_REVIEWING","Science office initial review","CHECK_ITEM","HISTORY_RECORD"),
            definition("SCIENCE_EXPERT_ASSIGN_FORM","APPLICATION","ScienceExpertAssignTask","APPLICATION_SCIENCE_EXPERT_ASSIGNING","Science office expert assignment","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("SCIENCE_EXPERT_REVIEW_FORM","APPLICATION","ScienceExpertReviewTask","APPLICATION_SCIENCE_EXPERT_REVIEWING","Science office expert review","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("SCIENCE_EXPERT_SUMMARY_FORM","APPLICATION","ScienceExpertSummaryTask","APPLICATION_SCIENCE_EXPERT_SUMMARIZING","Science office expert summary","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("APPLICATION_PUBLICITY_FORM","APPLICATION","ScienceOfficePublicityTask","APPLICATION_PUBLICITY","Application publicity","PUBLICITY","HISTORY_RECORD"),
            definition("APPLICATION_SUBMISSION_REVIEW_FORM","APPLICATION","ScienceOfficeSubmitTask","APPLICATION_SCIENCE_SUBMITTING","Application submission review","CHECK_ITEM","HISTORY_RECORD"),
            definition("APPLICATION_AUTHORITY_RESULT_FORM","APPLICATION","AuthorityReviewTask","APPLICATION_AUTHORITY_REVIEWING","Authority result","EXTERNAL_RESULT","HISTORY_RECORD"),
            definition("APPLICATION_SEAL_FORM","APPLICATION","SignAndSealTask","APPLICATION_SIGN_SEALING","Application signature and seal","SEAL","HISTORY_RECORD"),
            definition("APPLICATION_FINAL_SUBMISSION_FORM","APPLICATION","SubmitFinalMaterialsTask","APPLICATION_FINAL_MATERIAL_SUBMITTING","Application final submission","SUBMISSION","HISTORY_RECORD"),
            definition("PROJECT_APPROVAL_FORM","CONTRACT","AuthorityApproveProjectTask","CONTRACT_PROJECT_APPROVED","Project approval","EXTERNAL_RESULT","HISTORY_RECORD"),
            definition("CONTRACT_DRAFT_FORM","CONTRACT","FillContractTask","CONTRACT_DRAFT","Contract draft","CONTRACT_DRAFT","SINGLE_INSTANCE"),
            definition("CONTRACT_DEPT_REVIEW_FORM","CONTRACT","DeptReviewTask","CONTRACT_DEPT_REVIEWING","Contract department review","CHECK_ITEM","HISTORY_RECORD"),
            definition("CONTRACT_SCIENCE_REVIEW_FORM","CONTRACT","ScienceOfficeReviewTask","CONTRACT_SCIENCE_REVIEWING","Contract science office review","CHECK_ITEM","HISTORY_RECORD"),
            definition("CONTRACT_AUTHORITY_REVIEW_FORM","CONTRACT","AuthorityReviewTask","CONTRACT_AUTHORITY_REVIEWING","Contract authority review","EXTERNAL_RESULT","HISTORY_RECORD"),
            definition("CONTRACT_PDF_PRINT_FORM","CONTRACT","PrintPdfContractTask","CONTRACT_PDF_PRINTING","Printable contract","DOCUMENT","READ_ONLY"),
            definition("CONTRACT_LEADER_SIGN_FORM","CONTRACT","LeaderSignTask","CONTRACT_LEADER_SIGNING","Contract leader signature","SEAL","HISTORY_RECORD"),
            definition("CONTRACT_SCHOOL_SEAL_FORM","CONTRACT","SchoolSealTask","CONTRACT_SCHOOL_SEALING","Contract school seal","SEAL","HISTORY_RECORD"),
            definition("CONTRACT_AUTHORITY_SEAL_FORM","CONTRACT","AuthoritySealTask","CONTRACT_AUTHORITY_SEALING","Contract authority seal","EXTERNAL_RESULT","HISTORY_RECORD"),
            definition("CONTRACT_ARCHIVE_FORM","CONTRACT","ArchiveContractTask","CONTRACT_ARCHIVING","Contract archive","ARCHIVE","HISTORY_RECORD"),
            definition("ACCEPTANCE_NOTICE_FORM","ACCEPTANCE","PublishAcceptanceNoticeTask","ACCEPTANCE_NOTICE_PUBLISHING","Acceptance notice","NOTICE","HISTORY_RECORD"),
            definition("ACCEPTANCE_NOTIFY_LEADER_FORM","ACCEPTANCE","DeptNotifyLeaderTask","ACCEPTANCE_DEPT_NOTIFYING","Notify project leader","CHECK_ITEM","HISTORY_RECORD"),
            definition("FINANCIAL_SETTLEMENT_FORM","ACCEPTANCE","FinancialSettlementTask","ACCEPTANCE_FINANCIAL_SETTLEMENT","Financial settlement","FINANCIAL_SETTLEMENT","HISTORY_RECORD"),
            definition("ACCEPTANCE_APPLICATION_FORM","ACCEPTANCE","SubmitAcceptanceMaterialsTask","ACCEPTANCE_MATERIAL_DRAFT","Acceptance application","ACCEPTANCE_DRAFT","SINGLE_INSTANCE"),
            definition("ACCEPTANCE_REPORT_FORM","ACCEPTANCE","SubmitAcceptanceMaterialsTask","ACCEPTANCE_MATERIAL_DRAFT","Acceptance report","ACCEPTANCE_DRAFT","SINGLE_INSTANCE"),
            definition("ACHIEVEMENT_LIST_FORM","ACCEPTANCE","SubmitAcceptanceMaterialsTask","ACCEPTANCE_MATERIAL_DRAFT","Achievement list","ACHIEVEMENT","HISTORY_RECORD"),
            definition("ACCEPTANCE_DEPT_REVIEW_FORM","ACCEPTANCE","DeptReviewTask","ACCEPTANCE_DEPT_REVIEWING","Acceptance department review","CHECK_ITEM","HISTORY_RECORD"),
            definition("ACCEPTANCE_SCIENCE_REVIEW_FORM","ACCEPTANCE","ScienceOfficeReviewTask","ACCEPTANCE_SCIENCE_REVIEWING","Acceptance science office review","CHECK_ITEM","HISTORY_RECORD"),
            definition("ACCEPTANCE_AUTHORITY_REVIEW_FORM","ACCEPTANCE","AuthorityReviewTask","ACCEPTANCE_AUTHORITY_REVIEWING","Acceptance authority review","EXTERNAL_RESULT","HISTORY_RECORD"),
            definition("ACCEPTANCE_SEAL_FORM","ACCEPTANCE","SignSealAcceptanceTask","ACCEPTANCE_SIGN_SEALING","Acceptance signature and seal","SEAL","HISTORY_RECORD"),
            definition("ACCEPTANCE_FINAL_SUBMISSION_FORM","ACCEPTANCE","SubmitFinalMaterialsTask","ACCEPTANCE_FINAL_MATERIAL_SUBMITTING","Acceptance final submission","SUBMISSION","HISTORY_RECORD"),
            definition("EXPERT_ACCEPTANCE_ASSIGN_FORM","ACCEPTANCE","ExpertAssignTask","ACCEPTANCE_EXPERT_ASSIGNING","Acceptance expert assignment","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("EXPERT_ACCEPTANCE_REVIEW_FORM","ACCEPTANCE","ExpertReviewTask","ACCEPTANCE_EXPERT_REVIEWING","Acceptance expert review","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("EXPERT_ACCEPTANCE_REVIEW_SUMMARY_FORM","ACCEPTANCE","ExpertSummaryTask","ACCEPTANCE_EXPERT_SUMMARIZING","Acceptance expert summary","EXPERT_REVIEW","HISTORY_RECORD"),
            definition("ACCEPTANCE_CERTIFICATE_DOCUMENT","ACCEPTANCE","IssueCertificateTask","ACCEPTANCE_CERTIFICATE_ISSUING","Acceptance certificate","DOCUMENT","READ_ONLY"),
            definition("ACCEPTANCE_SUMMARY_FORM","ACCEPTANCE","IssueCertificateTask","ACCEPTANCE_CERTIFICATE_ISSUING","Acceptance summary","DOCUMENT","READ_ONLY"),
            definition("ACCEPTANCE_FAIL_DOCUMENT","ACCEPTANCE","IssueFailFileTask","ACCEPTANCE_FAIL_FILE_ISSUING","Acceptance failure document","DOCUMENT","READ_ONLY"),
            definition("SURPLUS_FUNDS_RETURN_FORM","ACCEPTANCE","ReturnSurplusFundsTask","ACCEPTANCE_SURPLUS_FUNDS_RETURNING","Surplus funds return","SURPLUS_RETURN","HISTORY_RECORD"));}

    public List<Map<String,Object>> forNode(String moduleType,String nodeId,String stateCode){return definitions().stream()
            .filter(item->Objects.equals(moduleType,item.get("moduleType")))
            .filter(item->Objects.equals(nodeId,item.get("nodeId"))||Objects.equals(stateCode,item.get("stateCode")))
            .toList();}

    @CanReadNodeForm
    public Map<String,Object> get(String formCode,Long projectId,Long moduleInstanceId,Long stateRecordId){
        Map<String,Object> definition=requireDefinition(formCode);
        Object data="HISTORY_RECORD".equals(definition.get("writeMode"))?records(moduleInstanceId,formCode):stored(moduleInstanceId,formCode);
        return response(definition,projectId,moduleInstanceId,stateRecordId,data);
    }

    @Transactional
    @CanOperateModule
    public Map<String,Object> save(Long moduleInstanceId,String formCode,Map<String,Object> request,ActorContext actor){
        Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"SINGLE_INSTANCE");
        ModuleContext module=requireCurrentDefinition(moduleInstanceId,definition,request);requireOperation(actor,moduleInstanceId);
        Object payload=payload(definition,request);
        jdbc.update("""
                INSERT INTO node_form(module_instance_id,node_id,round_no,form_json,submitted_by) VALUES (?,?,?,?,?)
                ON DUPLICATE KEY UPDATE form_json=VALUES(form_json),submitted_by=VALUES(submitted_by)
                """,moduleInstanceId,formCode,module.round(),write(payload),actor.userId());
        return response(definition,module.projectId(),moduleInstanceId,number(request.get("stateRecordId")),payload);
    }

    @Transactional
    @CanOperateModule
    public Map<String,Object> createRecord(Long moduleInstanceId,String formCode,Map<String,Object> request,ActorContext actor){
        Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"HISTORY_RECORD");
        ModuleContext module=requireCurrentDefinition(moduleInstanceId,definition,request);requireOperation(actor,moduleInstanceId);
        Object payload=payload(definition,request);
        GeneratedKeyHolder holder=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO node_form(module_instance_id,node_id,round_no,form_json,submitted_by) VALUES (?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,moduleInstanceId);ps.setString(2,formCode+"-"+java.util.UUID.randomUUID());ps.setInt(3,module.round());ps.setString(4,write(payload));ps.setLong(5,actor.userId());return ps;},holder);
        Long id=Objects.requireNonNull(holder.getKey()).longValue();return linked("definition",definition,"recordId",id,"data",response(definition,module.projectId(),moduleInstanceId,number(request.get("stateRecordId")),records(moduleInstanceId,formCode)));
    }

    @Transactional
    @CanOperateModule
    public Map<String,Object> updateRecord(Long moduleInstanceId,String formCode,Long recordId,Map<String,Object> request,ActorContext actor){
        Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"HISTORY_RECORD");
        ModuleContext module=requireCurrentDefinition(moduleInstanceId,definition,request);requireOperation(actor,moduleInstanceId);Object payload=payload(definition,request);
        int updated=jdbc.update("UPDATE node_form SET form_json=?,submitted_by=? WHERE id=? AND module_instance_id=? AND node_id LIKE ? AND round_no=?",write(payload),actor.userId(),recordId,moduleInstanceId,formCode+"-%",module.round());
        if(updated==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Node form record not found");
        return linked("definition",definition,"recordId",recordId,"data",response(definition,module.projectId(),moduleInstanceId,number(request.get("stateRecordId")),records(moduleInstanceId,formCode)));
    }

    @Transactional
    @CanOperateModule
    public void deleteRecord(Long moduleInstanceId,String formCode,Long recordId,ActorContext actor){Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"HISTORY_RECORD");ModuleContext module=requireCurrentDefinition(moduleInstanceId,definition,null);requireOperation(actor,moduleInstanceId);jdbc.update("DELETE FROM node_form WHERE id=? AND module_instance_id=? AND node_id LIKE ? AND round_no=?",recordId,moduleInstanceId,formCode+"-%",module.round());}

    @CanAccessModule
    public Object businessData(Long moduleInstanceId){
        List<String> data=jdbc.query("SELECT payload_json FROM project_application WHERE module_instance_id=?",(rs,row)->rs.getString(1),moduleInstanceId);
        if(data.isEmpty())data=jdbc.query("SELECT payload_json FROM contract_form WHERE module_instance_id=? ORDER BY round_no DESC LIMIT 1",(rs,row)->rs.getString(1),moduleInstanceId);
        if(data.isEmpty())data=jdbc.query("SELECT payload_json FROM acceptance_form WHERE module_instance_id=? ORDER BY round_no DESC LIMIT 1",(rs,row)->rs.getString(1),moduleInstanceId);
        Object stored=data.isEmpty()?Map.of():read(data.getFirst());
        Map<String,Object> result=new LinkedHashMap<>();
        if(stored instanceof Map<?,?> values)values.forEach((key,value)->result.put(String.valueOf(key),value));
        else result.put("businessDraft",stored);
        result.put("expertReviews",expertReviewViews.moduleReviews(moduleInstanceId));
        return result;
    }

    private Map<String,Object> response(Map<String,Object> definition,Long projectId,Long moduleId,Long stateId,Object stored){
        String kind=String.valueOf(definition.get("dataKind"));
        Map<String,Object> result=linked("definition",definition,"context",linked("projectId",projectId,"moduleInstanceId",moduleId,"stateRecordId",stateId),
                "applicationDraft",null,"contractDraft",null,"acceptanceDraft",null,"notices",List.of(),"checkItems",List.of(),
                "externalResults",List.of(),"sealRecords",List.of(),"submissionRecords",List.of(),"archiveRecords",List.of(),
                "publicities",List.of(),"financialSettlements",List.of(),"achievements",List.of(),"surplusReturns",List.of(),
                "expertReview",null,"materials",List.of());
        String key=switch(kind){case"APPLICATION_DRAFT"->"applicationDraft";case"CONTRACT_DRAFT"->"contractDraft";case"ACCEPTANCE_DRAFT"->"acceptanceDraft";case"NOTICE"->"notices";case"CHECK_ITEM"->"checkItems";case"EXTERNAL_RESULT"->"externalResults";case"SEAL"->"sealRecords";case"SUBMISSION"->"submissionRecords";case"ARCHIVE"->"archiveRecords";case"PUBLICITY"->"publicities";case"FINANCIAL_SETTLEMENT"->"financialSettlements";case"ACHIEVEMENT"->"achievements";case"SURPLUS_RETURN"->"surplusReturns";case"EXPERT_REVIEW"->"expertReview";default->null;};
        if(key!=null)result.put(key,"EXPERT_REVIEW".equals(kind)&&stored instanceof List<?> values?(values.isEmpty()?null:values.getLast()):stored);return result;
    }

    private Object stored(Long moduleId,String code){if(moduleId==null)return null;List<String> rows=jdbc.query("SELECT form_json FROM node_form WHERE module_instance_id=? AND node_id=? ORDER BY id DESC LIMIT 1",(rs,row)->rs.getString(1),moduleId,code);return rows.isEmpty()?businessData(moduleId):read(rows.getFirst());}
    private List<Object> records(Long moduleId,String code){if(moduleId==null)return List.of();return jdbc.query("SELECT id,form_json FROM node_form WHERE module_instance_id=? AND node_id LIKE ? ORDER BY id",(rs,row)->{Object value=read(rs.getString("form_json"));if(value instanceof Map<?,?> source){Map<String,Object> copy=new LinkedHashMap<>();source.forEach((key,item)->copy.put(String.valueOf(key),item));copy.putIfAbsent("recordId",rs.getLong("id"));return copy;}return linked("recordId",rs.getLong("id"),"value",value);},moduleId,code+"-%");}
    private Map<String,Object> requireDefinition(String code){return definitions().stream().filter(d->Objects.equals(d.get("formCode"),code)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Node form definition not found"));}
    private static Map<String,Object> definition(String code,String type,String node,String state,String title,String kind,String mode){return linked("formCode",code,"moduleType",type,"nodeId",node,"stateCode",state,"title",title,"dataKind",kind,"writeMode",mode,"supportsFiles",true,"materialTypeCodes",List.of());}
    private static void requireMode(Map<String,Object> definition,String expected){if(!expected.equals(definition.get("writeMode")))throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,"Node form does not support this write mode");}
    private static Object payload(Map<String,Object> definition,Map<String,Object> request){if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Request body is required");String kind=String.valueOf(definition.get("dataKind"));Object value=switch(kind){case"APPLICATION_DRAFT"->request.get("applicationDraft");case"CONTRACT_DRAFT"->request.get("contractDraft");case"ACCEPTANCE_DRAFT"->request.get("acceptanceDraft");case"NOTICE"->request.get("notice");case"EXPERT_REVIEW"->request.get("expertReview");case"CHECK_ITEM"->child(request.get("runtimeRecord"),"checkItem");case"EXTERNAL_RESULT"->child(request.get("runtimeRecord"),"externalResult");case"SEAL"->child(request.get("runtimeRecord"),"sealRecord");case"SUBMISSION"->child(request.get("runtimeRecord"),"submissionRecord");case"ARCHIVE"->child(request.get("runtimeRecord"),"archiveRecord");case"PUBLICITY"->child(request.get("projectRecord"),"publicity");case"FINANCIAL_SETTLEMENT"->child(request.get("projectRecord"),"financialSettlement");case"ACHIEVEMENT"->child(request.get("projectRecord"),"achievement");case"SURPLUS_RETURN"->child(request.get("projectRecord"),"surplusReturn");default->null;};return value==null?request:value;}
    private static Object child(Object source,String key){return source instanceof Map<?,?> values?values.get(key):null;}
    private void requireModule(Long id){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM module_instance WHERE id=?",Integer.class,id);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module instance not found");}
    private ModuleContext requireCurrentDefinition(Long id,Map<String,Object> definition,Map<String,Object> request){
        List<ModuleContext> rows=jdbc.query("SELECT project_id,module_code,current_node_id,current_state,current_round,finished FROM module_instance WHERE id=? FOR UPDATE",(rs,row)->new ModuleContext(rs.getLong("project_id"),rs.getString("module_code"),rs.getString("current_node_id"),rs.getString("current_state"),rs.getInt("current_round"),rs.getBoolean("finished")),id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module instance not found");
        ModuleContext module=rows.getFirst();
        if(module.finished()||!Objects.equals(module.moduleType(),definition.get("moduleType"))||!Objects.equals(module.nodeId(),definition.get("nodeId"))||!Objects.equals(module.state(),definition.get("stateCode")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Node form does not match the current workflow node");
        Long requestedProject=request==null?null:number(request.get("projectId"));
        if(requestedProject!=null&&!Objects.equals(requestedProject,module.projectId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Node form project does not match the module project");
        return module;
    }
    private void requireOperation(ActorContext actor,Long moduleId){if(operationPolicy==null||!operationPolicy.exactCanOperate(actor,moduleId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Current task cannot be operated by this user");}
    private String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private Object read(String value){try{return objectMapper.readValue(value,Object.class);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static Long number(Object v){if(v==null)return null;return v instanceof Number n?n.longValue():Long.valueOf(String.valueOf(v));}
    private static Map<String,Object> linked(Object...p){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<p.length;i+=2)m.put(String.valueOf(p[i]),p[i+1]);return m;}
    private record ModuleContext(Long projectId,String moduleType,String nodeId,String state,int round,boolean finished){}
}
