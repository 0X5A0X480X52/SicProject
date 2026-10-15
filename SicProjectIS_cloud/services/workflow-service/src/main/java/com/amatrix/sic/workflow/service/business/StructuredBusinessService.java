package com.amatrix.sic.workflow.service.business;

import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.workflow.dao.StructuredBusinessMapper;
import com.amatrix.sic.workflow.entity.BusinessTask;
import com.amatrix.sic.workflow.entity.ModuleRound;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.security.CanAccessProject;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 申请/合同/结题结构化正文、成果和财务结算持久化；校验当前业务节点操作人及关联模块/验收/状态记录，计算结余和执行率。
 */
@Service
public class StructuredBusinessService {
    private final StructuredBusinessMapper mapper;
    private final ObjectMapper objectMapper;
    private final WorkflowOperationPolicy operationPolicy;

    public StructuredBusinessService(StructuredBusinessMapper mapper, ObjectMapper objectMapper,
            WorkflowOperationPolicy operationPolicy) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.operationPolicy = operationPolicy;
    }

    @CanAccessProject
    public Map<String, Object> application(Long projectId) {
        var row=mapper.application(projectId);
        if(row==null)return linked("application",null,"extension",null,"detail",null);
        Map<String,Object> payload=read(row.json());
        if(payload.containsKey("application"))return payload;
        return linked("application",linked("applicationId",row.id(),"projectId",row.projectId(),"applicationTitle",row.title(),
                "isLimitedProject",row.limited(),"applicationSummary",row.summary(),"createdAt",row.createdAt(),"updatedAt",row.createdAt()),"extension",null,"detail",null);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> saveApplication(Long projectId, ActorContext actor, Map<String, Object> request) {
        Map<String, Object> application = child(request, "application", "Application data is required");
        BusinessTask current = requireBusinessTask(projectId,"APPLICATION","SubmitApplicationTask","APPLICATION_DRAFT",actor);
        Long id=mapper.applicationId(projectId,current.moduleId());
        if(id==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Project application not found");
        mapper.updateApplication(linked("id",id,"title",string(application.get("applicationTitle")),"limited",bool(application.get("isLimitedProject")),
                "summary",string(application.get("applicationSummary")),"json",write(request)));
        return application(projectId);
    }

    @CanAccessProject
    public Map<String, Object> contract(Long projectId) {
        return draft(projectId, "CONTRACT", "contract_form", "contract");
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> saveContract(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current = requireBusinessTask(projectId,"CONTRACT","FillContractTask","CONTRACT_DRAFT",actor);
        child(request, "contract", "Contract data is required");
        saveDraft(current, "contract_form", request);
        return contract(projectId);
    }

    @CanAccessProject
    public Map<String, Object> acceptance(Long projectId) {
        return draft(projectId, "ACCEPTANCE", "acceptance_form", "acceptance");
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> saveAcceptance(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current = requireBusinessTask(projectId,"ACCEPTANCE","SubmitAcceptanceMaterialsTask","ACCEPTANCE_MATERIAL_DRAFT",actor);
        child(request, "acceptance", "Acceptance data is required");
        saveDraft(current, "acceptance_form", request);
        return acceptance(projectId);
    }

    @CanAccessProject
    public List<Map<String, Object>> achievements(Long projectId) {
        return mapper.achievements(projectId);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> addAchievement(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current=requireBusinessTask(projectId,"ACCEPTANCE","SubmitAcceptanceMaterialsTask","ACCEPTANCE_MATERIAL_DRAFT",actor);
        String type = required(string(value(request, "achievementType")), "Achievement type is required");
        String title = required(string(value(request, "achievementTitle")), "Achievement title is required");
        Long moduleId = currentModuleId(request, current);
        requireRelatedAcceptance(number(value(request, "acceptanceId")), moduleId);
        LocalDate date = date(value(request, "publishOrGrantDate"));
        Map<String,Object> insert=linked("project",projectId,"module",moduleId,"acceptance",number(value(request,"acceptanceId")),"type",type,"title",title,
                "authors",string(value(request,"authorList")),"level",string(value(request,"achievementLevel")),"date",date,"proof",number(value(request,"proofMaterialVersionId")),"remark",string(value(request,"remark")));
        mapper.achievement(insert);Long id=((Number)Objects.requireNonNull(insert.get("id"))).longValue();
        return achievements(projectId).stream().filter(row -> id.equals(row.get("achievementId"))).findFirst()
                .orElseThrow();
    }

    @CanAccessProject
    public List<Map<String, Object>> settlements(Long projectId) {
        return mapper.settlements(projectId);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> addSettlement(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current=requireBusinessTask(projectId,"ACCEPTANCE","FinancialSettlementTask","ACCEPTANCE_FINANCIAL_SETTLEMENT",actor);
        Long moduleId = currentModuleId(request, current);
        requireRelatedAcceptance(number(value(request, "acceptanceId")), moduleId);
        requireStateRecord(number(value(request, "stateRecordId")), moduleId, current.round());
        BigDecimal received = decimal(value(request, "receivedAmount"));
        BigDecimal spent = decimal(value(request, "spentAmount"));
        if (received == null || spent == null) throw bad("Module, received amount and spent amount are required");
        if (received.signum() < 0 || spent.signum() < 0) throw bad("Financial amounts cannot be negative");
        BigDecimal surplus = received.subtract(spent);
        BigDecimal rate = received.signum() == 0 ? BigDecimal.ZERO
                : spent.multiply(new BigDecimal("100")).divide(received, 2, RoundingMode.HALF_UP);
        LocalDateTime settledAt = dateTime(value(request, "settledAt"));
        if (settledAt == null) settledAt = LocalDateTime.now();
        Map<String,Object> insert=linked("acceptance",number(value(request,"acceptanceId")),"project",projectId,"module",moduleId,"state",number(value(request,"stateRecordId")),
                "approved",decimal(value(request,"approvedAmount")),"received",received,"spent",spent,"surplus",surplus,"rate",rate,
                "result",string(value(request,"settlementResult")),"actor",actor.userId(),"comment",string(value(request,"financeReviewComment")),"settled",settledAt);
        mapper.settlement(insert);Long id=((Number)Objects.requireNonNull(insert.get("id"))).longValue();
        return settlements(projectId).stream().filter(row -> id.equals(row.get("settlementId"))).findFirst()
                .orElseThrow();
    }

    private Map<String, Object> draft(Long projectId, String moduleType, String table, String rootName) {
        String json=mapper.draft(projectId,moduleType);
        return json==null?linked(rootName,null,"extension",null):read(json);
    }

    private void saveDraft(BusinessTask module, String table, Map<String, Object> request) {
        mapper.saveDraft(table,module.moduleId(),module.round(),write(request));
    }

    private ModuleRound module(Long projectId, String moduleType) {
        ModuleRound module=mapper.module(projectId,moduleType);
        if(module==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,moduleType+" module instance not found");
        return module;
    }

    private void requireModuleProject(Long moduleId, Long projectId, boolean required) {
        if (moduleId == null) {
            if (required) throw bad("Module, received amount and spent amount are required");
            return;
        }
        Integer count = mapper.moduleProject(moduleId,projectId);
        if (count == null || count == 0) throw bad("Module instance does not belong to the project");
    }

    private Long currentModuleId(Map<String, Object> request, BusinessTask current) {
        Long requested = number(value(request, "moduleInstanceId"));
        if (requested != null && !requested.equals(current.moduleId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Business data module does not match the current workflow task");
        }
        return current.moduleId();
    }

    private void requireRelatedAcceptance(Long acceptanceId, Long moduleId) {
        if (acceptanceId == null) return;
        Integer count = mapper.relatedAcceptance(acceptanceId,moduleId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Acceptance record does not belong to the current workflow module");
        }
    }

    private void requireStateRecord(Long stateRecordId, Long moduleId, int round) {
        if (stateRecordId == null) return;
        Integer count = mapper.relatedState(stateRecordId,moduleId,round);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "State record does not belong to the current workflow round");
        }
    }

    private BusinessTask requireBusinessTask(Long projectId,String moduleType,String nodeId,String state,ActorContext actor){
        BusinessTask task=mapper.lockTask(projectId,moduleType,nodeId,state);
        if(task==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Business data does not match the current workflow node");
        if(!operationPolicy.exactCanOperate(actor,task.moduleId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Current task cannot be operated by this user");
        return task;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> request, String key, String message) {
        if (request == null || !(request.get(key) instanceof Map<?, ?> value)) throw bad(message);
        return (Map<String, Object>) value;
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read structured business data", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to save structured business data", ex);
        }
    }

    private static Object value(Map<String, Object> request, String key) {
        return request == null ? null : request.get(key);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw bad(message);
        return value;
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static Boolean bool(Object value) { return value instanceof Boolean bool ? bool : value == null ? null : Boolean.valueOf(String.valueOf(value)); }
    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(String.valueOf(value)); }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal decimal ? decimal : value instanceof Number number ? new BigDecimal(number.toString()) : value == null || String.valueOf(value).isBlank() ? null : new BigDecimal(String.valueOf(value)); }
    private static LocalDate date(Object value) { return value instanceof LocalDate date ? date : value == null || String.valueOf(value).isBlank() ? null : LocalDate.parse(String.valueOf(value)); }
    private static LocalDateTime dateTime(Object value) { return value instanceof LocalDateTime date ? date : value == null || String.valueOf(value).isBlank() ? null : LocalDateTime.parse(String.valueOf(value)); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static Map<String,Object> linked(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);return result;}
}
