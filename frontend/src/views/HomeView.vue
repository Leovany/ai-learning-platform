<template>
  <el-card shadow="never">
    <template #header>
      <span>欢迎使用 AI 智能学习平台</span>
    </template>
    <p class="desc">
      上传 PDF 学习资料，系统将自动解析内容并生成选择题，助你高效复习。
    </p>
    <el-space wrap>
      <el-button type="primary" @click="$router.push('/documents/upload')">
        <el-icon><Upload /></el-icon>
        上传 PDF
      </el-button>
      <el-button @click="$router.push('/quizzes')">
        <el-icon><EditPen /></el-icon>
        查看试卷
      </el-button>
    </el-space>
    <el-divider />
    <el-descriptions title="开发进度" :column="1" border>
      <el-descriptions-item label="Phase 0">项目脚手架</el-descriptions-item>
      <el-descriptions-item label="Phase 1">PDF 上传与解析</el-descriptions-item>
      <el-descriptions-item label="Phase 2">AI 智能出题</el-descriptions-item>
      <el-descriptions-item label="Phase 3">在线答题与成绩（当前）</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<style scoped>
.desc {
  color: #606266;
  margin-bottom: 20px;
  line-height: 1.6;
}
</style>
