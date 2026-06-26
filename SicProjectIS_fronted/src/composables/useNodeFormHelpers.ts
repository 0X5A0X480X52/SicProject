import type { NodeFormDataKind, NodeFormDefinition, NodeFormSaveRequest } from '../types/nodeForms'
import type { FlatFormData } from '../utils/nodeFormFields'

function numberOrUndefined(value: unknown): number | undefined {
  if (value === undefined || value === null || value === '') return undefined
  const next = Number(value)
  return Number.isNaN(next) ? undefined : next
}

/**
 * Build a complete NodeFormSaveRequest from a flat form data record.
 * Ported from NodeFormsDebugView.buildRequest() — covers all 15 dataKinds.
 */
export function buildRequestFromFlatForm(
  kind: NodeFormDataKind | undefined,
  form: FlatFormData,
  context: { projectId?: number; moduleInstanceId?: number; stateRecordId?: number },
  definition?: NodeFormDefinition,
): NodeFormSaveRequest {
  const base: NodeFormSaveRequest = {
    projectId: context.projectId,
    moduleInstanceId: context.moduleInstanceId,
    stateRecordId: context.stateRecordId,
  }

  switch (kind) {
    case 'APPLICATION_DRAFT':
      return {
        ...base,
        applicationDraft: {
          application: {
            applicationTitle: form.applicationTitle,
            isLimitedProject: form.isLimitedProject,
            applicationSummary: form.applicationSummary,
          },
          extension: {
            moduleInstanceId: context.moduleInstanceId,
            applicationCategory: form.applicationCategory,
            expectedBudget: numberOrUndefined(form.expectedBudget),
            isLimitedProject: form.isLimitedProject,
          },
          detail: {
            researchBackground: form.researchBackground,
            researchObjective: form.researchObjective,
            researchContent: form.researchContent,
            expectedOutcomes: form.expectedOutcomes,
          },
        },
      }

    case 'CONTRACT_DRAFT':
      return {
        ...base,
        contractDraft: {
          contract: {
            contractCode: form.contractCode,
            contractName: form.contractName,
            contractAmount: numberOrUndefined(form.contractAmount),
            sealStatus: form.sealStatus,
          },
          extension: {
            moduleInstanceId: context.moduleInstanceId,
            partyAName: form.partyAName,
            partyBName: form.partyBName,
            contractSource: form.contractSource,
          },
        },
      }

    case 'ACCEPTANCE_DRAFT':
      return {
        ...base,
        acceptanceDraft: {
          acceptance: {
            certificateNo: form.certificateNo,
            conclusion: form.conclusion,
          },
          extension: {
            moduleInstanceId: context.moduleInstanceId,
            acceptanceType: form.acceptanceType,
            taskCompletionRate: numberOrUndefined(form.taskCompletionRate),
            acceptanceBatchNo: form.acceptanceBatchNo,
          },
        },
      }

    case 'NOTICE':
      return {
        ...base,
        notice: {
          moduleType: definition?.moduleType,
          noticeType: form.noticeType || (definition?.moduleType ? `${definition.moduleType}_NOTICE` : undefined),
          noticeTitle: form.noticeTitle || definition?.title,
          noticeNo: form.noticeNo,
          publishUnit: form.publishUnit,
          noticeScope: form.noticeScope,
          contentSummary: form.contentSummary || form.remark,
          materialRequirementSummary: form.materialRequirementSummary,
        },
      }

    case 'CHECK_ITEM':
      return {
        ...base,
        runtimeRecord: {
          checkItem: {
            itemCode: definition?.formCode,
            itemName: definition?.title,
            itemType: 'REVIEW_RESULT',
            itemValue: String(form.approved !== false),
            itemResult: form.approved !== false ? 'APPROVED' : 'REJECTED',
            required: true,
            passed: form.approved !== false,
            remark: form.remark,
            sortNo: 1,
            moduleInstanceId: context.moduleInstanceId,
            stateRecordId: context.stateRecordId,
          },
        },
      }

    case 'EXTERNAL_RESULT':
      return {
        ...base,
        runtimeRecord: {
          externalResult: {
            resultType: form.resultType,
            externalActorCode: form.externalActorCode,
            externalActorName: form.externalActorName,
            externalResult: form.approved !== false ? 'APPROVED' : 'REJECTED',
            externalFileNo: form.externalFileNo,
            externalSystemNo: form.externalSystemNo,
            approvedAmount: numberOrUndefined(form.approvedAmount),
            summary: form.summary || form.remark,
          },
        },
      }

    case 'SEAL':
      return {
        ...base,
        runtimeRecord: {
          sealRecord: {
            sealSubject: form.sealSubject,
            sealType: form.sealType,
            sealReason: form.sealReason,
            copyCount: numberOrUndefined(form.copyCount),
            leaderSigned: form.leaderSigned,
            schoolSealed: form.schoolSealed,
            externalSealed: form.externalSealed,
            sealStatus: form.sealStatus,
            remark: form.remark,
          },
        },
      }

    case 'SUBMISSION':
      return {
        ...base,
        runtimeRecord: {
          submissionRecord: {
            submissionType: form.submissionType,
            targetActorCode: form.targetActorCode,
            targetActorName: form.targetActorName,
            submissionMethod: form.submissionMethod,
            submissionNo: form.submissionNo,
            receiptNo: form.receiptNo,
            remark: form.remark,
          },
        },
      }

    case 'ARCHIVE':
      return {
        ...base,
        runtimeRecord: {
          archiveRecord: {
            archiveType: form.archiveType,
            archiveNo: form.archiveNo,
            archiveLocation: form.archiveLocation,
            paperCopyCount: numberOrUndefined(form.paperCopyCount),
            electronicCopyCount: numberOrUndefined(form.electronicCopyCount),
            archiveStatus: form.archiveStatus,
            remark: form.remark,
          },
        },
      }

    case 'PUBLICITY':
      return {
        ...base,
        projectRecord: {
          publicity: {
            publicityTitle: form.publicityTitle,
            publicityScope: form.publicityScope,
            recommendedRank: numberOrUndefined(form.recommendedRank),
            recommendedReason: form.recommendedReason,
            hasObjection: form.hasObjection,
            objectionContent: form.objectionContent,
            publicityResult: form.publicityResult || (form.approved !== false ? 'APPROVED' : 'REJECTED'),
            moduleInstanceId: context.moduleInstanceId,
            stateRecordId: context.stateRecordId,
          },
        },
      }

    case 'FINANCIAL_SETTLEMENT':
      return {
        ...base,
        projectRecord: {
          financialSettlement: {
            moduleInstanceId: context.moduleInstanceId,
            stateRecordId: context.stateRecordId,
            approvedAmount: numberOrUndefined(form.approvedAmount),
            receivedAmount: numberOrUndefined(form.receivedAmount),
            spentAmount: numberOrUndefined(form.spentAmount),
            settlementResult: form.settlementResult || (form.approved !== false ? 'APPROVED' : 'REJECTED'),
            financeReviewComment: form.financeReviewComment || form.remark,
          },
        },
      }

    case 'ACHIEVEMENT':
      return {
        ...base,
        projectRecord: {
          achievement: {
            moduleInstanceId: context.moduleInstanceId,
            achievementType: form.achievementType,
            achievementTitle: form.achievementTitle,
            authorList: form.authorList,
            achievementLevel: form.achievementLevel,
            proofMaterialVersionId: numberOrUndefined(form.proofMaterialVersionId),
            remark: form.remark,
          },
        },
      }

    case 'SURPLUS_RETURN':
      return {
        ...base,
        projectRecord: {
          surplusReturn: {
            surplusAmount: numberOrUndefined(form.surplusAmount),
            returnRequired: form.returnRequired,
            returnAccountName: form.returnAccountName,
            returnAccountNo: form.returnAccountNo,
            returnBankName: form.returnBankName,
            returnStatus: form.returnStatus,
            returnedAmount: numberOrUndefined(form.returnedAmount),
            remark: form.remark,
            moduleInstanceId: context.moduleInstanceId,
            stateRecordId: context.stateRecordId,
          },
        },
      }

    case 'EXPERT_REVIEW':
      return {
        ...base,
        expertReview: {
          batchId: numberOrUndefined(form.batchId),
          assignmentId: numberOrUndefined(form.assignmentId),
          createBatch: form.reviewTitle
            ? {
                moduleInstanceId: context.moduleInstanceId,
                workflowNodeId: definition?.nodeId,
                reviewType: definition?.stateCode,
                reviewTitle: form.reviewTitle,
                ruleType: form.ruleType,
                minExpertCount: numberOrUndefined(form.minExpertCount),
                passScore: numberOrUndefined(form.passScore),
                recommendScore: numberOrUndefined(form.recommendScore),
                removeHighestLowest: form.removeHighestLowest,
                expectedExpertCount: numberOrUndefined(form.expectedExpertCount),
              }
            : undefined,
          assignExpert: form.expertUserId
            ? {
                expertUserId: numberOrUndefined(form.expertUserId),
                expertName: form.expertName,
              }
            : undefined,
          submitScore: form.scoreValue
            ? {
                reviewResult: form.reviewResult,
                reviewComment: form.reviewComment,
                scores: [
                  {
                    itemCode: form.itemCode,
                    itemName: form.itemName,
                    scoreValue: numberOrUndefined(form.scoreValue),
                  },
                ],
              }
            : undefined,
        },
      }

    default:
      return base
  }
}

