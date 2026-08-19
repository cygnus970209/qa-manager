import type { PresignRequest, PresignResponse } from '~/types/api'

/**
 * 브라우저에서 직접 S3로 PUT 업로드.
 * 1) 백엔드 /api/files/presigned 호출 → uploadUrl + publicUrl
 * 2) uploadUrl 로 PUT (Content-Type/Content-Length 정확히 맞춰야 서명 검증 성공)
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
  const maxFileSizeMb = Number(config.public.uploadMaxFileSizeMb) || 100

  function assertUploadable(file: File, purpose: PresignRequest['purpose']) {
    if (file.size > maxFileSizeMb * 1024 * 1024) {
      throw new Error(`파일 크기는 ${maxFileSizeMb}MB 이하만 업로드할 수 있습니다: ${file.name}`)
    }
    const type = (file.type || '').toLowerCase()
    const isImage = type.startsWith('image/')
    if (purpose === 'avatar') {
      if (!isImage) throw new Error(`이미지만 업로드할 수 있습니다: ${file.name}`)
    } else if (!isImage && type !== 'application/pdf') {
      throw new Error(`이미지 또는 PDF만 업로드할 수 있습니다: ${file.name}`)
    }
  }

  async function uploadFile(file: File, purpose: PresignRequest['purpose']): Promise<string> {
    assertUploadable(file, purpose)
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
        fileSize: file.size,
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

  return { uploadFile }
}
