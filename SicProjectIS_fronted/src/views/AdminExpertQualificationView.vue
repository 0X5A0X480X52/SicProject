<script setup lang="ts">
/**
 * 科研或系统管理员的专家资格审批页，按当前审批阶段提交结果并刷新记录；审批成功后新身份需重新登录获取。
 */
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import ExpertQualificationReviewModule from '../components/expert-qualification/ExpertQualificationReviewModule.vue'
</script>

<template>
  <PermissionWorkbenchLayout>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">专家资格</p>
          <h2 class="section-heading">资格审核</h2>
        </div>
      </section>

      <ExpertQualificationReviewModule />
    </div>
  </PermissionWorkbenchLayout>
</template>
