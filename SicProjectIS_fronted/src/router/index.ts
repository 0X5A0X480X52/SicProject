import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import DashboardView from '../views/DashboardView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import ProjectAuthorizationView from '../views/ProjectAuthorizationView.vue'
import AdminOverviewView from '../views/AdminOverviewView.vue'
import UserRoleManagementView from '../views/UserRoleManagementView.vue'
import RolePermissionMatrixView from '../views/RolePermissionMatrixView.vue'
import AdminProjectAuthorizationsView from '../views/AdminProjectAuthorizationsView.vue'
import AuditLogsView from '../views/AuditLogsView.vue'
import NodeFormsDebugView from '../features/node-forms/common/NodeFormsDebugView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/dashboard', name: 'dashboard', component: DashboardView },
    { path: '/projects', name: 'projects', component: ProjectsView },
    {
      path: '/projects/:projectId/authorization',
      name: 'project-authorization',
      component: ProjectAuthorizationView,
    },
    { path: '/admin/overview', name: 'admin-overview', component: AdminOverviewView },
    { path: '/admin/users', name: 'admin-users', component: UserRoleManagementView },
    { path: '/admin/roles-permissions', name: 'admin-roles-permissions', component: RolePermissionMatrixView },
    { path: '/admin/project-authorizations', name: 'admin-project-authorizations', component: AdminProjectAuthorizationsView },
    { path: '/admin/audit-logs', name: 'admin-audit-logs', component: AuditLogsView },
    { path: '/node-forms/debug', name: 'node-forms-debug', component: NodeFormsDebugView },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }
  return true
})
