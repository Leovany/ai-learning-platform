<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="28"><Reading /></el-icon>
        <span>AI 学习平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#fff"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/documents">
          <el-icon><Document /></el-icon>
          <span>学习文档</span>
        </el-menu-item>
        <el-menu-item index="/documents/upload">
          <el-icon><Upload /></el-icon>
          <span>上传 PDF</span>
        </el-menu-item>
        <el-menu-item index="/quizzes">
          <el-icon><EditPen /></el-icon>
          <span>试卷列表</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ pageTitle }}</span>
        <el-tag v-if="appStore.backendOnline === true" type="success" size="small">后端已连接</el-tag>
        <el-tag v-else-if="appStore.backendOnline === false" type="danger" size="small">后端未连接</el-tag>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) || 'AI 智能学习平台')

onMounted(() => {
  appStore.checkBackend()
})
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #001529;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 20px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.page-title {
  font-size: 18px;
  font-weight: 500;
}
.main {
  background: #f5f7fa;
}
</style>
