package com.amatrix.sic.contracts;

import java.util.List;

/**
 * Workflow 发给 Project 的批量访问判定请求；一次传递业务操作人及多个待判定目标以减少 RPC。
 * @param actor 被判定的用户身份上下文，不是调用服务 token；Project 按其用户、部门和角色判断
 * @param targets 待判定目标列表；结果使用 decisionId 与 projectId 关联，不以列表位置作为唯一依据
 */
public record ProjectAccessBatchRequest(ActorContext actor, List<ProjectOperationTarget> targets) {
}
