import request, { getData } from '@/api/request'

export type DocumentStatus = 'PENDING' | 'PARSED' | 'FAILED'

export interface DocumentVO {
  id: number
  fileName: string
  fileSize: number
  pageCount: number | null
  status: DocumentStatus
  createdAt: string
  extractedText?: string | null
  textPreview?: string | null
}

export function listDocuments() {
  return getData<DocumentVO[]>(request.get('/documents'))
}

export function getDocument(id: number) {
  return getData<DocumentVO>(request.get(`/documents/${id}`))
}

export function uploadDocument(file: File) {
  const form = new FormData()
  form.append('file', file)
  return getData<DocumentVO>(
    request.post('/documents/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  )
}

export function deleteDocument(id: number) {
  return getData<void>(request.delete(`/documents/${id}`))
}

export interface DocumentPageVO {
  documentId: number
  fileName: string
  page: number
  pageCount: number
  text: string
  highlightStart?: number | null
  highlightEnd?: number | null
  highlightText?: string | null
}

export function getDocumentPage(documentId: number, page: number, highlight?: string) {
  return getData<DocumentPageVO>(
    request.get(`/documents/${documentId}/pages/${page}`, {
      params: highlight ? { highlight } : undefined,
    }),
  )
}

export function reparseDocument(id: number) {
  return getData<DocumentVO>(request.post(`/documents/${id}/reparse`))
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function statusLabel(status: DocumentStatus): string {
  const map: Record<DocumentStatus, string> = {
    PENDING: '解析中',
    PARSED: '已解析',
    FAILED: '解析失败',
  }
  return map[status]
}

export function statusType(status: DocumentStatus): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<DocumentStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'info',
    PARSED: 'success',
    FAILED: 'danger',
  }
  return map[status]
}
