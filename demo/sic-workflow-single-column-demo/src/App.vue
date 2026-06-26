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
            <h3>{{ selectedNode.name }}</h3>
            <p>{{ selectedNode.desc }}</p>
          </div>
          <div class="selected-meta">
            <div>
              <span>状态</span>
              <el-tag :type="nodeTagType(selectedNode.status)" effect="light">
                {{ selectedNode.statusText }}
              </el-tag>
            </div>
            <div>
              <span>责任角色</span>
              <strong>{{ selectedNode.actor || '-' }}</strong>
            </div>
            <div>
              <span>完成时间</span>
              <strong>{{ selectedNode.finishedAt || '尚未完成' }}</strong>
            </div>
            <div>
              <span>处理次数</span>
              <strong>{{ selectedNode.roundCount || 0 }} 次</strong>
            </div>
          </div>
        </div>
      </el-card>

      <section class="two-column-row">
        <el-card shadow="never" class="section-card current-task-card">
          <template #header>
            <div class="card-title-row compact">
              <h2>当前办理任务</h2>
              <el-tag type="primary">处理中</el-tag>
            </div>
          </template>

          <div class="task-title">
            <h3>{{ process.currentTask.nodeName }}</h3>
            <p>{{ process.currentTask.instruction }}</p>
          </div>

          <div class="task-grid">
            <div>
              <span>责任人</span>
              <strong>{{ process.currentTask.assignee }}</strong>
            </div>
            <div>
              <span>角色</span>
              <strong>{{ process.currentTask.roleName }}</strong>
            </div>
            <div>
              <span>办理模式</span>
              <strong>{{ process.currentTask.mode }}</strong>
            </div>
            <div>
              <span>截止时间</span>
              <strong>{{ process.currentTask.deadline }}</strong>
            </div>
          </div>

          <div class="task-actions">
            <el-button type="primary">进入办理</el-button>
            <el-button>补充备注</el-button>
            <el-button type="warning" plain>退回补正</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="section-card material-card">
          <template #header>
            <div class="card-title-row compact">
              <h2>关键材料</h2>
              <el-button text type="primary">查看全部</el-button>
            </div>
          </template>

          <div class="material-list">
            <div v-for="material in process.materials" :key="material.id" class="material-item">
              <div>
                <strong>{{ material.name }}</strong>
                <span>{{ material.type }} · {{ material.updatedAt }}</span>
              </div>
              <el-tag size="small" type="success" effect="light">{{ material.status }}</el-tag>
            </div>
          </div>
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
          <el-collapse-item title="页面设计说明" name="design">
            <ul class="design-notes">
              <li>项目摘要从右侧栏移动到顶部信息条，避免主流程被切断。</li>
              <li>本轮流转记录单独成块，便于办理人员快速判断当前状态。</li>
              <li>历史轮次默认折叠，保留完整追溯能力但不占据首屏。</li>
              <li>BPMN 作为辅助查看，不再长期占据主视觉区域。</li>
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

const nodeTagType = (status) => {
  const map = {
    done: 'success',
    current: 'primary',
    waiting: 'info',
    returned: 'warning',
    rejected: 'danger'
  }
  return map[status] || 'info'
}
</script>
