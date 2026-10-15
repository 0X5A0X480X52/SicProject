package com.amatrix.sic.workflow.dto;

import java.math.BigDecimal;
import java.util.List;

/** 评审批次、专家指派和本人分项评分的输入；模块 ID、节点配置 ID 和 assignment ID 用途不同。 */
public final class ExpertReviewDtos {
    private ExpertReviewDtos() {}

    /**
     * 指派节点创建评审批次，配置当前轮次的评分阈值。
     * @param moduleInstanceId 当前 Workflow 模块实例 ID，服务据此锁定模块和业务轮次
     * @param workflowNodeId 当前定义的节点配置主键，不是 BPMN 节点代码
     * @param reviewType 必填评审类型，用于区分部门、科技处或验收评审
     * @param reviewTitle 必填批次标题
     * @param ruleType 规则配置；空白时存为 AVERAGE，当前聚合实现仍按有效评分算术平均
     * @param minExpertCount 可选规则配置，空值存为 3；当前批次完成条件仍按实际指派人数
     * @param passScore 可选通过阈值
     * @param recommendScore 可选推荐阈值，达到时优先返回 RECOMMENDED
     * @param removeHighestLowest 是否在有效评分至少 5 人时去掉一个最高分和最低分
     * @param expectedExpertCount 请求中的预期人数配置；实际完成条件以服务中已指派与已提交人数统计为准
     */
    public record CreateBatchRequest(Long moduleInstanceId, Long workflowNodeId, String reviewType, String reviewTitle,
            String ruleType, Integer minExpertCount, BigDecimal passScore, BigDecimal recommendScore,
            Boolean removeHighestLowest, Integer expectedExpertCount) {}

    /**
     * 向评审批次指派专家的请求。
     * @param expertUserId 必填 IAM 用户 ID；必须启用、具有已审批 EXPERT 角色，且不能是当前指派人本人
     * @param expertName 可选展示姓名；空白时使用 IAM 当前 realName
     * @param expertOrg 可选专家单位快照
     * @param expertTitle 可选职称快照
     */
    public record AssignExpertRequest(Long expertUserId, String expertName, String expertOrg, String expertTitle) {}

    /**
     * 被指派专家提交本人评分的请求；assignmentId 由路径提供，不在请求体中重复传递。
     * @param conflictOfInterest 是否存在利益冲突；为 true 时本次评分标为无效
     * @param valid 可选人工有效标志；空值按有效处理，但利益冲突优先使其无效
     * @param reviewResult 可选评审结论文本
     * @param reviewComment 可选总体评语
     * @param scores 至少一个分项；一次提交后 assignment 进入 SUBMITTED，不能再次覆盖
     */
    public record SubmitScoreRequest(Boolean conflictOfInterest, Boolean valid, String reviewResult, String reviewComment,
            List<ScoreItem> scores) {}

    /**
     * 本人评分的一个分项，服务将 scoreValue × weight 相加并保留两位小数。
     * @param itemCode 必填评分项代码，用于保存分项标识
     * @param itemName 必填评分项名称快照
     * @param weight 缺省为 1；当前计算不按总权重自动归一化
     * @param maxScore 缺省为 100，用于限制该分项值范围
     * @param scoreValue 必填分值，必须介于 0 与 maxScore 之间
     * @param comment 可选分项评语
     */
    public record ScoreItem(String itemCode, String itemName, BigDecimal weight, BigDecimal maxScore,
            BigDecimal scoreValue, String comment) {}
}
