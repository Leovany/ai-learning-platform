import request, { getData } from '@/api/request'

export interface LlmConfigVO {
  provider: string
  model: string
  apiBase: string
}

export function getLlmConfig() {
  return getData<LlmConfigVO>(request.get('/llm/config'))
}
