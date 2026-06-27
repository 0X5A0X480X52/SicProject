<script setup lang="ts">
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
