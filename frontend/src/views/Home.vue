<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderAdd, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import request from '../utils/request'
import { chunkUpload } from '../utils/chunkUpload'

const router = useRouter()
const CHUNK_THRESHOLD = 100 * 1024 * 1024

interface FileItem { fileName: string; isDirectory: boolean; size: number; lastModifiedTime: string }

const currentPath = ref('')
const files = ref<FileItem[]>([])
const loading = ref(true)
const uploadProgress = ref(0)
const uploading = ref(false)
const uploadingName = ref('')
const capacity = ref({ totalCapacity: 0, usedCapacity: 0, usagePercentage: 0 })
const newFolderVisible = ref(false)
const newFolderName = ref('')
const renameVisible = ref(false)
const renameOldPath = ref('')
const renameNewName = ref('')
const showTrash = ref(false)
const trashFiles = ref<any[]>([])
const shareVisible = ref(false)
const sharePath = ref('')
const shareExpiry = ref(24)
const role = ref(localStorage.getItem('cloud_role') || '')
const uploadNodes = ref<any[]>([])
const selectedUploadNodeId = ref('')
const availableUploadNodes = computed(() => uploadNodes.value.filter((node: any) => node.status === 'online'))
const myNodes = ref<any[]>([])
const bindDialog = ref(false)
const bindQrCode = ref('')
const bindTicket = ref({ ticket: '', deepLink: '', expiresAt: '' })
const loadingBindTicket = ref(false)
const primaryNode = computed(() => myNodes.value.find((n: any) => n.status === 'online') || myNodes.value[0] || null)
const nodeStatusType = computed(() => primaryNode.value?.status === 'online' ? 'success' : primaryNode.value ? 'danger' : 'info')
const nodeStatusText = computed(() => primaryNode.value?.status === 'online' ? '手机节点在线' : primaryNode.value ? '手机节点离线' : '未绑定手机节点')

const breadcrumbItems = computed(() => {
  if (!currentPath.value) return [{ name: '根目录', path: '' }]
  const parts = currentPath.value.split('/').filter(Boolean)
  const items = [{ name: '根目录', path: '' }]
  let acc = ''
  parts.forEach(p => { acc += (acc ? '/' : '') + p; items.push({ name: p, path: acc }) })
  return items
})

async function fetchFiles() { loading.value = true; try { const { data } = await request.get('/files/list', { params: { path: currentPath.value } }); files.value = data || [] } catch { ElMessage.error('加载失败') } finally { loading.value = false } }
async function fetchCapacity() { try { const { data } = await request.get('/files/capacity'); capacity.value = data } catch {} }
async function fetchUploadNodes() { if (role.value === 'ADMIN') { try { const { data } = await request.get('/admin/nodes'); uploadNodes.value = data || [] } catch {} } }
async function fetchMyNodes() { try { const { data } = await request.get('/app-bind/nodes'); myNodes.value = data || [] } catch {} }
async function openBindDialog() {
  loadingBindTicket.value = true
  bindDialog.value = true
  try {
    const { data } = await request.post('/app-bind/tickets')
    bindTicket.value = data.data
    bindQrCode.value = await QRCode.toDataURL(bindTicket.value.deepLink, { width: 220, margin: 1 })
  } catch (err: any) {
    bindDialog.value = false
    ElMessage.error(err?.response?.data?.message || '生成绑定二维码失败')
  } finally {
    loadingBindTicket.value = false
  }
}
function navigateTo(path: string) { currentPath.value = path; fetchFiles() }
function handleBreadcrumbClick(path: string) { navigateTo(path) }
function handleFileClick(row: FileItem) { const p = currentPath.value ? currentPath.value + '/' + row.fileName : row.fileName; row.isDirectory ? navigateTo(p) : downloadFile(p) }

async function downloadFile(path: string) {
  const token = localStorage.getItem('cloud_token')
  if (!token) { router.push('/login'); return }
  try {
    // 1. 获取临时签名下载 URL（5分钟有效，防盗链）
    const { data } = await request.post('/files/download-token', { path })
    if (data.code !== 200) { ElMessage.error('下载失败'); return }
    // 2. 用签名 URL 下载
    const r = await fetch(data.data.url)
    if (!r.ok) { ElMessage.error('下载失败'); return }
    const blob = await r.blob(); const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    const mm = r.headers.get('Content-Disposition'); let n = path.split('/').pop() || 'download'
    if (mm) { const m = mm.match(/filename\*?=(?:UTF-8'')?([^;\s]+)/i); if (m) n = decodeURIComponent(m[1]) }
    a.download = n; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
  } catch { ElMessage.error('下载失败') }
}

