<template>
  <el-card shadow="never" v-loading="loading">
    <div class="filter-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索文件名、标题..."
        clearable
        class="search-input"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select v-model="statusFilter" placeholder="状态筛选" clearable class="status-select">
        <el-option label="全部" value="ALL" />
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="已完成" value="READY" />
        <el-option label="失败" value="FAILED" />
      </el-select>

      <el-select v-model="sortOrder" class="sort-select">
        <el-option label="最新在前" value="newest" />
        <el-option label="最早在前" value="oldest" />
        <el-option label="题目最多" value="mostQuestions" />
        <el-option label="题目最少" value="leastQuestions" />
      </el-select>

      <el-button link type="primary" @click="loadQuizzes">刷新</el-button>
    </div>

    <el-empty v-if="!loading && filteredQuizzes.length === 0" description="暂无试卷，请先从文档生成考题" />

    <div v-else class="quiz-list">
      <QuizTaskCard
        v-for="quiz in paginatedFilteredQuizzes"
        :key="quiz.id"
        :quiz="quiz"
        :action-loading="actionLoadingId === quiz.id"
        @take="goTake(quiz.id)"
        @export="onExport(quiz.id, quiz.title)"
        @delete="onDelete(quiz)"
        @cancel="onCancel(quiz)"
        @regenerate="onRegenerate(quiz)"
      />
    </div>

    <div v-if="filteredQuizzes.length > pageSize" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="filteredQuizzes.length"
        layout="prev, pager, next"
        background
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QuizTaskCard from '@/components/QuizTaskCard.vue'
import {
  cancelQuiz,
  deleteQuiz,
  exportQuiz,
  generateQuiz,
  getQuizStatus,
  listQuizzes,
  type QuizVO,
} from '@/api/quiz'

type SortOrder = 'newest' | 'oldest' | 'mostQuestions' | 'leastQuestions'
type StatusFilter = 'ALL' | 'ACTIVE' | 'READY' | 'FAILED'

const router = useRouter()
const loading = ref(false)
const quizzes = ref<QuizVO[]>([])
const actionLoadingId = ref<number | null>(null)
const pageSize = 10
const currentPage = ref(1)
const searchQuery = ref('')
const statusFilter = ref<StatusFilter>('ALL')
const sortOrder = ref<SortOrder>('newest')

let refreshTimer: number | null = null

const hasPendingTasks = computed(() =>
  quizzes.value.some((q) => q.status === 'PENDING' || q.status === 'GENERATING'),
)

const filteredQuizzes = computed(() => {
  let result = [...quizzes.value]
  
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (q) =>
        q.title.toLowerCase().includes(query) ||
        q.documentName.toLowerCase().includes(query),
    )
  }
  
  if (statusFilter.value !== 'ALL') {
    if (statusFilter.value === 'ACTIVE') {
      result = result.filter((q) => q.status === 'PENDING' || q.status === 'GENERATING')
    } else if (statusFilter.value === 'READY') {
      result = result.filter((q) => q.status === 'READY')
    } else if (statusFilter.value === 'FAILED') {
      result = result.filter((q) => q.status === 'FAILED' || q.status === 'CANCELLED')
    }
  }
  
  result.sort((a, b) => {
    switch (sortOrder.value) {
      case 'newest':
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      case 'oldest':
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      case 'mostQuestions':
        return b.questionCount - a.questionCount
      case 'leastQuestions':
        return a.questionCount - b.questionCount
      default:
        return 0
    }
  })
  
  return result
})

const paginatedFilteredQuizzes = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredQuizzes.value.slice(start, start + pageSize)
})

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
    .filter((q) => q.status === 'PENDING' || q.status === 'GENERATING')
    .map((q) => q.id)

  for (const id of pendingIds) {
    try {
      const updated = await getQuizStatus(id)
      const index = quizzes.value.findIndex((q) => q.id === id)
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

function goTake(id: number) {
  router.push(`/quizzes/${id}/take`)
}

async function onExport(id: number, title: string) {
  try {
    const response = await exportQuiz(id)
    const contentType = response.headers['content-type']
    
    if (contentType?.includes('application/json')) {
      const errorData = response.data
      ElMessage.error(errorData.message || '下载失败，请重试')
      return
    }
    
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
  } catch (error: any) {
    if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else {
      ElMessage.error('下载失败，请重试')
    }
  }
}

async function onRegenerate(row: QuizVO) {
  actionLoadingId.value = row.id
  try {
    await deleteQuiz(row.id)
    await generateQuiz(row.documentId, row.questionCount)
    ElMessage.success('已重新提交生成任务')
    await loadQuizzes()
    startAutoRefresh()
  } catch {
    ElMessage.error('重试失败，请重试')
  } finally {
    actionLoadingId.value = null
  }
}

async function onCancel(row: QuizVO) {
  await ElMessageBox.confirm(`确定取消任务「${row.title}」？`, '取消确认', { type: 'warning' })
  actionLoadingId.value = row.id
  try {
    await cancelQuiz(row.id)
    ElMessage.success('任务已取消')
    await loadQuizzes()
  } finally {
    actionLoadingId.value = null
  }
}

async function onDelete(row: QuizVO) {
  await ElMessageBox.confirm(`确定删除「${row.title}」？`, '删除确认', { type: 'warning' })
  await deleteQuiz(row.id)
  ElMessage.success('已删除')
  await loadQuizzes()
}

onMounted(async () => {
  await loadQuizzes()
  startAutoRefresh()
})

onUnmounted(stopAutoRefresh)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-input {
  width: 300px;
}

.status-select {
  width: 150px;
}

.sort-select {
  width: 150px;
}

.quiz-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
