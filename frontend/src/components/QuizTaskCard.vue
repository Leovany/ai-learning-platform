<template>
  <el-card class="task-card" :class="{ 'task-card-generating': quiz.status === 'GENERATING' }" shadow="hover">
    <div class="task-header">
      <div class="task-title">
        <span class="title-text">{{ quiz.title }}</span>
        <el-tag :type="quizStatusType(quiz.status)" size="small" class="status-tag">
          {{ quizStatusLabel(quiz.status) }}
        </el-tag>
      </div>
      <div class="task-meta">
        <span class="meta-item">📝 {{ quiz.questionCount }} 题</span>
        <span v-if="llmLabel" class="meta-item llm-meta">🤖 由 {{ llmLabel }} 生成</span>
        <span v-if="pageHint" class="meta-item">📑 {{ pageHint }}</span>
      </div>
    </div>

    <div v-if="quiz.status === 'GENERATING' || quiz.status === 'PENDING'" class="progress-section">
      <el-progress
        :percentage="quiz.progress || 0"
        :stroke-width="6"
        :show-text="true"
        text-inside
        status="active"
      />
      <div class="progress-info">
        <span>{{ quiz.status === 'GENERATING' ? '正在生成题目...' : '排队等待中...' }}</span>
        <span v-if="quiz.estimatedCompletionTime" class="estimate-time">
          预计: {{ formatTime(quiz.estimatedCompletionTime) }}
        </span>
      </div>
    </div>

    <div v-if="quiz.status === 'FAILED'" class="error-section">
      <el-alert
        type="error"
        :title="'生成失败: ' + (quiz.errorMessage || '未知错误')"
        show-icon
        :closable="false"
      />
    </div>

    <div class="task-footer">
      <span class="task-time">{{ formatTime(quiz.createdAt) }}</span>
      <div class="task-actions">
        <el-button v-if="quiz.status === 'READY'" link type="primary" @click="$emit('take')">开始答题</el-button>
        <el-button v-if="quiz.status === 'READY'" link type="success" @click="$emit('export')">下载考题</el-button>
        <el-button
          v-if="quiz.status === 'FAILED'"
          link
          type="warning"
          :loading="actionLoading"
          @click="$emit('regenerate')"
        >
          重试
        </el-button>
        <el-button
          v-if="quiz.status === 'PENDING' || quiz.status === 'GENERATING'"
          link
          type="danger"
          :loading="actionLoading"
          @click="$emit('cancel')"
        >
          取消
        </el-button>
        <el-button v-if="quiz.status !== 'GENERATING'" link type="danger" @click="$emit('delete')">删除</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { quizStatusLabel, quizStatusType, type QuizVO } from '@/api/quiz'
import { formatLlmLabel } from '@/utils/llm'

const props = defineProps<{
  quiz: QuizVO
  pageHint?: string
  actionLoading?: boolean
}>()

defineEmits<{
  take: []
  export: []
  delete: []
  cancel: []
  regenerate: []
}>()

const llmLabel = computed(() => formatLlmLabel(props.quiz.llmProvider, props.quiz.llmModel))

function formatTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}
</script>

<style scoped>
.task-card {
  border-radius: 8px;
  margin-bottom: 12px;
}
.task-card-generating {
  border-left: 4px solid #e6a23c;
}
.task-header {
  margin-bottom: 12px;
}
.task-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.title-text {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}
.llm-meta {
  color: #409eff;
}
.progress-section {
  margin-bottom: 12px;
}
.progress-info {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.error-section {
  margin-bottom: 12px;
}
.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
.task-time {
  font-size: 12px;
  color: #909399;
}
.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
