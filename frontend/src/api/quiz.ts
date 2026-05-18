import request, { getData } from '@/api/request'

export type QuizStatus = 'PENDING' | 'GENERATING' | 'READY' | 'FAILED' | 'CANCELLED'

export interface QuestionVO {
  id: number
  sortOrder: number
  stem: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  correctAnswer?: string
  explanation?: string
  sourcePage?: number | null
  sourceQuote?: string | null
  sourceHighlightStart?: number | null
  sourceHighlightEnd?: number | null
  pdfExcerpt?: string | null
  documentPageCount?: number | null
  userAnswer?: string | null
  isCorrect?: boolean
}

export interface QuizVO {
  id: number
  documentId: number
  documentName?: string
  title: string
  questionCount: number
  status: QuizStatus
  errorMessage?: string
  createdAt: string
  progress?: number
  estimatedCompletionTime?: string
  questions?: QuestionVO[]
}

export interface QuizAttemptVO {
  id: number
  quizId: number
  score: number
  total: number
  submittedAt: string
}

export type QuizDifficulty = 'easy' | 'medium' | 'hard'

export interface QuizConfigVO {
  defaultCount: number
  maxCount: number
}

export interface SubmitQuizResult {
  attemptId: number
  quizId: number
  documentId?: number
  documentName?: string
  title: string
  score: number
  total: number
  questions: QuestionVO[]
}

export function getQuizConfig() {
  return getData<QuizConfigVO>(request.get('/quizzes/config'))
}

export function listQuizzes() {
  return getData<QuizVO[]>(request.get('/quizzes'))
}

export function getQuiz(id: number, includeAnswers = false) {
  return getData<QuizVO>(
    request.get(`/quizzes/${id}`, { params: { includeAnswers } }),
  )
}

export function getQuizStatus(id: number) {
  return getData<QuizVO>(request.get(`/quizzes/${id}/status`))
}

export function generateQuiz(
  documentId: number,
  questionCount?: number,
  difficulty?: QuizDifficulty,
) {
  return getData<QuizVO>(
    request.post('/quizzes/generate', { documentId, questionCount, difficulty }),
  )
}

export function cancelQuiz(id: number) {
  return getData<void>(request.post(`/quizzes/${id}/cancel`))
}

export function exportQuiz(id: number) {
  return request.get(`/quizzes/${id}/export`, { responseType: 'blob' })
}

export function listQuizAttempts(quizId: number) {
  return getData<QuizAttemptVO[]>(request.get(`/quizzes/${quizId}/attempts`))
}

export function deleteQuiz(id: number) {
  return getData<void>(request.delete(`/quizzes/${id}`))
}

export function submitQuiz(
  id: number,
  answers: { questionId: number; userAnswer: string }[],
) {
  return getData<SubmitQuizResult>(
    request.post(`/quizzes/${id}/submit`, { answers }),
  )
}

export function quizStatusLabel(status: QuizStatus): string {
  const map: Record<QuizStatus, string> = {
    PENDING: '排队中',
    GENERATING: '生成中',
    READY: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[status]
}

export function quizStatusType(status: QuizStatus): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<QuizStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'info',
    GENERATING: 'warning',
    READY: 'success',
    FAILED: 'danger',
    CANCELLED: 'info',
  }
  return map[status]
}
