package com.amatrix.sic.workflow.service.form;

import com.amatrix.sic.workflow.dao.review.ExpertReviewViewRepository;
import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.amatrix.sic.workflow.dao.NodeFormMapper;
import com.amatrix.sic.workflow.entity.NodeFormModule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.security.CanOperateModule;
import com.amatrix.sic.security.CanAccessModule;
import com.amatrix.sic.security.CanReadNodeForm;
import com.amatrix.sic.contracts.ActorContext;

import tools.jackson.databind.ObjectMapper;

/**
 * 维护 43 个节点表单目录；按节点/状态匹配，区分三种写模式；校验当前节点和轮次，存储 JSON，聚合业务草稿与实时专家评审视图。
 */
@Service
public class NodeFormService {
    private final NodeFormMapper mapper;
    private final ObjectMapper objectMapper;
    private final ExpertReviewViewRepository expertReviewViews;
    private final WorkflowOperationPolicy operationPolicy;
    @Autowired public NodeFormService(NodeFormMapper mapper,ObjectMapper objectMapper,ExpertReviewViewRepository expertReviewViews,WorkflowOperationPolicy operationPolicy){this.mapper=mapper;this.objectMapper=objectMapper;this.expertReviewViews=expertReviewViews;this.operationPolicy=operationPolicy;}

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

