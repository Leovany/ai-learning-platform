<template>
  <el-card shadow="never">
    <template #header>
      <span>成绩：{{ result?.title }}</span>
    </template>

    <div v-if="!result" class="empty-hint">暂无成绩数据，请从答题页提交后查看</div>

    <template v-else>
      <div class="score-panel">
        <el-progress
          type="circle"
          :percentage="percentage"
          :width="140"
          :color="percentage >= 60 ? '#67c23a' : '#e6a23c'"
        />
        <div class="score-text">
          <span class="score-num">{{ result.score }} / {{ result.total }}</span>
          <span class="score-label">正确题数</span>
        </div>
      </div>

      <el-divider />

      <el-tabs v-model="activeTab" class="result-tabs" @tab-change="onTabChange">
        <el-tab-pane name="wrong">
          <template #label>
            <span>错题</span>
            <el-badge :value="wrongQuestions.length" :max="99" class="tab-badge" type="danger" />
          </template>
        </el-tab-pane>
        <el-tab-pane name="correct">
          <template #label>
            <span>正确</span>
            <el-badge :value="correctQuestions.length" :max="99" class="tab-badge" type="success" />
          </template>
        </el-tab-pane>
      </el-tabs>

      <el-empty
        v-if="displayedQuestions.length === 0"
        :description="activeTab === 'wrong' ? '全部答对，没有错题' : '暂无答对的题目'"
      />

      <div v-for="q in displayedQuestions" :key="q.id" class="question-review">
        <div class="review-header">
          <span>{{ q.sortOrder }}. {{ q.stem }}</span>
          <el-tag :type="q.isCorrect ? 'success' : 'danger'" size="small">
            {{ q.isCorrect ? '正确' : '错误' }}
          </el-tag>
        </div>
        <ul class="options-list">
          <li
            v-for="opt in optionList(q)"
            :key="opt.key"
            :class="['option-item', optionClass(q, opt.key)]"
          >
            <span class="opt-key">{{ opt.key }}.</span>
            <span class="opt-text">{{ opt.text }}</span>
            <el-tag v-if="q.correctAnswer === opt.key" type="success" size="small" class="opt-tag">
              正确答案
            </el-tag>
            <el-tag
              v-else-if="q.userAnswer === opt.key"
              type="danger"
              size="small"
              class="opt-tag"
            >
              你的选择
            </el-tag>
          </li>
        </ul>
        <p class="answer-line">
          你的答案：<strong>{{ q.userAnswer || '未作答' }}</strong>
          · 正确答案：<strong>{{ q.correctAnswer }}</strong>
        </p>
        <el-collapse v-if="q.explanation || q.sourcePage" v-model="expandedNames">
          <el-collapse-item title="查看解析" :name="String(q.id)">
            <p v-if="q.explanation" class="explanation">{{ q.explanation }}</p>
            <div v-if="q.sourcePage" class="pdf-location">
              <p class="location-line">
                <el-icon class="loc-icon"><Location /></el-icon>
                <strong>PDF 位置：</strong>
                第 {{ q.sourcePage }} 页
                <template v-if="q.documentPageCount"> / 共 {{ q.documentPageCount }} 页</template>
                <template v-if="hasHighlight(q)">
                  · 原文第 {{ q.sourceHighlightStart! + 1 }}–{{ q.sourceHighlightEnd }} 字符
                </template>
              </p>
              <p v-if="q.sourceQuote || q.pdfExcerpt" class="source-quote">
                「{{ q.sourceQuote || q.pdfExcerpt }}」
              </p>
              <el-button
                type="primary"
                link
                size="small"
                :loading="loadingPage === q.id"
                @click="viewPdfPage(q)"
              >
                查看该页 PDF 原文（高亮对应段落）
              </el-button>
            </div>
            <p v-else-if="q.explanation" class="no-page-hint">
              暂无页码定位。请在学习文档中点击「重新解析」后重新生成试卷。
            </p>
          </el-collapse-item>
        </el-collapse>
      </div>

      <div class="footer-actions">
        <el-button type="primary" @click="$router.push('/quizzes')">返回试卷题库</el-button>
        <el-button @click="$router.push(`/quizzes/${quizId}/take`)">再答一次</el-button>
      </div>
    </template>

    <el-drawer v-model="pageDrawerVisible" :title="pageDrawerTitle" size="55%">
      <div v-if="pageData" class="page-viewer">
        <el-tag type="info" size="small" class="page-tag">
          第 {{ pageData.page }} / {{ pageData.pageCount }} 页
        </el-tag>
        <div class="page-text-box">
          <template v-if="hasPageHighlight(pageData)">
            <span>{{ beforeHighlight }}</span>
            <mark class="hl-mark">{{ highlightSegment }}</mark>
            <span>{{ afterHighlight }}</span>
          </template>
          <template v-else>{{ pageData.text || '（该页无文本）' }}</template>
        </div>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDocumentPage, type DocumentPageVO } from '@/api/document'
import type { QuestionVO, SubmitQuizResult } from '@/api/quiz'

const route = useRoute()
const router = useRouter()
const quizId = Number(route.params.id)
const result = ref((history.state.result as SubmitQuizResult | undefined) ?? undefined)

