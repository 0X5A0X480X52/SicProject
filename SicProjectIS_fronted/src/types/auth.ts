export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
}

export interface CurrentUser {
  userId: number
  username: string
  realName: string
  deptId: number | null
  deptName: string | null
  roleCodes: string[]
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
