package com.amatrix.sic.material.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import com.amatrix.sic.contracts.AccessDecision;
import com.amatrix.sic.contracts.ApiResponse;

/**
 * Feign 调用 Project 的内部单项访问决策。
 */
@FeignClient(name="project-service",contextId="materialProjectAccessClient")
public interface MaterialProjectAccessClient{
    /**
     * {@code GET /internal/projects/{projectId}/access}：向 Project 判定用户的材料读写或删除权限。
     * <p>Authorization 使用 material-service 内部 Bearer token；userId、deptId、roles 来自已验证用户 ActorContext，
     * operation 由 Material 服务传入 MATERIAL_READ、MATERIAL_WRITE 或 MATERIAL_DELETE。
     * <p>返回 AccessDecision；业务拒绝为 allowed=false，Feign/Project 故障不应转换为允许访问。
     * 此单项接口不传模块、轮次或节点，范围受限授权按 Project 的通用匹配规则判定。
     */
    @GetMapping("/internal/projects/{projectId}/access") ApiResponse<AccessDecision> decide(@RequestHeader("Authorization")String authorization,@PathVariable Long projectId,@RequestParam Long userId,@RequestParam(required=false)Long deptId,@RequestParam String operation,@RequestParam List<String>roles);
}
