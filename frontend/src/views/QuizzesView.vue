<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <div class="page-header">
        <span>试卷题库</span>
        <div class="header-actions">
          <el-radio-group v-model="viewMode" size="small" @change="onViewModeChange">
            <el-radio-button value="document">按文件名</el-radio-button>
            <el-radio-button value="page">按 PDF 页码</el-radio-button>
          </el-radio-group>
          <el-button link type="primary" @click="loadQuizzes">刷新</el-button>
        </div>
      </div>
    </template>

    <el-empty v-if="!loading && quizzes.length === 0" description="暂无试卷，请先从文档生成考题" />

    <!-- 按文件名分组 -->
    <template v-else-if="viewMode === 'document'">
      <el-collapse v-model="expandedDocs" class="doc-collapse">
        <el-collapse-item
          v-for="group in documentGroups"
          :key="group.documentId"
          :name="group.documentId"
        >
          <template #title>
            <span class="group-title">
              <el-icon><Document /></el-icon>
              {{ group.documentName }}
              <el-tag size="small" type="info" class="count-tag">{{ group.quizzes.length }} 份</el-tag>
              <span v-if="group.pageCount" class="page-count-hint">共 {{ group.pageCount }} 页</span>
            </span>
          </template>
          <QuizTaskCard
            v-for="quiz in paginatedQuizzes(group.quizzes, group.documentId)"
            :key="quiz.id"
            :quiz="quiz"
            :action-loading="actionLoadingId === quiz.id"
            @take="goTake(quiz.id)"
            @export="onExport(quiz.id, quiz.title)"
            @delete="onDelete(quiz)"
            @cancel="onCancel(quiz)"
            @regenerate="onRegenerate(quiz)"
          />
          <el-pagination
            v-if="group.quizzes.length > pageSize"
            class="group-pagination"
            small
            layout="prev, pager, next"
            :total="group.quizzes.length"
            :page-size="pageSize"
            :current-page="docPageMap[group.documentId] || 1"
            @current-change="(p: number) => setDocPage(group.documentId, p)"
          />
        </el-collapse-item>
      </el-collapse>
    </template>

    <!-- 按 PDF 页码分组 -->
    <template v-else>
      <div v-if="pageViewLoading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        正在分析题目页码…
      </div>
      <el-collapse v-else v-model="expandedDocs" class="doc-collapse">
        <el-collapse-item
          v-for="docGroup in pageViewByDocument"
          :key="docGroup.documentId"
          :name="docGroup.documentId"
        >
          <template #title>
            <span class="group-title">
              <el-icon><Document /></el-icon>
              {{ docGroup.documentName }}
              <el-tag size="small" type="info" class="count-tag">
                {{ docGroup.pageSections.length }} 个页码分组
              </el-tag>
            </span>
          </template>

          <div v-if="docGroup.activeQuizzes.length" class="subsection">
            <div class="subsection-title">进行中的任务</div>
            <QuizTaskCard
              v-for="quiz in docGroup.activeQuizzes"
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

          <div
            v-for="section in docGroup.pageSections"
            :key="`${docGroup.documentId}-p-${section.page}`"
            class="subsection"
          >
            <div class="subsection-title">
              {{ section.page > 0 ? `第 ${section.page} 页` : '未标注页码' }}
              <el-tag size="small" type="success">{{ section.quizzes.length }} 份试卷</el-tag>
            </div>
            <QuizTaskCard
              v-for="quiz in section.quizzes"
              :key="`${quiz.id}-${section.page}`"
              :quiz="quiz"
              :page-hint="`本题集含第 ${formatPageList(quizPageMap.get(quiz.id))} 页`"
              :action-loading="actionLoadingId === quiz.id"
              @take="goTake(quiz.id)"
              @export="onExport(quiz.id, quiz.title)"
              @delete="onDelete(quiz)"
              @cancel="onCancel(quiz)"
              @regenerate="onRegenerate(quiz)"
            />
          </div>

          <el-empty
            v-if="!docGroup.activeQuizzes.length && !docGroup.pageSections.length"
            description="该文档暂无已完成的试卷"
            :image-size="60"
          />
        </el-collapse-item>
      </el-collapse>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QuizTaskCard from '@/components/QuizTaskCard.vue'
import { listDocuments, type DocumentVO } from '@/api/document'
import {
  cancelQuiz,
  deleteQuiz,
  exportQuiz,
  generateQuiz,
  getQuiz,
  getQuizStatus,
  listQuizzes,
  type QuizVO,
} from '@/api/quiz'

type ViewMode = 'document' | 'page'

interface DocumentGroup {
  documentId: number
  documentName: string
  pageCount: number | null
  quizzes: QuizVO[]
}

interface PageSection {
  page: number
  quizzes: QuizVO[]
}

const router = useRouter()
const loading = ref(false)
const pageViewLoading = ref(false)
const viewMode = ref<ViewMode>('document')
const quizzes = ref<QuizVO[]>([])
const documents = ref<DocumentVO[]>([])
const quizPageMap = ref<Map<number, Set<number>>>(new Map())
const expandedDocs = ref<number[]>([])
const actionLoadingId = ref<number | null>(null)
const pageSize = 5
const docPageMap = reactive<Record<number, number>>({})

