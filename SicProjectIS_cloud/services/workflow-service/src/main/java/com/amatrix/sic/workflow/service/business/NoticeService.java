package com.amatrix.sic.workflow.service.business;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.workflow.dao.NoticeMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;

/**
 * 公告字段校验及 CRUD（无删除入口），按全部/部门/用户范围控制可见性；发布修改要求科技处或系统管理员。
 */
@Service
public class NoticeService {
    private final NoticeMapper mapper;

    public NoticeService(NoticeMapper mapper) {
        this.mapper = mapper;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> list(ActorContext actor, String moduleType) {
        String module=moduleType==null||moduleType.isBlank()?null:moduleType.trim().toUpperCase(java.util.Locale.ROOT);
        List<Map<String,Object>> notices=mapper.list(module);
        return notices.stream().filter(notice -> visible(actor, notice)).toList();
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(ActorContext actor, Long noticeId) {
        Map<String,Object> notice=mapper.find(noticeId);
        if(notice==null||!visible(actor,notice))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notice not found");
        return notice;
    }

    @PreAuthorize("hasRole('SCIENCE_ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Transactional
    public Map<String, Object> create(ActorContext actor, Map<String, Object> request) {
        validate(request);
        Map<String,Object> values=values(actor.userId(),request);
        mapper.insert(values);
        return get(actor,((Number)Objects.requireNonNull(values.get("id"))).longValue());
    }

    @PreAuthorize("hasRole('SCIENCE_ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Transactional
    public Map<String, Object> update(Long noticeId, ActorContext actor, Map<String, Object> request) {
        validate(request);
        get(actor, noticeId);
        Map<String,Object> values=values(actor.userId(),request);
        values.put("id",noticeId);mapper.update(values);
        return get(actor, noticeId);
    }

    private boolean visible(ActorContext actor, Map<String, Object> notice) {
        if (actor != null && (actor.hasRole("SYSTEM_ADMIN") || actor.hasRole("SCIENCE_ADMIN"))) return true;
        String scope = String.valueOf(notice.get("noticeScope"));
        if (scope == null || scope.isBlank() || "null".equalsIgnoreCase(scope) || "ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        String normalized = scope.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("DEPT")) {
            return actor != null && actor.deptId() != null
                    && ids(notice.get("targetDeptScope")).contains(String.valueOf(actor.deptId()));
        }
        if (normalized.contains("USER")) {
            return actor != null && actor.userId() != null
                    && ids(notice.get("targetUserScope")).contains(String.valueOf(actor.userId()));
        }
        return false;
    }

    private Set<String> ids(Object raw) {
        if (raw == null) return Set.of();
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) return Set.of();
        value = value.replace('[', ' ').replace(']', ' ').replace("\"", "");
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                .collect(Collectors.toSet());
    }

    private Map<String,Object> values(Long actorId,Map<String,Object> request) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:List.of("noticeType","noticeTitle","noticeNo","publishUnit","noticeScope","targetDeptScope","targetUserScope","projectCategory","materialRequirementSummary","contentSummary","remark"))
            result.put(key,string(request,key));
        result.put("moduleType",string(request,"moduleType").toUpperCase(java.util.Locale.ROOT));result.put("actor",actorId);
        result.put("isLimitedProject",bool(request.get("isLimitedProject")));result.put("limitCount",integer(request.get("limitCount")));
        for(String key:List.of("publishTime","startTime","deadlineTime"))result.put(key,dateTime(request.get(key)));
        return result;
    }

    private void validate(Map<String, Object> request) {
        if (request == null || blank(string(request, "moduleType")) || blank(string(request, "noticeType"))
                || blank(string(request, "noticeTitle"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Module type, notice type and title are required");
        }
        LocalDateTime start = dateTime(request.get("startTime"));
        LocalDateTime deadline = dateTime(request.get("deadlineTime"));
        if (start != null && deadline != null && deadline.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notice deadline cannot be before start time");
        }
    }

    private static String string(Map<String, Object> values, String key) { Object value=values==null?null:values.get(key);return value==null?null:String.valueOf(value); }
    private static Boolean bool(Object value) { return value instanceof Boolean bool?bool:value==null?null:Boolean.valueOf(String.valueOf(value)); }
    private static Integer integer(Object value) { return value instanceof Number number?number.intValue():value==null?null:Integer.valueOf(String.valueOf(value)); }
    private static LocalDateTime dateTime(Object value) { return value instanceof LocalDateTime time?time:value==null||String.valueOf(value).isBlank()?null:LocalDateTime.parse(String.valueOf(value)); }
    private static boolean blank(String value) { return value==null||value.isBlank(); }
    private static Map<String,Object> linked(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);return result;}
}