async function handleDelete(row: FileItem) {
  const p = currentPath.value ? currentPath.value + '/' + row.fileName : row.fileName
  try { await ElMessageBox.confirm('确定删除？将移入回收站', '删除', { type: 'warning' }); await request.delete('/files', { params: { path: p } }); ElMessage.success('已移入回收站'); fetchFiles(); fetchCapacity() } catch {}
}
function openRename(row: FileItem) { renameOldPath.value = currentPath.value ? currentPath.value + '/' + row.fileName : row.fileName; renameNewName.value = row.fileName; renameVisible.value = true }
async function handleRename() { const n = renameNewName.value.trim(); if (!n) { renameVisible.value = false; return }; try { await request.put('/files/rename', null, { params: { path: renameOldPath.value, name: n } }); ElMessage.success('已重命名'); renameVisible.value = false; fetchFiles() } catch (e: any) { ElMessage.error(e?.response?.data?.message || '失败') } }

async function handleUpload(options: { file: File }) {
  let fn = options.file.name, fp = currentPath.value ? currentPath.value + '/' + fn : fn
  try { const { data } = await request.get('/files/exists', { params: { path: fp } })
    if (data.exists) {
      const s = fn.includes('.') ? fn.slice(0, fn.lastIndexOf('.')) + '_' + new Date().toISOString().slice(0, 10) + fn.slice(fn.lastIndexOf('.')) : fn + '_' + new Date().toISOString().slice(0, 10)
      try { await ElMessageBox.confirm('文件已存在', '文件已存在', { confirmButtonText: '覆盖', cancelButtonText: '改名', distinguishCancelAndClose: true }) }
      catch (err: any) {
        if (err === 'cancel') { try { const r = await ElMessageBox.prompt('新文件名', '改名上传', { confirmButtonText: '确认', inputValue: s }); fn = r.value || s; fp = currentPath.value ? currentPath.value + '/' + fn : fn } catch { return } } else return
      }
    }
  } catch {}
  uploading.value = true; uploadingName.value = fn; uploadProgress.value = 0
  if (options.file.size > CHUNK_THRESHOLD || selectedUploadNodeId.value || primaryNode.value?.status === 'online') {
    try { await chunkUpload({ file: options.file, filePath: fp, nodeId: selectedUploadNodeId.value, onProgress: p => { uploadProgress.value = p } }); ElMessage.success('上传成功'); fetchFiles(); fetchCapacity() }
    catch { ElMessage.error('上传失败') } finally { uploading.value = false; uploadingName.value = ''; uploadProgress.value = 0 }
    return
  }
  const fd = new FormData(); fd.append('file', options.file)
  try { await request.post('/files/upload', fd, { params: { path: currentPath.value }, onUploadProgress: e => { if (e.total) uploadProgress.value = Math.round(e.loaded * 100 / e.total) } }); ElMessage.success('上传成功'); fetchFiles(); fetchCapacity() }
  catch { ElMessage.error('上传失败') } finally { uploading.value = false; uploadingName.value = ''; uploadProgress.value = 0 }
}

async function createFolder() { const n = newFolderName.value.trim(); if (!n) return; try { await request.post('/files/folder', null, { params: { path: currentPath.value, name: n } }); ElMessage.success('已创建'); newFolderVisible.value = false; newFolderName.value = ''; fetchFiles() } catch (e: any) { ElMessage.error(e?.response?.data?.message || '失败') } }

function formatSize(b: number): string { if (!b) return '-'; const u = ['B', 'KB', 'MB', 'GB', 'TB']; let i = 0, s = b; while (s >= 1024 && i < u.length - 1) { s /= 1024; i++ } return s.toFixed(i ? 1 : 0) + ' ' + u[i] }
function formatCapacityStr(b: number): string { return b >= 1073741824 ? (b / 1073741824).toFixed(1) + ' GB' : (b / 1048576).toFixed(1) + ' MB' }
const capacityColor = computed(() => { const p = capacity.value.usagePercentage; return p >= 90 ? '#c95a5a' : p >= 70 ? '#b8934e' : '#7a8b9a' })

