<template>
  <el-card shadow="never">
    <template #header>
      <span>欢迎使用 AI 智能学习平台</span>
    </template>
    <p class="desc">
      上传 PDF 学习资料，系统将自动解析内容并生成选择题，助你高效复习。
    </p>
    <el-space wrap>
      <el-button type="primary" @click="$router.push('/documents/upload')">
        <el-icon><Upload /></el-icon>
        上传 PDF
      </el-button>
      <el-button @click="$router.push('/documents')">
        <el-icon><Document /></el-icon>
        学习文档
      </el-button>
      <el-button @click="$router.push('/quizzes')">
        <el-icon><EditPen /></el-icon>
        试卷题库
      </el-button>
    </el-space>

    <el-divider />

    <el-descriptions title="系统状态" :column="1" border v-loading="llmLoading">
      <el-descriptions-item label="后端">
        <el-tag v-if="appStore.backendOnline === true" type="success" size="small">已连接</el-tag>
        <el-tag v-else-if="appStore.backendOnline === false" type="danger" size="small">未连接</el-tag>
        <el-tag v-else type="info" size="small">检测中</el-tag>
      </el-descriptions-item>
      <el-descriptions-item v-if="llmConfig" label="大模型">
        {{ llmProviderLabel(llmConfig.provider) }} · {{ llmConfig.model }}
      </el-descriptions-item>
    </el-descriptions>

    <el-divider />

    <el-descriptions title="功能模块" :column="1" border>
      <el-descriptions-item label="文档">PDF 上传、解析、按页预览</el-descriptions-item>
      <el-descriptions-item label="出题">智谱 / DeepSeek 多模型，可选难度与题数</el-descriptions-item>
      <el-descriptions-item label="练习">在线答题、成绩统计、错题解析与 PDF 定位</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { getLlmConfig, type LlmConfigVO } from '@/api/llm'

const appStore = useAppStore()
const llmLoading = ref(false)
const llmConfig = ref<LlmConfigVO | null>(null)

function llmProviderLabel(provider: string) {
  const map: Record<string, string> = {
    zhipu: '智谱 AI',
    deepseek: 'DeepSeek',
  }
  return map[provider] || provider
}

async function loadLlmConfig() {
  if (appStore.backendOnline !== true) return
  llmLoading.value = true
  try {
    llmConfig.value = await getLlmConfig()
  } catch {
    llmConfig.value = null
  } finally {
    llmLoading.value = false
  }
}

onMounted(async () => {
  await appStore.checkBackend()
  await loadLlmConfig()
})
</script>

<style scoped>
.desc {
  color: #606266;
  margin-bottom: 20px;
  line-height: 1.6;
}
</style>