/**
 * Quick action button configuration by node type category.
 */
export interface QuickActionConfig {
  category: 'approval' | 'draft' | 'operation' | 'project_record' | 'notice' | 'expert' | 'generic'
  /** Label for the primary approve/submit button */
  primaryLabel: string
  /** Label for the secondary reject button (only for approval category) */
  secondaryLabel?: string
  /** Whether this node supports quick approve/reject without opening drawer */
  supportsQuickAction: boolean
  /** Hint text shown below the quick action buttons */
  description?: string
  /** Placeholder for the remark textarea (varies by node type) */
  remarkPlaceholder?: string
}

/**
 * Detect the expert review sub-type from the current node context.
 * Returns 'assign' | 'review' | 'summary' | null.
 */
export function detectExpertSubType(
  currentNodeId?: string | null,
  candidateRoleCode?: string,
): 'assign' | 'review' | 'summary' | null {
  if (!currentNodeId) return null
  const id = String(currentNodeId)
  if (id.includes('ExpertAssign') || id.includes('Assign')) return 'assign'
  if (id.includes('ExpertSummary') || id.includes('Summary')) return 'summary'
  if (candidateRoleCode === 'EXPERT') return 'review'
  return null
}

/**
 * Return quick action button configuration based on the node's dataKind.
 */