const pageDrawerVisible = ref(false)
const pageDrawerTitle = ref('')
const pageData = ref<DocumentPageVO | null>(null)
const loadingPage = ref<number | null>(null)
const expandedNames = ref<string[]>([])
const activeTab = ref<'wrong' | 'correct'>('wrong')

const beforeHighlight = computed(() => {
  const d = pageData.value
  if (!d?.text || d.highlightStart == null) return ''
  return d.text.slice(0, d.highlightStart)
})
const highlightSegment = computed(() => {
  const d = pageData.value
  if (!d?.text || d.highlightStart == null || d.highlightEnd == null) return ''
  return d.text.slice(d.highlightStart, d.highlightEnd)
})
const afterHighlight = computed(() => {
  const d = pageData.value
  if (!d?.text || d.highlightEnd == null) return ''
  return d.text.slice(d.highlightEnd)
})

const wrongQuestions = computed(() =>
  result.value?.questions.filter((q) => !q.isCorrect) ?? [],
)
const correctQuestions = computed(() =>
  result.value?.questions.filter((q) => q.isCorrect) ?? [],
)
const displayedQuestions = computed(() =>
  activeTab.value === 'wrong' ? wrongQuestions.value : correctQuestions.value,
)

function initExpandedNames(questions: QuestionVO[]) {
  expandedNames.value = questions
    .filter((q) => q.explanation || q.sourcePage)
    .map((q) => String(q.id))
}

function onTabChange(tab: string | number) {
  const questions = tab === 'wrong' ? wrongQuestions.value : correctQuestions.value
  initExpandedNames(questions)
}

onMounted(() => {
  if (!result.value) {
    router.replace('/quizzes')
    return
  }
  activeTab.value = 'wrong'
  initExpandedNames(wrongQuestions.value)
})

const percentage = computed(() => {
  if (!result.value?.total) return 0
  return Math.round((result.value.score / result.value.total) * 100)
})

function optionList(q: QuestionVO) {
  return [
    { key: 'A', text: q.optionA },
    { key: 'B', text: q.optionB },
    { key: 'C', text: q.optionC },
    { key: 'D', text: q.optionD },
  ]
}

function optionClass(q: QuestionVO, key: string) {
  if (q.correctAnswer === key) return 'opt-correct'
  if (q.userAnswer === key && !q.isCorrect) return 'opt-wrong'
  return ''
}

function hasHighlight(q: QuestionVO) {
  return (
    q.sourceHighlightStart != null &&
    q.sourceHighlightEnd != null &&
    q.sourceHighlightEnd > q.sourceHighlightStart!
  )
}

function hasPageHighlight(d: DocumentPageVO) {
  return (
    d.highlightStart != null &&
    d.highlightEnd != null &&
    d.highlightEnd > d.highlightStart &&
    d.text
  )
}

async function viewPdfPage(q: QuestionVO) {
  const docId = result.value?.documentId
  const page = q.sourcePage
  if (!docId || !page) {
    ElMessage.warning('无法定位 PDF 页码')
    return
  }
  loadingPage.value = q.id
  try {
    const quote = q.sourceQuote || q.pdfExcerpt || ''
    pageData.value = await getDocumentPage(docId, page, quote || undefined)
    pageDrawerTitle.value = `${pageData.value.fileName} — 第 ${page} 页原文`
    pageDrawerVisible.value = true
  } finally {
    loadingPage.value = null
  }
}
</script>

<style scoped>
.empty-hint {
  color: #909399;
  padding: 24px 0;
}
.score-panel {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 16px 0;
}
.score-text {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.score-num {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
}
.score-label {
  color: #909399;
}
.result-tabs {
  margin-bottom: 16px;
}
.tab-badge {
  margin-left: 6px;
}
.question-review {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}
.review-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  line-height: 1.5;
}
.options-list {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
}
.option-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  background: #fafafa;
  font-size: 14px;
  line-height: 1.5;
}
.option-item.opt-correct {
  border-color: #b3e19d;
  background: #f0f9eb;
}
.option-item.opt-wrong {
  border-color: #fbc4c4;
  background: #fef0f0;
}
.opt-key {
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
}
.opt-text {
  flex: 1;
  color: #606266;
}
.opt-tag {
  flex-shrink: 0;
}
.answer-line {
  color: #606266;
  font-size: 14px;
  margin-bottom: 8px;
}
.explanation {
  margin-bottom: 12px;
  line-height: 1.6;
  color: #303133;
}
.pdf-location {
  margin-top: 8px;
}
.location-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
}
.loc-icon {
  color: #409eff;
}
.source-quote {
  margin: 10px 0;
  padding: 10px 12px;
  background: #f4f4f5;
  border-left: 3px solid #409eff;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.no-page-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
.footer-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}
.page-viewer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-tag {
  align-self: flex-start;
}
.page-text-box {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 14px;
  color: #303133;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  max-height: 70vh;
  overflow-y: auto;
}
.hl-mark {
  background: #fff3cd;
  padding: 2px 0;
  border-radius: 2px;
  color: #303133;
}
</style>
