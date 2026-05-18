const PROVIDER_LABELS: Record<string, string> = {
  zhipu: '智谱 AI',
  deepseek: 'DeepSeek',
  qwen: '通义千问',
}

/** 格式化大模型展示文案，如「通义千问 · qwen-plus」 */
export function formatLlmLabel(provider?: string | null, model?: string | null): string {
  if (!provider && !model) {
    return ''
  }
  const name = provider ? (PROVIDER_LABELS[provider] ?? provider) : ''
  if (model) {
    return name ? `${name} · ${model}` : model
  }
  return name
}