export function quickActionConfig(kind?: NodeFormDataKind, expertSubType?: string | null): QuickActionConfig {
  switch (kind) {
    case 'CHECK_ITEM':
    case 'EXTERNAL_RESULT':
      return {
        category: 'approval',
        primaryLabel: '通过 ✓',
        secondaryLabel: '不通过 ✗',
        supportsQuickAction: true,
        remarkPlaceholder: '填写审批意见',
      }

    case 'APPLICATION_DRAFT':
    case 'CONTRACT_DRAFT':
    case 'ACCEPTANCE_DRAFT':
      return {
        category: 'draft',
        primaryLabel: '提交草稿',
        secondaryLabel: '保存草稿',
        supportsQuickAction: true,
        remarkPlaceholder: '填写办理说明',
      }

    case 'SEAL':
    case 'SUBMISSION':
    case 'ARCHIVE':
      return {
        category: 'operation',
        primaryLabel: '确认办理',
        supportsQuickAction: true,
        remarkPlaceholder: '填写办理说明',
      }

    case 'PUBLICITY':
    case 'FINANCIAL_SETTLEMENT':
    case 'ACHIEVEMENT':
    case 'SURPLUS_RETURN':
      return {
        category: 'project_record',
        primaryLabel: '提交记录',
        supportsQuickAction: true,
        remarkPlaceholder: '填写办理说明',
      }

    case 'NOTICE':
      return {
        category: 'notice',
        primaryLabel: '发布通知',
        supportsQuickAction: true,
        remarkPlaceholder: '填写通知说明',
      }

    case 'EXPERT_REVIEW': {
      if (expertSubType === 'assign') {
        return {
          category: 'expert',
          primaryLabel: '进入专家评审管理',
          supportsQuickAction: false,
          description: '当前为分配评审专家结点，请在办理面板中创建评审批次、选择并邀请专家。',
          remarkPlaceholder: '填写节点办理备注（可选）',
        }
      }
      if (expertSubType === 'review') {
        return {
          category: 'expert',
          primaryLabel: '进入专家评审面板',
          supportsQuickAction: false,
          description: '当前为专家审核提交结点，请在办理面板中选择您的评分任务并提交评分结果。',
          remarkPlaceholder: '填写审核备注（可选）',
        }
      }
      if (expertSubType === 'summary') {
        return {
          category: 'expert',
          primaryLabel: '进入汇总评审面板',
          supportsQuickAction: false,
          description: '当前为汇总提交专家评审结果结点，请在办理面板中查看专家提交进度并汇总最终结果。',
          remarkPlaceholder: '填写汇总备注（可选）',
        }
      }
      return {
        category: 'expert',
        primaryLabel: '进入专家评审面板',
        supportsQuickAction: false,
        description: '当前为专家评审结点，请在办理面板中进行专家评审相关操作。',
        remarkPlaceholder: '填写办理备注（可选）',
      }
    }

    default:
      return {
        category: 'generic',
        primaryLabel: '提交办理',
        supportsQuickAction: false,
        remarkPlaceholder: '填写审批意见或办理说明',
      }
  }
}

/**
 * Composable wrapper for node form helpers.
 */
export function useNodeFormHelpers() {
  return {
    buildRequestFromFlatForm,
    quickActionConfig,
    detectExpertSubType,
  }
}

