package com.amatrix.sic.material.policy;

import com.amatrix.sic.material.client.MaterialProjectAccessClient;
import com.amatrix.sic.material.client.auth.MaterialTokenProvider;

import org.springframework.stereotype.Component;
import com.amatrix.sic.contracts.ActorContext;

/**
 * 将操作人和 MATERIAL_READ/WRITE/DELETE 等操作发给 Project，使用内部 Token，按响应判定权限。
 */
@Component("materialAccessPolicy")
public class MaterialAccessPolicy{
    private final MaterialProjectAccessClient projects;private final MaterialTokenProvider tokens;
    public MaterialAccessPolicy(MaterialProjectAccessClient projects,MaterialTokenProvider tokens){this.projects=projects;this.tokens=tokens;}

    /**
     * 将当前用户身份和材料操作代码发送给 Project 做权威项目范围判定。
     * <p>Feign Authorization 使用 material-service token；业务用户信息来自 actor。只有响应 success=true、data 非空且 allowed=true 才放行。
     * Feign 异常会向上抛出而不是返回 true；业务拒绝返回 false，由材料服务入口转换为 403。
     */
    public boolean canAccess(ActorContext actor,Long projectId,String operation){var r=projects.decide(tokens.authorization(),projectId,actor.userId(),actor.deptId(),operation,actor.roleCodes());return r.success()&&r.data()!=null&&r.data().allowed();}
}
