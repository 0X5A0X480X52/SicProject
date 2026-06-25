<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import { listProjectMaterials, uploadMaterialVersion } from '../../api/materials'
import type { MaterialContextView, MaterialRequirementView } from '../../types/nodeForms'

const props = defineProps<{
  projectId: number
  requirements: MaterialRequirementView[]
  selectedIds?: number[]
}>()
const emit = defineEmits<{
  'update:selectedIds': [ids: number[]]
  'materials-changed': []
}>()

const loading = ref(false)
const uploadingCode = ref('')
const materials = ref<MaterialContextView[]>([])

const selectedIds = computed(() => props.selectedIds ?? [])

const materialsByType = computed(() => {
  const map = new Map<string, MaterialContextView[]>()
  for (const item of materials.value) {
    const list = map.get(item.materialTypeCode) ?? []
    list.push(item)
    map.set(item.materialTypeCode, list)
  }
  for (const list of map.values()) {
    list.sort((a, b) => Number(b.isCurrent) - Number(a.isCurrent) || b.versionNo - a.versionNo)
  }
  return map
})

function materialVersionIdsForType(materialTypeCode?: string) {
  if (!materialTypeCode) return new Set<number>()
  return new Set((materialsByType.value.get(materialTypeCode) ?? []).map((item) => item.materialVersionId))
}

function currentMaterialVersionIdsForType(materialTypeCode?: string) {
  if (!materialTypeCode) return new Set<number>()
  return new Set((materialsByType.value.get(materialTypeCode) ?? [])
    .filter((item) => item.isCurrent)
    .map((item) => item.materialVersionId))
}

function selectedIdsFor(requirement: MaterialRequirementView) {
  const idsForType = currentMaterialVersionIdsForType(requirement.materialTypeCode)
  return selectedIds.value.filter((id) => idsForType.has(id))
}

function updateSelectedFor(requirement: MaterialRequirementView, value: unknown) {
  const nextForType = Array.isArray(value) ? value.map(Number).filter(Number.isFinite) : []
  const idsForType = materialVersionIdsForType(requirement.materialTypeCode)
  const keptOtherTypes = selectedIds.value.filter((id) => !idsForType.has(id))
  emit('update:selectedIds', Array.from(new Set([...keptOtherTypes, ...nextForType])))
}

async function loadMaterials() {
  loading.value = true
  try {
    materials.value = await listProjectMaterials(props.projectId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '材料加载失败')
  } finally {
    loading.value = false
  }
}

async function upload(requirement: MaterialRequirementView, file: File) {
  if (!requirement.materialTypeCode) return false
  uploadingCode.value = requirement.materialTypeCode
  try {
    const response = await uploadMaterialVersion(props.projectId, requirement.materialTypeCode, file)
    await loadMaterials()
    const idsForType = materialVersionIdsForType(requirement.materialTypeCode)
    const keptOtherTypes = selectedIds.value.filter((id) => !idsForType.has(id))
    emit('update:selectedIds', Array.from(new Set([...keptOtherTypes, response.context.materialVersionId])))
    emit('materials-changed')
    ElMessage.success('材料已上传并选中新版本')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '材料上传失败')
  } finally {
    uploadingCode.value = ''
  }
  return false
}

watch(() => props.projectId, loadMaterials)

onMounted(loadMaterials)
</script>

<template>
  <el-card class="workflow-card" shadow="never">
    <template #header>材料附件</template>
    <el-empty v-if="!requirements.length" description="当前节点暂无材料要求" />
    <el-table v-else v-loading="loading" :data="requirements" size="small" border>
      <el-table-column prop="materialTypeName" label="材料" min-width="150">
        <template #default="{ row }">
          <div class="workflow-material-name-cell">
            <strong>{{ row.materialTypeName || row.materialTypeCode || '-' }}</strong>
            <span v-if="row.description">{{ row.description }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="要求" width="82" align="center">
        <template #default="{ row }">
          <el-tag :type="row.required ? 'danger' : 'info'" size="small">{{ row.required ? '必填' : '选填' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="选择材料版本" min-width="220">
        <template #default="{ row }">
          <el-select
            :model-value="selectedIdsFor(row)"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择版本"
            style="width: 100%"
            @update:model-value="updateSelectedFor(row, $event)"
          >
            <el-option
              v-for="item in materialsByType.get(row.materialTypeCode) || []"
              :key="item.materialVersionId"
              :label="`${item.fileName} #${item.versionNo}${item.isCurrent ? '（当前）' : '（旧版本不可提交）'}`"
              :value="item.materialVersionId"
              :disabled="!item.isCurrent"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="上传" width="110">
        <template #default="{ row }">
          <el-upload :show-file-list="false" :before-upload="(file: UploadRawFile) => upload(row, file)">
            <el-button size="small" :loading="uploadingCode === row.materialTypeCode">上传</el-button>
          </el-upload>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