let refreshTimer: number | null = null

const hasPendingTasks = computed(() =>
  quizzes.value.some((q) => q.status === 'PENDING' || q.status === 'GENERATING'),
)

const documentGroups = computed<DocumentGroup[]>(() => {
  const docById = new Map(documents.value.map((d) => [d.id, d]))
  const map = new Map<number, QuizVO[]>()

  for (const q of quizzes.value) {
    if (!map.has(q.documentId)) {
      map.set(q.documentId, [])
    }
    map.get(q.documentId)!.push(q)
  }

  return Array.from(map.entries())
    .map(([documentId, items]) => {
      const doc = docById.get(documentId)
      return {
        documentId,
        documentName: doc?.fileName ?? items[0]?.documentName ?? `文档 #${documentId}`,
        pageCount: doc?.pageCount ?? null,
        quizzes: [...items].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
        ),
      }
    })
    .sort((a, b) => a.documentName.localeCompare(b.documentName, 'zh-CN'))
})

const pageViewByDocument = computed(() => {
  return documentGroups.value.map((dg) => {
    const activeQuizzes = dg.quizzes.filter(
      (q) => q.status === 'PENDING' || q.status === 'GENERATING' || q.status === 'FAILED',
    )

    const pageToQuizzes = new Map<number, QuizVO[]>()
    for (const q of dg.quizzes.filter((x) => x.status === 'READY')) {
      const pages = quizPageMap.value.get(q.id)
      if (!pages?.size) {
        const fallback = 0
        if (!pageToQuizzes.has(fallback)) {
          pageToQuizzes.set(fallback, [])
        }
        pageToQuizzes.get(fallback)!.push(q)
        continue
      }
      for (const page of pages) {
        if (!pageToQuizzes.has(page)) {
          pageToQuizzes.set(page, [])
        }
        if (!pageToQuizzes.get(page)!.some((x) => x.id === q.id)) {
          pageToQuizzes.get(page)!.push(q)
        }
      }
    }

    const pageSections: PageSection[] = Array.from(pageToQuizzes.entries())
      .filter(([page]) => page > 0)
      .sort(([a], [b]) => a - b)
      .map(([page, items]) => ({ page, quizzes: items }))

    const unassigned = pageToQuizzes.get(0)
    if (unassigned?.length) {
      pageSections.push({ page: 0, quizzes: unassigned })
    }

    return {
      ...dg,
      activeQuizzes,
      pageSections,
    }
  })
})

function paginatedQuizzes(items: QuizVO[], documentId: number) {
  const page = docPageMap[documentId] || 1
  const start = (page - 1) * pageSize
  return items.slice(start, start + pageSize)
}

function setDocPage(documentId: number, page: number) {
  docPageMap[documentId] = page
}

function formatPageList(pages: Set<number> | undefined) {
  if (!pages?.size) return '-'
  return [...pages].sort((a, b) => a - b).join('、')
}

async function enrichQuizPages() {
  const ready = quizzes.value.filter((q) => q.status === 'READY')
  if (!ready.length) {
    quizPageMap.value = new Map()
    return
  }
  pageViewLoading.value = true
  try {
    const map = new Map<number, Set<number>>()
    await Promise.all(
      ready.map(async (q) => {
        try {
          const detail = await getQuiz(q.id, false)
          const pages = new Set(
            (detail.questions ?? [])
              .map((x) => x.sourcePage)
              .filter((p): p is number => p != null && p > 0),
          )
          map.set(q.id, pages)
        } catch {
          map.set(q.id, new Set())
        }
      }),
    )
    quizPageMap.value = map
  } finally {
    pageViewLoading.value = false
  }
}

function syncExpandedDocs() {
  expandedDocs.value = documentGroups.value.map((g) => g.documentId)
}

async function loadQuizzes() {
  loading.value = true
  try {
    const [quizList, docList] = await Promise.all([listQuizzes(), listDocuments()])
    quizzes.value = quizList
    documents.value = docList
    syncExpandedDocs()
    if (viewMode.value === 'page') {
      await enrichQuizPages()
    }
  } finally {
    loading.value = false
  }
}

async function onViewModeChange() {
  if (viewMode.value === 'page') {
    await enrichQuizPages()
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

  if (viewMode.value === 'page') {
    const becameReady = pendingIds.some((id) => {
      const q = quizzes.value.find((x) => x.id === id)
      return q?.status === 'READY'
    })
    if (becameReady) {
      await enrichQuizPages()
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

watch(documentGroups, syncExpandedDocs, { deep: true })

onMounted(async () => {
  await loadQuizzes()
  startAutoRefresh()
})

onUnmounted(stopAutoRefresh)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.doc-collapse {
  border: none;
}
.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}
.count-tag {
  margin-left: 4px;
}
.page-count-hint {
  font-size: 12px;
  font-weight: normal;
  color: #909399;
}
.group-pagination {
  margin-top: 8px;
  justify-content: flex-end;
}
.subsection {
  margin-bottom: 20px;
}
.subsection-title {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #909399;
}
</style>
