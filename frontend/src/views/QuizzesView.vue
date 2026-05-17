<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <span>试卷题库</span>
    </template>

    <el-empty v-if="!loading && quizzes.length === 0" description="暂无试卷，请先从文档生成考题" />

    <el-table v-else :data="quizzes" stripe class="quiz-table">
      <el-table-column prop="title" label="试卷标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="documentName" label="来源文档" min-width="120" show-overflow-tooltip />
      <el-table-column prop="questionCount" label="题数" width="70" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="quizStatusType(row.status)" size="small">
            {{ quizStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.status !== 'READY'"
            @click="goTake(row.id)"
          >
            答题
          </el-button>
          <el-button
            v-if="row.status === 'READY'"
            link
            type="primary"
            @click="openAttempts(row)"
          >
            记录
          </el-button>
          <el-button
            v-if="row.status === 'FAILED'"
            link
            type="warning"
            :loading="regeneratingId === row.id"
            @click="onRegenerate(row)"
          >
            重试
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="attemptsVisible" :title="`答题记录 · ${attemptsQuiz?.title}`" size="400px">
      <el-empty v-if="!attemptsLoading && attempts.length === 0" description="暂无答题记录" />
      <el-table v-else :data="attempts" size="small" v-loading="attemptsLoading">
        <el-table-column label="得分" width="100">
          <template #default="{ row }">{{ row.score }} / {{ row.total }}</template>
        </el-table-column>
        <el-table-column label="正确率" width="90">
          <template #default="{ row }">
            {{ row.total ? Math.round((row.score / row.total) * 100) : 0 }}%
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteQuiz,
  generateQuiz,
  listQuizAttempts,
  listQuizzes,
  quizStatusLabel,
  quizStatusType,
  type QuizAttemptVO,
  type QuizVO,
} from '@/api/quiz'

const router = useRouter()
const loading = ref(false)
const quizzes = ref<QuizVO[]>([])
const regeneratingId = ref<number | null>(null)

const attemptsVisible = ref(false)
const attemptsLoading = ref(false)
const attemptsQuiz = ref<QuizVO | null>(null)
const attempts = ref<QuizAttemptVO[]>([])

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

function goTake(id: number) {
  router.push(`/quizzes/${id}/take`)
}

async function openAttempts(row: QuizVO) {
  attemptsQuiz.value = row
  attemptsVisible.value = true
  attemptsLoading.value = true
  try {
    attempts.value = await listQuizAttempts(row.id)
  } finally {
    attemptsLoading.value = false
  }
}

async function onRegenerate(row: QuizVO) {
  regeneratingId.value = row.id
  try {
    await deleteQuiz(row.id)
    const quiz = await generateQuiz(row.documentId, row.questionCount)
    ElMessage.success('正在重新生成，请稍候…')
    await loadQuizzes()
    if (quiz.status === 'READY') {
      router.push(`/quizzes/${quiz.id}/take`)
    }
  } catch {
  } finally {
    regeneratingId.value = null
  }
}

async function onDelete(row: QuizVO) {
  await ElMessageBox.confirm(`确定删除试卷「${row.title}」？`, '删除确认', { type: 'warning' })
  await deleteQuiz(row.id)
  ElMessage.success('已删除')
  await loadQuizzes()
}

onMounted(loadQuizzes)
</script>

<style scoped>
@media (max-width: 768px) {
  .quiz-table {
    font-size: 13px;
  }
}
</style>
