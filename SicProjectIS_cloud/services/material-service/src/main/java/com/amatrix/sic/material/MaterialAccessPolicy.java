package com.amatrix.sic.material;

import org.springframework.stereotype.Component;
import com.amatrix.sic.contracts.ActorContext;

@Component("materialAccessPolicy")
public class MaterialAccessPolicy{
    private final MaterialProjectAccessClient projects;private final MaterialTokenProvider tokens;
    public MaterialAccessPolicy(MaterialProjectAccessClient projects,MaterialTokenProvider tokens){this.projects=projects;this.tokens=tokens;}
    public boolean canAccess(ActorContext actor,Long projectId,String operation){var r=projects.decide(tokens.authorization(),projectId,actor.userId(),actor.deptId(),operation,actor.roleCodes());return r.success()&&r.data()!=null&&r.data().allowed();}
}
