import type { PresignRequest, PresignResponse } from '~/types/api'

/**
 * 브라우저에서 직접 S3로 PUT 업로드.
 * 1) 백엔드 /api/files/presigned 호출 → uploadUrl + publicUrl
 * 2) uploadUrl 로 PUT (Content-Type 정확히 맞춰야 서명 검증 성공)
 * 3) publicUrl 반환
 */
function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

export function useUpload() {
  const api = useApi()
  const config = useRuntimeConfig()

  async function uploadImage(file: File, purpose: PresignRequest['purpose']): Promise<string> {
    // 데모 모드: S3 대신 브라우저에서 data URL 로 변환해 localStorage 에 보관한다.
    if (config.public.demoMode === true) {
      return await fileToDataUrl(file)
    }
    const presign = await api<PresignResponse>('/api/files/presigned', {
      method: 'POST',
      body: {
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        purpose,
      },
    })
    const res = await fetch(presign.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
      body: file,
    })
    if (!res.ok) {
      throw new Error(`S3 업로드 실패 (${res.status})`)
    }
    return presign.publicUrl
  }

  return { uploadImage }
}
