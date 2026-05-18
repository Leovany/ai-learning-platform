<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <span>{{ quiz?.title || '答题' }}</span>
          <el-tag v-if="llmLabel" type="info" size="small" class="llm-tag">
            由 {{ llmLabel }} 生成
          </el-tag>
        </div>
        <el-tag v-if="quiz">第 {{ currentIndex + 1 }} / {{ questions.length }} 题</el-tag>
      </div>
    </template>

    <template v-if="currentQuestion">
      <p class="stem">{{ currentIndex + 1 }}. {{ currentQuestion.stem }}</p>
      <el-radio-group v-model="answers[currentQuestion.id]" class="options">
        <el-radio
          v-for="opt in optionList(currentQuestion)"
          :key="opt.key"
          :value="opt.key"
          border
          class="option-item"
        >
          {{ opt.key }}. {{ opt.text }}
        </el-radio>
      </el-radio-group>

      <div class="nav">
        <el-button :disabled="currentIndex === 0" @click="currentIndex--">上一题</el-button>
        <el-button
          v-if="currentIndex < questions.length - 1"
          type="primary"
          @click="currentIndex++"
        >
          下一题
        </el-button>
        <el-button
          v-else
          type="primary"
          :loading="submitting"
          @click="onSubmit"
        >
          提交答卷
        </el-button>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQuiz, submitQuiz, type QuestionVO, type QuizVO } from '@/api/quiz'
import { formatLlmLabel } from '@/utils/llm'

const route = useRoute()
const router = useRouter()
const quizId = Number(route.params.id)

const loading = ref(false)
const submitting = ref(false)
const quiz = ref<QuizVO | null>(null)
const questions = ref<QuestionVO[]>([])
const currentIndex = ref(0)
const answers = reactive<Record<number, string>>({})

const currentQuestion = computed(() => questions.value[currentIndex.value])

const llmLabel = computed(() =>
  formatLlmLabel(quiz.value?.llmProvider, quiz.value?.llmModel),
)

function optionList(q: QuestionVO) {
  return [
    { key: 'A', text: q.optionA },
    { key: 'B', text: q.optionB },
    { key: 'C', text: q.optionC },
    { key: 'D', text: q.optionD },
  ]
}

onMounted(async () => {
  loading.value = true
  try {
    quiz.value = await getQuiz(quizId, false)
    questions.value = quiz.value.questions || []
    for (const q of questions.value) {
      answers[q.id] = ''
    }
  } finally {
    loading.value = false
  }
})

async function onSubmit() {
  const unanswered = questions.value.filter((q) => !answers[q.id])
  if (unanswered.length > 0) {
    await ElMessageBox.confirm(
      `还有 ${unanswered.length} 道题未作答，确定提交吗？`,
      '提交确认',
    )
  }
  submitting.value = true
  try {
    const payload = questions.value.map((q) => ({
      questionId: q.id,
      userAnswer: answers[q.id] || '',
    }))
    const result = await submitQuiz(quizId, payload)
    router.push({
      name: 'quiz-result',
      params: { id: quizId },
      state: { result },
    })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.llm-tag {
  font-weight: normal;
}
.stem {
  font-size: 16px;
  line-height: 1.6;
  margin-bottom: 20px;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}
.option-item {
  width: 100%;
  margin-right: 0;
  height: auto;
  padding: 12px 16px;
}
.nav {
  margin-top: 32px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .stem {
    font-size: 15px;
  }
}
</style>
