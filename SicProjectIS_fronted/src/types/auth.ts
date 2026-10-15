/**
 * 描述注册登录、当前身份和权限码结构；部门可为空，角色变更后需重新登录取得新的 token 与显示身份。
 */
export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
}

export interface CurrentUser {
  userId: number
  username: string
  realName: string
  /** 新账号可为空；申请资格还须由后端读取当前启用部门判断。 */
  deptId: number | null
  deptName: string | null
  roleCodes: string[]
  /** 全局能力码，不包含某个项目 grant 是否匹配当前任务的最终结论。 */
  permissionCodes: string[]
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: CurrentUser
}

export interface RegisterRequest {
  username: string
  password: string
  realName: string
  deptId: number | null
  phone: string
  email: string
}
