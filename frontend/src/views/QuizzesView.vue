<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <span>考题生成任务队列</span>
      <el-button link type="primary" @click="loadQuizzes" style="float: right;">
        刷新
      </el-button>
    </template>

    <el-empty v-if="!loading && quizzes.length === 0" description="暂无生成任务，请先从文档生成考题" />

    <div v-else class="task-list">
      <el-card 
        v-for="quiz in quizzes" 
        :key="quiz.id" 
        class="task-card"
        :class="{ 'task-card-generating': quiz.status === 'GENERATING' }"
      >
        <div class="task-header">
          <div class="task-title">
            <span class="title-text">{{ quiz.title }}</span>
            <el-tag :type="quizStatusType(quiz.status)" size="small" class="status-tag">
              {{ quizStatusLabel(quiz.status) }}
            </el-tag>
          </div>
          <div class="task-meta">
            <span class="meta-item">📄 {{ quiz.documentName }}</span>
            <span class="meta-item">📝 {{ quiz.questionCount }} 题</span>
            <span v-if="llmLabel(quiz)" class="meta-item llm-meta">
              🤖 由 {{ llmLabel(quiz) }} 生成
            </span>
          </div>
        </div>

        <div v-if="quiz.status === 'GENERATING' || quiz.status === 'PENDING'" class="progress-section">
          <div class="progress-bar-wrapper">
            <el-progress 
              :percentage="quiz.progress || 0" 
              :stroke-width="6"
              :show-text="true"
              text-inside
              status="active"
            />
          </div>
          <div class="progress-info">
            <span v-if="quiz.status === 'GENERATING'">正在生成题目...</span>
            <span v-else>排队等待中...</span>
            <span v-if="quiz.estimatedCompletionTime" class="estimate-time">
              预计完成: {{ formatTime(quiz.estimatedCompletionTime) }}
            </span>
          </div>
        </div>

        <div v-if="quiz.status === 'FAILED'" class="error-section">
          <el-alert 
            type="error" 
            :title="'生成失败: ' + (quiz.errorMessage || '未知错误')" 
            show-icon 
            closable 
          />
        </div>

        <div class="task-footer">
          <div class="task-time">
            <span>创建时间: {{ formatTime(quiz.createdAt) }}</span>
          </div>
          <div class="task-actions">
            <el-button 
              v-if="quiz.status === 'READY'"
              link 
              type="primary" 
              @click="goDetail(quiz.id)"
            >
              查看详情
            </el-button>
            <el-button 
              v-if="quiz.status === 'READY'"
              link 
              type="success" 
              @click="onExport(quiz.id, quiz.title)"
            >
              下载考题
            </el-button>
            <el-button 
              v-if="quiz.status === 'READY'"
              link 
              type="primary" 
              @click="goTake(quiz.id)"
            >
              开始答题
            </el-button>
            <el-button 
              v-if="quiz.status === 'FAILED'"
              link 
              type="warning" 
              :loading="regeneratingId === quiz.id"
              @click="onRegenerate(quiz)"
            >
              重试
            </el-button>
            <el-button 
              v-if="quiz.status === 'PENDING' || quiz.status === 'GENERATING'"
              link 
              type="danger" 
              :loading="cancellingId === quiz.id"
              @click="onCancel(quiz)"
            >
              取消
            </el-button>
            <el-button 
              v-if="quiz.status !== 'GENERATING'"
              link 
              type="danger" 
              @click="onDelete(quiz)"
            >
              删除
            </el-button>
          </div>
        </div>
      </el-card>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelQuiz,
  deleteQuiz,
  exportQuiz,
  generateQuiz,
  getQuizStatus,
  listQuizzes,
  quizStatusLabel,
  quizStatusType,
  type QuizVO,
} from '@/api/quiz'
import { formatLlmLabel } from '@/utils/llm'

const router = useRouter()

function llmLabel(quiz: QuizVO) {
  return formatLlmLabel(quiz.llmProvider, quiz.llmModel)
}
const loading = ref(false)
const quizzes = ref<QuizVO[]>([])
const regeneratingId = ref<number | null>(null)
const cancellingId = ref<number | null>(null)

let refreshTimer: number | null = null

const hasPendingTasks = computed(() => {
  return quizzes.value.some(q => q.status === 'PENDING' || q.status === 'GENERATING')
})

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

async function loadQuizzes() {
  loading.value = true
  try {
    quizzes.value = await listQuizzes()
  } finally {
    loading.value = false
  }
}

async function refreshPendingQuizzes() {
  const pendingIds = quizzes.value
    .filter(q => q.status === 'PENDING' || q.status === 'GENERATING')
    .map(q => q.id)

  for (const id of pendingIds) {
    try {
      const updated = await getQuizStatus(id)
      const index = quizzes.value.findIndex(q => q.id === id)
      if (index !== -1) {
        quizzes.value[index] = updated
      }
    } catch (e) {
      console.error('Failed to refresh quiz status:', e)
    }
  }
}

function startAutoRefresh() {
  if (refreshTimer) return
  refreshTimer = window.setInterval(() => {
    if (hasPendingTasks.value) {
      refreshPendingQuizzes()
    } else {
      stopAutoRefresh()
    }
  }, 2000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function goDetail(id: number) {
  router.push(`/quizzes/${id}/take`)
}

function goTake(id: number) {
  router.push(`/quizzes/${id}/take`)
}

async function onExport(id: number, title: string) {
  try {
    const response = await exportQuiz(id)
    const blob = response.data
    const filename = title.replaceAll(/[^a-zA-Z0-9\u4e00-\u9fa5\-_\.]/g, '_') + '.md'
    
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('下载成功')
  } catch (error) {
    ElMessage.error('下载失败，请重试')
  }
}

async function onRegenerate(row: QuizVO) {
  regeneratingId.value = row.id
  try {
    await deleteQuiz(row.id)
    const quiz = await generateQuiz(row.documentId, row.questionCount)
    ElMessage.success('已重新提交生成任务')
    await loadQuizzes()
    startAutoRefresh()
  } catch (error) {
    ElMessage.error('重试失败，请重试')
  } finally {
    regeneratingId.value = null
  }
}

async function onCancel(row: QuizVO) {
  await ElMessageBox.confirm(`确定取消任务「${row.title}」？`, '取消确认', { type: 'warning' })
  cancellingId.value = row.id
  try {
    await cancelQuiz(row.id)
    ElMessage.success('任务已取消')
    await loadQuizzes()
  } finally {
    cancellingId.value = null
  }
}

async function onDelete(row: QuizVO) {
  await ElMessageBox.confirm(`确定删除任务「${row.title}」？`, '删除确认', { type: 'warning' })
  await deleteQuiz(row.id)
  ElMessage.success('已删除')
  await loadQuizzes()
}

onMounted(async () => {
  await loadQuizzes()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.task-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-card {
  border-radius: 8px;
  transition: all 0.3s ease;
}

.task-card-generating {
  border-left: 4px solid #e6a23c;
}

.task-header {
  margin-bottom: 16px;
}

.task-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.title-text {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.status-tag {
  flex-shrink: 0;
}

.task-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #606266;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.llm-meta {
  color: #409eff;
}

.progress-section {
  margin-bottom: 16px;
}

.progress-bar-wrapper {
  margin-bottom: 8px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.estimate-time {
  color: #909399;
  font-size: 12px;
}

.error-section {
  margin-bottom: 16px;
}

.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.task-time {
  font-size: 12px;
  color: #909399;
}

.task-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 768px) {
  .task-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .task-actions {
    flex-wrap: wrap;
  }
  
  .task-meta {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
