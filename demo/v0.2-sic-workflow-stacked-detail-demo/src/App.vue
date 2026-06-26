<template>
  <main class="workflow-page">
    <section class="hero-card">
      <div class="hero-main">
        <el-button class="back-button" text :icon="ArrowLeft">返回</el-button>
        <div class="hero-text">
          <div class="title-line">
            <h1>{{ process.moduleName }}</h1>
            <el-tag type="primary" effect="light">{{ process.statusText }}</el-tag>
            <el-tag type="warning" effect="light">第 {{ process.currentRoundNo }} 轮</el-tag>
          </div>
          <p>{{ process.projectName }}</p>
        </div>
      </div>
      <div class="hero-actions">
        <el-button :icon="Refresh">刷新</el-button>
        <el-button>查看材料</el-button>
        <el-button type="primary" :icon="EditPen">办理当前节点</el-button>
      </div>
    </section>

    <section class="summary-strip">
      <div v-for="item in summaryItems" :key="item.label" class="summary-item">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </section>

    <section class="content-stack">
      <el-card shadow="never" class="section-card detail-stack-card">
        <template #header>
          <div class="card-title-row compact">
            <div>
              <h2>项目详细信息</h2>
              <p>保留详情，但用堆叠折叠方式收纳，避免占据主流程视线</p>
            </div>
            <el-tag type="info" effect="plain">堆叠区</el-tag>
          </div>
        </template>

        <el-collapse v-model="openedDetailPanels" class="detail-collapse">
          <el-collapse-item title="基础信息" name="base">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="项目编号">{{ process.projectNo }}</el-descriptions-item>
              <el-descriptions-item label="申报编号">{{ process.applicationNo }}</el-descriptions-item>
              <el-descriptions-item label="生命周期">{{ process.lifecycle }}</el-descriptions-item>
              <el-descriptions-item label="负责人">{{ process.owner }}</el-descriptions-item>
              <el-descriptions-item label="所属单位">{{ process.department }}</el-descriptions-item>
              <el-descriptions-item label="联系方式">{{ process.contact }}</el-descriptions-item>
            </el-descriptions>
          </el-collapse-item>

          <el-collapse-item title="项目属性与周期" name="attribute">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="项目类型">{{ process.projectType }}</el-descriptions-item>
              <el-descriptions-item label="项目级别">{{ process.projectLevel }}</el-descriptions-item>
              <el-descriptions-item label="预算金额">{{ process.budget }}</el-descriptions-item>
              <el-descriptions-item label="开始日期">{{ process.startDate }}</el-descriptions-item>
              <el-descriptions-item label="结束日期">{{ process.endDate }}</el-descriptions-item>
              <el-descriptions-item label="最近更新">{{ process.updatedAt }}</el-descriptions-item>
            </el-descriptions>
          </el-collapse-item>

          <el-collapse-item title="合作单位与关键词" name="extra">
            <div class="tag-block">
              <span>合作单位</span>
              <div>
                <el-tag v-for="item in process.collaborators" :key="item" effect="plain">{{ item }}</el-tag>
              </div>
            </div>
            <div class="tag-block">
              <span>关键词</span>
              <div>
                <el-tag v-for="item in process.keywords" :key="item" type="primary" effect="light">{{ item }}</el-tag>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <el-card shadow="never" class="section-card process-card">
        <template #header>
          <div class="card-title-row">
            <div>
              <h2>流程办理进度</h2>
              <p>点击节点查看该节点状态与处理说明</p>
            </div>
            <el-tag type="info" effect="plain">{{ process.nodes.length }} 步</el-tag>
          </div>
        </template>

        <ProcessSteps
          :nodes="process.nodes"
          :selected-node-id="selectedNodeId"
          @select-node="handleSelectNode"
        />

        <div class="selected-node-panel">
          <div class="selected-main">
            <span class="eyebrow">选中节点</span>
            <strong>{{ selectedNode.name }}</strong>
            <p>{{ selectedNode.desc }}</p>
          </div>
          <div class="selected-meta">
            <span>责任角色</span>
            <strong>{{ selectedNode.actor }}</strong>
          </div>
          <div class="selected-meta">
            <span>节点状态</span>
            <strong>{{ selectedNode.statusText }}</strong>
          </div>
          <div class="selected-meta">
            <span>完成时间</span>
            <strong>{{ selectedNode.finishedAt || '未完成' }}</strong>
          </div>
        </div>
      </el-card>

      <section class="workbench-grid">
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-title-row compact">
              <div>
                <h2>当前办理任务</h2>
                <p>页面最核心的操作入口，只保留办理所需字段</p>
              </div>
              <el-tag type="primary" effect="plain">{{ process.currentTask.mode }}</el-tag>
            </div>
          </template>
          <div class="task-grid">
            <div>
              <span>当前节点</span>
              <strong>{{ process.currentTask.nodeName }}</strong>
            </div>
            <div>
              <span>处理角色</span>
              <strong>{{ process.currentTask.roleName }}</strong>
            </div>
            <div>
              <span>处理人</span>
              <strong>{{ process.currentTask.assignee }}</strong>
            </div>
            <div>
              <span>截止时间</span>
              <strong>{{ process.currentTask.deadline }}</strong>
            </div>
          </div>
          <el-alert class="task-alert" type="info" :closable="false" show-icon>
            {{ process.currentTask.instruction }}
          </el-alert>
          <div class="action-row">
            <el-button
              v-for="action in process.availableActions"
              :key="action.name"
              :type="action.type === 'default' ? '' : action.type"
            >
              {{ action.name }}
            </el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-title-row compact">
              <div>
                <h2>材料与意见</h2>
                <p>材料清单、审批意见和风险提示统一放在一个办理辅助面板</p>
              </div>
            </div>
          </template>

          <el-tabs model-value="materials" class="clean-tabs">
            <el-tab-pane label="材料" name="materials">
              <div class="check-row">
                <el-tag
                  v-for="check in process.materialChecks"
                  :key="check.label"
                  :type="check.status"
                  effect="light"
                >
                  {{ check.label }}：{{ check.value }}
                </el-tag>
              </div>
              <div class="material-list">
                <div v-for="material in process.materials" :key="material.id" class="material-item">
                  <div>
                    <strong>{{ material.name }}</strong>
                    <span>{{ material.type }} · {{ material.version }} · {{ material.owner }} · {{ material.updatedAt }}</span>
                  </div>
                  <el-tag size="small" :type="material.status === '待生成' ? 'warning' : 'success'" effect="light">
                    {{ material.status }}
                  </el-tag>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="意见" name="opinions">
              <div class="opinion-list">
                <div v-for="opinion in process.opinions" :key="opinion.id" class="opinion-item">
                  <div>
                    <strong>{{ opinion.role }} · {{ opinion.operator }}</strong>
                    <span>{{ opinion.time }}</span>
                  </div>
                  <el-tag size="small" :type="opinion.conclusion === '通过' ? 'success' : 'info'" effect="light">
                    {{ opinion.conclusion }}
                  </el-tag>
                  <p>{{ opinion.content }}</p>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="风险" name="risks">
              <div class="risk-list">
                <el-alert
                  v-for="risk in process.risks"
                  :key="risk.id"
                  :type="risk.type"
                  :title="risk.title"
                  :description="risk.desc"
                  :closable="false"
                  show-icon
                />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </section>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-title-row compact">
            <div>
              <h2>本轮流转记录</h2>
              <p>仅展示当前第 {{ process.currentRound.roundNo }} 轮，历史退回放入下方轮次记录</p>
            </div>
            <el-tag type="primary" effect="plain">{{ process.currentRound.records.length }} 条</el-tag>
          </div>
        </template>
        <RecordTimeline :records="process.currentRound.records" />
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-title-row compact">
            <div>
              <h2>历史轮次</h2>
              <p>默认折叠，仅在排查退回原因时展开</p>
            </div>
            <el-tag type="warning" effect="plain">{{ process.historyRounds.length }} 轮</el-tag>
          </div>
        </template>

        <el-collapse accordion>
          <el-collapse-item
            v-for="round in process.historyRounds"
            :key="round.roundNo"
            :name="round.roundNo"
          >
            <template #title>
              <div class="round-title">
                <strong>第 {{ round.roundNo }} 轮</strong>
                <el-tag size="small" type="warning" effect="light">{{ round.resultText }}</el-tag>
                <span>{{ round.summary }}</span>
                <em>{{ round.startAt }} ~ {{ round.endAt }}</em>
              </div>
            </template>
            <RecordTimeline :records="round.records" />
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <el-card shadow="never" class="section-card helper-card">
        <template #header>
          <div class="card-title-row compact">
            <div>
              <h2>辅助查看</h2>
              <p>BPMN 与原始流程信息不作为主视图，按需展开</p>
            </div>
          </div>
        </template>

        <el-collapse>
          <el-collapse-item title="BPMN 流程图预览" name="bpmn">
            <div class="bpmn-placeholder">
              <div class="bpmn-node done">发布通知</div>
              <div class="bpmn-arrow">→</div>
              <div class="bpmn-node done">提交材料</div>
              <div class="bpmn-arrow">→</div>
              <div class="bpmn-node done">学院审核</div>
              <div class="bpmn-arrow">→</div>
              <div class="bpmn-node current">专家评审</div>
              <div class="bpmn-arrow">→</div>
              <div class="bpmn-node wait">经费审核</div>
            </div>
          </el-collapse-item>
          <el-collapse-item title="本页建议补齐的核心面板" name="panels">
            <ul class="design-notes">
              <li>材料版本与必交项校验：避免只显示“材料列表”，却无法判断是否缺失。</li>
              <li>审批意见/专家意见：办理人员需要看到上一节点结论，而不只是流转日志。</li>
              <li>可执行操作区：通过、退回、转交、暂存等操作应固定在当前任务附近。</li>
              <li>风险提示：逾期、重复退回、材料缺失、节点无人处理等需要突出提示。</li>
              <li>历史轮次：保留完整追溯能力，但默认折叠，避免挤占首屏。</li>
            </ul>
          </el-collapse-item>
        </el-collapse>
      </el-card>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowLeft, EditPen, Refresh } from '@element-plus/icons-vue'
import ProcessSteps from './components/ProcessSteps.vue'
import RecordTimeline from './components/RecordTimeline.vue'
import { processData } from './data/mockProcess'

const process = ref(processData)
const selectedNodeId = ref(process.value.currentNodeId)
const openedDetailPanels = ref(['base'])

const selectedNode = computed(() => {
  return process.value.nodes.find((node) => node.id === selectedNodeId.value) || process.value.nodes[0]
})

const summaryItems = computed(() => [
  { label: '项目编号', value: process.value.projectNo },
  { label: '负责人', value: process.value.owner },
  { label: '所属单位', value: process.value.department },
  { label: '当前节点', value: process.value.currentTask.nodeName },
  { label: '最近更新', value: process.value.updatedAt }
])

const handleSelectNode = (node) => {
  selectedNodeId.value = node.id
}
</script>