async function toggleTrash() { showTrash.value = !showTrash.value; showTrash.value ? await fetchTrash() : await fetchFiles() }
async function fetchTrash() { loading.value = true; try { const { data } = await request.get('/files/trash'); trashFiles.value = data || [] } catch {} finally { loading.value = false } }
async function restoreFile(path: string) { try { await request.post('/files/restore', null, { params: { path } }); ElMessage.success('已恢复'); fetchTrash() } catch { ElMessage.error('恢复失败') } }
async function permDeleteFile(path: string) { try { await ElMessageBox.confirm('永久删除？不可恢复', '确认', { type: 'warning' }); await request.delete('/files/permanent', { params: { path } }); ElMessage.success('已删除'); fetchTrash() } catch {} }
async function createShare() { try { const { data } = await request.post('/files/share', { path: sharePath.value, expireHours: shareExpiry.value }); ElMessage.success('分享链接: ' + data.data.url); shareVisible.value = false } catch { ElMessage.error('创建失败') } }
function openShare(path: string) { sharePath.value = path; shareVisible.value = true }

let nodeStatusTimer: number | undefined

onMounted(() => {
  fetchFiles(); fetchCapacity(); fetchUploadNodes(); fetchMyNodes()
  nodeStatusTimer = window.setInterval(fetchMyNodes, 10000)
})

onUnmounted(() => {
  if (nodeStatusTimer) window.clearInterval(nodeStatusTimer)
})
</script>

