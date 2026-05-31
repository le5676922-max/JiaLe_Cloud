import request from './request'

const CHUNK_SIZE = 10 * 1024 * 1024 // 10MB

export interface UploadOptions {
  file: File
  filePath: string
  nodeId?: string
  onProgress?: (percent: number) => void
}

export interface UploadResult {
  filePath: string
  size: number
}

/**
 * 分片上传（含断点续传）
 */
export async function chunkUpload(opts: UploadOptions): Promise<UploadResult> {
  const { file, filePath, nodeId, onProgress } = opts
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

  // 1. 检查断点
  let uploadedChunks: number[] = []
  try {
    const { data: res } = await request.get('/upload/resume', {
      params: { filePath }
    })
    uploadedChunks = res.data?.uploadedChunks || []
  } catch { /* 首次上传 */ }

  // 2. 初始化上传
  const { data: initRes } = await request.post('/upload/init', {
    filePath,
    fileSize: file.size,
    totalChunks,
    nodeId: nodeId || undefined
  })
  const uploadId = initRes.data.uploadId
  const nodePort = initRes.data.nodePort
  // 自动获取云服务器地址（浏览器当前访问的地址）
  const nodeBase = nodePort ? `http://${window.location.hostname}:${nodePort}` : ''

  // 3. 并行上传分片（直连存储节点）
  const CHUNK_PARALLEL = 5
  const pending: number[] = []
  for (let i = 0; i < totalChunks; i++) {
    if (!uploadedChunks.includes(i)) {
      pending.push(i)
    }
  }

  let completed = uploadedChunks.length
  const total = totalChunks

  async function uploadChunk(index: number): Promise<void> {
    const start = index * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, file.size)
    const chunk = file.slice(start, end)

    const formData = new FormData()
    formData.append('chunk', chunk)

    const params = `uploadId=${uploadId}&chunkIndex=${index}&filePath=${encodeURIComponent(filePath)}`
    if (nodeBase) {
      // 直连存储节点，不经协调节点中转
      const token = localStorage.getItem('cloud_token')
      const response = await fetch(`${nodeBase}/api/upload/chunk?${params}`, {
        method: 'POST',
        body: formData,
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!response.ok) {
        throw new Error(`chunk upload failed: ${response.status}`)
      }
    } else {
      await request.post('/upload/chunk', formData, {
        params: { uploadId, chunkIndex: index, filePath },
        timeout: 0,
      })
    }

    completed++
    if (onProgress) {
      onProgress(Math.round((completed / total) * 100))
    }
  }

  // 并行队列
  for (let i = 0; i < pending.length; i += CHUNK_PARALLEL) {
    const batch = pending.slice(i, i + CHUNK_PARALLEL)
    await Promise.all(batch.map(uploadChunk))
  }

  // 4. 合并（直连存储节点，有 nodePort 时不用经过协调节点）
  const mergeBody = { uploadId, filePath, totalChunks, expectedSize: file.size }
  const mergeToken = localStorage.getItem('cloud_token')
  let mergeRes
  if (nodeBase) {
    const r = await fetch(`${nodeBase}/api/upload/merge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${mergeToken}` },
      body: JSON.stringify(mergeBody),
    })
    if (!r.ok) {
      throw new Error(`merge failed: ${r.status}`)
    }
    mergeRes = await r.json()
  } else {
    const { data } = await request.post('/upload/merge', mergeBody)
    mergeRes = data
  }

  return {
    filePath: mergeRes.data?.filePath ?? filePath,
    size: mergeRes.data?.size ?? mergeRes.size
  }
}
