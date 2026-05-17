<template>
  <el-card shadow="never" v-loading="uploading">
    <template #header>上传 PDF 文档</template>
    <el-upload
      drag
      accept=".pdf"
      :show-file-list="false"
      :http-request="handleUpload"
      :before-upload="beforeUpload"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">仅支持 PDF，单文件最大 20MB</div>
      </template>
    </el-upload>

    <el-alert
      v-if="lastResult"
      class="result-alert"
      :title="`「${lastResult.fileName}」上传成功`"
      :type="lastResult.status === 'PARSED' ? 'success' : lastResult.status === 'FAILED' ? 'warning' : 'info'"
      show-icon
      :closable="false"
    >
      <template v-if="lastResult.status === 'PARSED'">
        共 {{ lastResult.pageCount }} 页，已提取文本，可前往文档列表生成考题。
      </template>
      <template v-else-if="lastResult.status === 'FAILED'">
        文件已保存但文本解析失败，可能是扫描版 PDF。
      </template>
    </el-alert>

    <div v-if="lastResult" class="actions">
      <el-button type="primary" @click="$router.push('/documents')">查看文档列表</el-button>
      <el-button @click="lastResult = null">继续上传</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage } from 'element-plus'
import { uploadDocument, type DocumentVO } from '@/api/document'

const uploading = ref(false)
const lastResult = ref<DocumentVO | null>(null)

function beforeUpload(file: File) {
  const isPdf = file.name.toLowerCase().endsWith('.pdf')
  const isLt20M = file.size / 1024 / 1024 < 20
  if (!isPdf) {
    ElMessage.error('仅支持 PDF 格式')
    return false
  }
  if (!isLt20M) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  lastResult.value = null
  try {
    lastResult.value = await uploadDocument(options.file as File)
    ElMessage.success('上传成功')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-icon {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 8px;
}
.result-alert {
  margin-top: 24px;
}
.actions {
  margin-top: 16px;
}
</style>