<template>
  <div class="home-root">
    <div class="toolbar">
      <div class="toolbar-actions">
        <el-upload class="upload-drag" drag :show-file-list="false" :http-request="handleUpload" multiple>
          <el-icon :size="28"><UploadFilled /></el-icon>
          <div class="upload-text">拖拽文件或点击上传</div>
        </el-upload>
        <el-button :icon="FolderAdd" @click="newFolderVisible = true">新建文件夹</el-button>
        <el-button :icon="Refresh" @click="fetchFiles">刷新</el-button>
        <el-button :type="showTrash ? 'warning' : 'default'" @click="toggleTrash">{{ showTrash ? '返回文件' : '回收站' }}</el-button>
        <el-tag :type="nodeStatusType" effect="plain">{{ nodeStatusText }}</el-tag>
        <el-button @click="openBindDialog">绑定手机节点</el-button>
        <el-select v-if="role === 'ADMIN'" v-model="selectedUploadNodeId" class="node-select" placeholder="自动选择节点" clearable>
          <el-option label="自动选择节点" value="" />
          <el-option
            v-for="node in availableUploadNodes"
            :key="node.id"
            :label="`${node.name || node.id} (${formatSize(node.deviceFree || 0)} free)`"
            :value="node.id"
          />
        </el-select>
      </div>
      <div class="capacity-widget">
        <div class="capacity-text"><span>已用 {{ formatCapacityStr(capacity.usedCapacity) }} / {{ formatCapacityStr(capacity.totalCapacity) }}</span><span>{{ capacity.usagePercentage }}%</span></div>
        <el-progress :percentage="capacity.usagePercentage" :color="capacityColor" :stroke-width="6" :show-text="false" />
      </div>
    </div>
    <div v-if="uploading" class="upload-progress-bar"><span class="upload-progress-label">上传：{{ uploadingName }}</span><el-progress :percentage="uploadProgress" :stroke-width="8" /></div>
    <div v-if="!showTrash" class="breadcrumb-bar">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="(item, idx) in breadcrumbItems" :key="item.path">
          <a v-if="idx < breadcrumbItems.length - 1" class="breadcrumb-link" @click="handleBreadcrumbClick(item.path)"><el-icon v-if="idx===0" :size="14"><HomeFilled /></el-icon> {{ item.name }}</a>
          <span v-else>{{ item.name }}</span>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="table-container">
      <el-table v-if="!showTrash" v-loading="loading" :data="files" stripe style="width:100%" row-class-name="file-row" @row-click="handleFileClick">
        <el-table-column label="文件名" min-width="260" sortable prop="fileName">
          <template #default="{ row }"><div class="file-name-cell"><el-icon :size="20" :color="row.isDirectory?'#b8934e':'#9ca3af'"><Folder v-if="row.isDirectory" /><Document v-else /></el-icon><span class="file-name-text">{{ row.fileName }}</span></div></template>
        </el-table-column>
        <el-table-column label="大小" width="110" align="right" sortable prop="size"><template #default="{ row }"><span class="file-meta">{{ row.isDirectory?'-':formatSize(row.size) }}</span></template></el-table-column>
        <el-table-column label="修改时间" width="170" align="right" sortable prop="lastModifiedTime"><template #default="{ row }"><span class="file-meta file-time">{{ row.lastModifiedTime }}</span></template></el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }"><div class="action-cell" @click.stop>
            <el-button type="primary" link size="small" :icon="Edit" @click="openRename(row)">重命名</el-button>
            <el-button type="warning" link size="small" @click="openShare(currentPath ? currentPath+'/'+row.fileName : row.fileName)">分享</el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </div></template>
        </el-table-column>
        <template #empty><el-empty description="此文件夹为空" :image-size="80" /></template>
      </el-table>
      <el-table v-if="showTrash" v-loading="loading" :data="trashFiles" stripe style="width:100%" row-class-name="file-row">
        <el-table-column label="文件名" min-width="260" prop="fileName" />
        <el-table-column label="大小" width="110" align="right"><template #default="{ row }">{{ formatSize(row.size) }}</template></el-table-column>
        <el-table-column label="删除时间" width="170" align="right" prop="lastModifiedTime" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }"><div @click.stop>
            <el-button type="primary" link size="small" @click="restoreFile(currentPath ? currentPath+'/'+row.fileName : row.fileName)">还原</el-button>
            <el-button type="danger" link size="small" @click="permDeleteFile(currentPath ? currentPath+'/'+row.fileName : row.fileName)">删除</el-button>
          </div></template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="newFolderVisible" title="新建文件夹" width="400px" @keyup.enter="createFolder">
      <el-input v-model="newFolderName" placeholder="文件夹名" size="large" />
      <template #footer><el-button @click="newFolderVisible=false">取消</el-button><el-button type="primary" @click="createFolder">创建</el-button></template>
    </el-dialog>
    <el-dialog v-model="renameVisible" title="重命名" width="400px" @keyup.enter="handleRename">
      <el-input v-model="renameNewName" placeholder="新名称" size="large" />
      <template #footer><el-button @click="renameVisible=false">取消</el-button><el-button type="primary" @click="handleRename">确定</el-button></template>
    </el-dialog>
    <el-dialog v-model="shareVisible" title="创建分享链接" width="400px">
      <el-select v-model="shareExpiry" style="width:100%">
        <el-option :value="1" label="1小时后过期" /><el-option :value="24" label="24小时后过期" /><el-option :value="168" label="7天后过期" /><el-option :value="720" label="30天后过期" />
      </el-select>
      <template #footer><el-button @click="shareVisible=false">取消</el-button><el-button type="primary" @click="createShare">生成链接</el-button></template>
    </el-dialog>
    <el-dialog v-model="bindDialog" title="绑定手机节点" width="420px">
      <div v-loading="loadingBindTicket" class="bind-box">
        <img v-if="bindQrCode" :src="bindQrCode" alt="手机节点绑定二维码" class="bind-qr" />
        <div class="bind-tip">打开手机 App，点击“扫码绑定”后扫描此二维码。</div>
        <div class="bind-expire">有效期至：{{ bindTicket.expiresAt }}</div>
      </div>
      <template #footer>
        <el-button @click="bindDialog=false">关闭</el-button>
        <el-button @click="openBindDialog">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.home-root { min-height: 100vh; background: #f5f6f8; }
.toolbar { display: flex; align-items: center; justify-content: space-between; padding: 16px 28px; gap: 16px; flex-wrap: wrap; }
.toolbar-actions { display: flex; align-items: center; gap: 10px; }
.node-select { width: 220px; }
.bind-box { min-height: 280px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; }
.bind-qr { width: 220px; height: 220px; }
.bind-tip { color: #374151; font-size: 14px; }
.bind-expire { color: #9ca3af; font-size: 12px; }
.upload-drag { margin-right: 8px; }
.upload-text { font-size: 13px; color: #9ca3af; margin-top: 8px; }
.upload-progress-bar { padding: 0 28px 16px; display: flex; flex-direction: column; gap: 6px; }
.upload-progress-label { font-size: 12px; color: #6b7280; }
.capacity-widget { display: flex; flex-direction: column; gap: 6px; min-width: 220px; }
.capacity-text { display: flex; justify-content: space-between; font-size: 12px; color: #9ca3af; }
.breadcrumb-bar { padding: 0 28px 12px; }
.breadcrumb-link { color: #6b7280; cursor: pointer; text-decoration: none; }
.breadcrumb-link:hover { color: #374151; }
.table-container { padding: 0 28px 40px; }
.file-name-cell { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.file-name-text { color: #374151; }
.file-name-cell:hover .file-name-text { color: #6b7280; }
.file-meta { font-size: 13px; color: #9ca3af; }
.file-time { font-size: 12px; }
.action-cell { display: flex; gap: 4px; justify-content: center; }
@media (max-width: 640px) { .toolbar { padding: 12px 16px; gap: 8px; flex-direction: column; align-items: stretch; } .toolbar-actions { flex-wrap: wrap; } .capacity-widget { min-width: unset; width: 100%; } .table-container { padding: 0 8px 40px; } }
</style>
