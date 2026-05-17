import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/documents',
      name: 'documents',
      component: () => import('@/views/DocumentsView.vue'),
      meta: { title: '学习文档' },
    },
    {
      path: '/documents/upload',
      name: 'document-upload',
      component: () => import('@/views/DocumentUploadView.vue'),
      meta: { title: '上传文档' },
    },
    {
      path: '/quizzes',
      name: 'quizzes',
      component: () => import('@/views/QuizzesView.vue'),
      meta: { title: '试卷题库' },
    },
    {
      path: '/quizzes/:id/take',
      name: 'quiz-take',
      component: () => import('@/views/QuizTakeView.vue'),
      meta: { title: '在线答题' },
    },
    {
      path: '/quizzes/:id/result',
      name: 'quiz-result',
      component: () => import('@/views/QuizResultView.vue'),
      meta: { title: '答题成绩' },
    },
  ],
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || 'AI 智能学习平台'
  document.title = `${title} - AI 智能学习平台`
})

export default router
