import axios from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: '/api',
  timeout: 300000,
})

request.interceptors.response.use(
  (response) => {
    const contentType = response.headers['content-type']
    if (contentType?.includes('application/json')) {
      const result = response.data as ApiResult
      if (result.code !== 0) {
        ElMessage.error(result.message || '请求失败')
        return Promise.reject(new Error(result.message))
      }
    }
    return response
  },
  (error) => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  },
)

export async function getData<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const res = await promise
  return res.data.data
}

export default request
