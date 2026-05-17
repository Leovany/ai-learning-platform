import { defineStore } from 'pinia'
import { ref } from 'vue'
import request, { getData } from '@/api/request'

export const useAppStore = defineStore('app', () => {
  const backendOnline = ref<boolean | null>(null)

  async function checkBackend() {
    try {
      await getData(request.get('/ping'))
      backendOnline.value = true
    } catch {
      backendOnline.value = false
    }
  }

  return { backendOnline, checkBackend }
})