    /**
     * 仅在运行时权限、序号和模块锁校验后调用，加入迁移事务同时写节点表单与业务草稿；不是独立提交的补写操作。
     */
    public void persistTransition(Long moduleId, Long projectId, String moduleType, String nodeId,
            String state, int round, String formCode, Map<String,Object> request, Long userId) {
        List<Map<String,Object>> candidates = forNode(moduleType, nodeId, state);
        if (candidates.isEmpty()) {
            // 未注册表单的旧版或自定义定义继续按节点键保存数据，保留历史读取兼容性。
            if (formCode != null && !formCode.equals(moduleType + "_FORM"))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Node form does not match the current workflow node");
            store(moduleId, nodeId, round, request, userId);
            return;
        }
        Map<String,Object> definition = candidates.stream()
                .filter(d -> formCode == null || Objects.equals(d.get("formCode"), formCode)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Node form does not match the current workflow node"));
        Long requestedProject = number(request.get("projectId"));
        Long requestedModule = number(request.get("moduleInstanceId"));
        if ((requestedProject != null && !requestedProject.equals(projectId))
                || (requestedModule != null && !requestedModule.equals(moduleId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Node form context does not match the module");
        Object value = payload(definition, request);
        String code = String.valueOf(definition.get("formCode"));
        String key = "HISTORY_RECORD".equals(definition.get("writeMode"))
                ? code + "-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16) : code;
        store(moduleId, key, round, value, userId);
        persistDraft(String.valueOf(definition.get("dataKind")), moduleId, projectId, round, value);
        if ("FINANCIAL_SETTLEMENT".equals(definition.get("dataKind")))
            persistSettlement(moduleId, projectId, round, value, userId);
    }

    private void store(Long moduleId, String key, int round, Object value, Long userId) {
        mapper.store(moduleId, key, round, write(value), userId);
    }

    private void persistDraft(String kind, Long moduleId, Long projectId, int round, Object value) {
        if (!(value instanceof Map<?,?> draft)) return;
        if ("APPLICATION_DRAFT".equals(kind) && draft.get("application") instanceof Map<?,?> application) {
            if (mapper.updateApplication(moduleId, projectId, application, write(value)) == 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Project application draft is missing");
        } else if ("CONTRACT_DRAFT".equals(kind) || "ACCEPTANCE_DRAFT".equals(kind)) {
            mapper.storeDraft(kind, moduleId, round, write(value));
        }
    }

    private void persistSettlement(Long moduleId, Long projectId, int round, Object value, Long userId) {
        if (!(value instanceof Map<?,?> data))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Financial settlement data is required");
        java.math.BigDecimal received = amount(data.get("receivedAmount"));
        java.math.BigDecimal spent = amount(data.get("spentAmount"));
        if (received == null || spent == null || received.signum() < 0 || spent.signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Financial amounts must be nonnegative");
        java.math.BigDecimal surplus = received.subtract(spent);
        java.math.BigDecimal rate = received.signum() == 0 ? java.math.BigDecimal.ZERO
                : spent.multiply(new java.math.BigDecimal("100")).divide(received,2,java.math.RoundingMode.HALF_UP);
        Long stateId = mapper.latestState(moduleId, round);
        mapper.settlement(linked("project",projectId,"module",moduleId,"state",stateId,
                "approved",amount(data.get("approvedAmount")),"received",received,"spent",spent,
                "surplus",surplus,"rate",rate,"result",data.get("settlementResult"),"actor",userId,
                "comment",data.get("financeReviewComment")));
    }

    private static java.math.BigDecimal amount(Object value) {
        if (value == null) return null;
        try { return new java.math.BigDecimal(value.toString()); }
        catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid financial amount");
        }
    }

    /**
     * 按表单定义的写入模式校验当前节点与轮次，再保存节点表单及对应业务数据；兼容读取不会扩大可写范围。
     */
    @Transactional
    @CanOperateModule
    public Map<String,Object> save(Long moduleInstanceId,String formCode,Map<String,Object> request,ActorContext actor){
        Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"SINGLE_INSTANCE");
        NodeFormModule module=requireCurrentDefinition(moduleInstanceId,definition,request);requireOperation(actor,moduleInstanceId);
        Object payload=payload(definition,request);
        store(moduleInstanceId,formCode,module.round(),payload,actor.userId());
        persistDraft(String.valueOf(definition.get("dataKind")), moduleInstanceId, module.projectId(), module.round(), payload);
        return response(definition,module.projectId(),moduleInstanceId,number(request.get("stateRecordId")),payload);
    }

    @Transactional
    @CanOperateModule
    public Map<String,Object> createRecord(Long moduleInstanceId,String formCode,Map<String,Object> request,ActorContext actor){
        Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"HISTORY_RECORD");
        NodeFormModule module=requireCurrentDefinition(moduleInstanceId,definition,request);requireOperation(actor,moduleInstanceId);
        Object payload=payload(definition,request);
        Map<String,Object> insert=linked("module",moduleInstanceId,"key",formCode+"-"+java.util.UUID.randomUUID().toString().replace("-", "").substring(0,16),
                "round",module.round(),"json",write(payload),"actor",actor.userId());
        mapper.insertRecord(insert);
        Long id=((Number)Objects.requireNonNull(insert.get("id"))).longValue();return linked("definition",definition,"recordId",id,"data",response(definition,module.projectId(),moduleInstanceId,number(request.get("stateRecordId")),records(moduleInstanceId,formCode)));
    }

    @Transactional
    @CanOperateModule
    public Map<String,Object> updateRecord(Long moduleInstanceId,String formCode,Long recordId,Map<String,Object> request,ActorContext actor){
        Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"HISTORY_RECORD");
        NodeFormModule module=requireCurrentDefinition(moduleInstanceId,definition,request);requireOperation(actor,moduleInstanceId);Object payload=payload(definition,request);
        int updated=mapper.updateRecord(moduleInstanceId,recordId,formCode+"-%",module.round(),write(payload),actor.userId());
        if(updated==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Node form record not found");
        return linked("definition",definition,"recordId",recordId,"data",response(definition,module.projectId(),moduleInstanceId,number(request.get("stateRecordId")),records(moduleInstanceId,formCode)));
    }

    @Transactional
    @CanOperateModule
    public void deleteRecord(Long moduleInstanceId,String formCode,Long recordId,ActorContext actor){Map<String,Object> definition=requireDefinition(formCode);requireMode(definition,"HISTORY_RECORD");NodeFormModule module=requireCurrentDefinition(moduleInstanceId,definition,null);requireOperation(actor,moduleInstanceId);mapper.deleteRecord(moduleInstanceId,recordId,formCode+"-%",module.round());}

    @CanAccessModule
    public Object businessData(Long moduleInstanceId){
        String data=mapper.application(moduleInstanceId);
        if(data==null)data=mapper.contract(moduleInstanceId);
        if(data==null)data=mapper.acceptance(moduleInstanceId);
        Object stored=data==null?Map.of():read(data);
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

    private Object stored(Long moduleId,String code){
        if(moduleId==null)return null;
        Map<String,Object> definition=requireDefinition(code);
        String json=mapper.stored(moduleId,code,String.valueOf(definition.get("nodeId")));
        Object value=json==null?businessData(moduleId):read(json);
        return normalizeStored(definition,value);
    }
    private List<Object> records(Long moduleId,String code){
        if(moduleId==null)return List.of();
        Map<String,Object> definition=requireDefinition(code);
        return mapper.records(moduleId,code+"-%",String.valueOf(definition.get("nodeId"))).stream().<Object>map(row->{
            Object value=normalizeStored(definition,read(row.json()));
            if(value instanceof Map<?,?> source){Map<String,Object> copy=new LinkedHashMap<>();source.forEach((key,item)->copy.put(String.valueOf(key),item));copy.putIfAbsent("recordId",row.id());return copy;}
            return linked("recordId",row.id(),"value",value);
        }).toList();
    }
    /**
     * 读取已保存表单时规范化为当前响应形状，保持历史数据兼容；不直接覆盖数据库原文。
     */
    @SuppressWarnings("unchecked")
    private static Object normalizeStored(Map<String,Object> definition,Object value){return value instanceof Map<?,?> map?payload(definition,(Map<String,Object>)map):value;}
    private Map<String,Object> requireDefinition(String code){return definitions().stream().filter(d->Objects.equals(d.get("formCode"),code)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Node form definition not found"));}
    private static Map<String,Object> definition(String code,String type,String node,String state,String title,String kind,String mode){return linked("formCode",code,"moduleType",type,"nodeId",node,"stateCode",state,"title",title,"dataKind",kind,"writeMode",mode,"supportsFiles",true,"materialTypeCodes",List.of());}
    private static void requireMode(Map<String,Object> definition,String expected){if(!expected.equals(definition.get("writeMode")))throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,"Node form does not support this write mode");}
    private static Object payload(Map<String,Object> definition,Map<String,Object> request){if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Request body is required");String kind=String.valueOf(definition.get("dataKind"));Object value=switch(kind){case"APPLICATION_DRAFT"->request.get("applicationDraft");case"CONTRACT_DRAFT"->request.get("contractDraft");case"ACCEPTANCE_DRAFT"->request.get("acceptanceDraft");case"NOTICE"->request.get("notice");case"EXPERT_REVIEW"->request.get("expertReview");case"CHECK_ITEM"->child(request.get("runtimeRecord"),"checkItem");case"EXTERNAL_RESULT"->child(request.get("runtimeRecord"),"externalResult");case"SEAL"->child(request.get("runtimeRecord"),"sealRecord");case"SUBMISSION"->child(request.get("runtimeRecord"),"submissionRecord");case"ARCHIVE"->child(request.get("runtimeRecord"),"archiveRecord");case"PUBLICITY"->child(request.get("projectRecord"),"publicity");case"FINANCIAL_SETTLEMENT"->child(request.get("projectRecord"),"financialSettlement");case"ACHIEVEMENT"->child(request.get("projectRecord"),"achievement");case"SURPLUS_RETURN"->child(request.get("projectRecord"),"surplusReturn");default->null;};return value==null?request:value;}
    private static Object child(Object source,String key){return source instanceof Map<?,?> values?values.get(key):null;}
    private void requireModule(Long id){Integer count=mapper.moduleCount(id);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module instance not found");}
    private NodeFormModule requireCurrentDefinition(Long id,Map<String,Object> definition,Map<String,Object> request){
        NodeFormModule module=mapper.lockModule(id);
        if(module==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module instance not found");
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
}
