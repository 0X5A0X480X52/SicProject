package com.amatrix.sic.project.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amatrix.sic.project.dao.ProjectQueryMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ProjectQueryService {
    private final ProjectQueryMapper mapper;

    public ProjectQueryService(ProjectQueryMapper mapper) {
        this.mapper = mapper;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> accessibleProjects(Long userId, Long deptId, List<String> roles) {
        boolean global = roles.contains("SYSTEM_ADMIN") || roles.contains("SCIENCE_ADMIN");
        boolean department = roles.contains("DEPT_ADMIN") && deptId != null;
        boolean expert = roles.contains("EXPERT");
        boolean finance = roles.contains("FINANCE_ADMIN");
        return mapper.accessible(userId,deptId,global,department,expert,finance);
    }

    public static List<String> roles(Object claim) {
        return claim instanceof Collection<?> values ? values.stream().map(String::valueOf).toList() : List.of();
    }

    public static Long number(Object claim) {
        return claim instanceof Number number ? number.longValue() : null;
    }

    static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }

    static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
