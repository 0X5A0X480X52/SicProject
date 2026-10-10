package com.amatrix.sic.contracts;

import java.util.List;

public record ProjectAccessBatchRequest(ActorContext actor, List<ProjectOperationTarget> targets) {
}
