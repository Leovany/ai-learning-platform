import request, { getData } from '@/api/request'

export type QuizStatus = 'GENERATING' | 'READY' | 'FAILED'

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
  createdAt: string
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

export function listQuizzes() {
  return getData<QuizVO[]>(request.get('/quizzes'))
}

export function getQuiz(id: number, includeAnswers = false) {
  return getData<QuizVO>(
    request.get(`/quizzes/${id}`, { params: { includeAnswers } }),
  )
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
    GENERATING: '生成中',
    READY: '可答题',
    FAILED: '生成失败',
  }
  return map[status]
}

export function quizStatusType(status: QuizStatus): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<QuizStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    GENERATING: 'info',
    READY: 'success',
    FAILED: 'danger',
  }
  return map[status]
}
