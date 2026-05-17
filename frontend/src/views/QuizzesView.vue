<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <span>试卷题库</span>
    </template>

    <el-empty v-if="!loading && quizzes.length === 0" description="暂无试卷，请先从文档生成考题" />

    <el-table v-else :data="quizzes" stripe>
      <el-table-column prop="title" label="试卷标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="documentName" label="来源文档" min-width="140" show-overflow-tooltip />
      <el-table-column prop="questionCount" label="题数" width="70" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="quizStatusType(row.status)" size="small">
            {{ quizStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.status !== 'READY'"
            @click="goTake(row.id)"
          >
            答题
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteQuiz,
  listQuizzes,
  quizStatusLabel,
  quizStatusType,
  type QuizVO,
} from '@/api/quiz'

const router = useRouter()
const loading = ref(false)
const quizzes = ref<QuizVO[]>([])

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

async function onDelete(row: QuizVO) {
  await ElMessageBox.confirm(`确定删除试卷「${row.title}」？`, '删除确认', { type: 'warning' })
  await deleteQuiz(row.id)
  ElMessage.success('已删除')
  await loadQuizzes()
}

onMounted(loadQuizzes)
</script>
