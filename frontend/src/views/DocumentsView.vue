<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>学习文档</span>
        <el-button type="primary" size="small" @click="$router.push('/documents/upload')">
          上传 PDF
        </el-button>
      </div>
    </template>

    <el-empty v-if="!loading && documents.length === 0" description="暂无文档，请先上传 PDF" />

    <el-table v-else :data="documents" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="pageCount" label="页数" width="80">
        <template #default="{ row }">{{ row.pageCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            link
            type="primary"
            :disabled="row.status !== 'PARSED'"
            @click="openGenerate(row)"
          >
            生成考题
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawerVisible" title="文档详情" size="50%">
      <template v-if="currentDoc">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="文件名">{{ currentDoc.fileName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(currentDoc.status)" size="small">
              {{ statusLabel(currentDoc.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="页数">{{ currentDoc.pageCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatFileSize(currentDoc.fileSize) }}</el-descriptions-item>
        </el-descriptions>
        <el-button
          type="warning"
          plain
          size="small"
          :loading="reparsing"
          style="margin-bottom: 12px"
          @click="onReparse"
        >
          重新解析（补全分页页码）
        </el-button>
        <el-divider content-position="left">按页预览（带页码）</el-divider>
        <template v-if="currentDoc.pageCount && currentDoc.pageCount > 0">
          <div class="page-toolbar">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="1"
              :total="currentDoc.pageCount"
              layout="total, prev, pager, next, jumper"
              small
              @current-change="onPageChange"
            />
          </div>
          <el-input
            v-model="currentPageText"
            v-loading="pageLoading"
            type="textarea"
            :rows="12"
            readonly
            :placeholder="`第 ${currentPage} 页内容`"
          />
        </template>
        <el-empty v-else description="暂无分页数据，请点击「重新解析」" :image-size="60" />
      </template>
    </el-drawer>

    <el-dialog v-model="generateVisible" title="生成考题" width="420px" :close-on-click-modal="!generating">
      <p class="gen-hint">文档：{{ generateDoc?.fileName }}</p>
      <el-form label-width="100px">
        <el-form-item label="题目数量">
          <el-input-number v-model="questionCount" :min="1" :max="quizConfig?.maxCount ?? 50" />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="difficulty" style="width: 100%">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
      </el-form>
      <p v-if="plannedLlmLabel" class="gen-tip">
        将按配置调用大模型（失败时自动切换）：当前优先 <strong>{{ plannedLlmLabel }}</strong>
      </p>
      <p v-else class="gen-tip">将调用大模型生成选择题，请确保已在 .env 中配置 API Key</p>
      <template #footer>
        <el-button :disabled="generating" @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="confirmGenerate">开始生成</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteDocument,
  formatFileSize,
  getDocument,
  getDocumentPage,
  listDocuments,
  reparseDocument,
  statusLabel,
  statusType,
  type DocumentVO,
} from '@/api/document'
import { generateQuiz, getQuizConfig, type QuizDifficulty, type QuizConfigVO } from '@/api/quiz'
import { getLlmConfig } from '@/api/llm'
import { formatLlmLabel } from '@/utils/llm'

const router = useRouter()
const loading = ref(false)
const documents = ref<DocumentVO[]>([])
const drawerVisible = ref(false)
const currentDoc = ref<DocumentVO | null>(null)
const currentPage = ref(1)
const currentPageText = ref('')
const pageLoading = ref(false)
const pageCache = ref<Record<number, string>>({})
const reparsing = ref(false)

const generateVisible = ref(false)
const generateDoc = ref<DocumentVO | null>(null)
const questionCount = ref(10)
const difficulty = ref<QuizDifficulty>('medium')
const generating = ref(false)
const quizConfig = ref<QuizConfigVO | null>(null)
const plannedLlmLabel = ref('')

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = await listDocuments()
  } finally {
    loading.value = false
  }
}

async function loadQuizConfig() {
  try {
    quizConfig.value = await getQuizConfig()
  } catch (error) {
    console.error('Failed to load quiz config:', error)
  }
}

async function openDetail(row: DocumentVO) {
  drawerVisible.value = true
  currentDoc.value = await getDocument(row.id)
  pageCache.value = {}
  currentPage.value = 1
  await loadPageContent(1)
}

async function loadPageContent(page: number) {
  if (!currentDoc.value?.pageCount || page < 1 || page > currentDoc.value.pageCount) return
  if (pageCache.value[page]) {
    currentPageText.value = pageCache.value[page]
    return
  }
  pageLoading.value = true
  try {
    const pageVo = await getDocumentPage(currentDoc.value.id, page)
    const text = pageVo.text || '（该页无文本，可能为扫描页）'
    pageCache.value[page] = text
    currentPageText.value = text
  } catch {
    currentPageText.value = '（加载失败）'
  } finally {
    pageLoading.value = false
  }
}

function onPageChange(page: number) {
  loadPageContent(page)
}

async function onReparse() {
  if (!currentDoc.value) return
  reparsing.value = true
  try {
    currentDoc.value = await reparseDocument(currentDoc.value.id)
    ElMessage.success('已重新解析，分页页码已更新')
    pageCache.value = {}
    currentPage.value = 1
    await loadPageContent(1)
    await loadDocuments()
  } finally {
    reparsing.value = false
  }
}

async function openGenerate(row: DocumentVO) {
  generateDoc.value = row
  questionCount.value = quizConfig.value?.defaultCount ?? 10
  generateVisible.value = true
  plannedLlmLabel.value = ''
  try {
    const cfg = await getLlmConfig()
    plannedLlmLabel.value = formatLlmLabel(cfg.provider, cfg.model)
  } catch {
    plannedLlmLabel.value = ''
  }
}

async function confirmGenerate() {
  if (!generateDoc.value) return
  generating.value = true
  try {
    const quiz = await generateQuiz(
      generateDoc.value.id,
      questionCount.value,
      difficulty.value,
    )
    generateVisible.value = false
    ElMessage.success('已提交生成任务，请在试卷列表查看进度')
    router.push('/quizzes')
  } catch (error) {
    console.error('Generate quiz failed:', error)
    ElMessage.error('生成任务提交失败，请重试')
  } finally {
    generating.value = false
  }
}

async function onDelete(row: DocumentVO) {
  await ElMessageBox.confirm(`确定删除「${row.fileName}」？`, '删除确认', {
    type: 'warning',
  })
  await deleteDocument(row.id)
  ElMessage.success('已删除')
  await loadDocuments()
}

onMounted(() => {
  loadDocuments()
  loadQuizConfig()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.gen-hint {
  margin-bottom: 16px;
  color: #303133;
}
.page-toolbar {
  margin-bottom: 12px;
}
</style>
