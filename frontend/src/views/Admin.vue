<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import request from '../utils/request'

const router = useRouter()
const activeTab = ref('dashboard')
const stats = ref({ totalUsers: 0, enabledUsers: 0, vipUsers: 0 })
const users = ref<any[]>([])
const loadingUsers = ref(false)

// ---- user form ----
const userDialog = ref(false)
const editingUser = ref('')
const userForm = ref({ email: '', password: '', role: 'USER', membership: 'FREE', quota: 5, enabled: true })

// ---- nodes ----
const nodes = ref<any[]>([])
const loadingNodes = ref(false)
const bindDialog = ref(false)
const bindMode = ref<'methods' | 'qr' | 'manual'>('methods')
const loadingBindTicket = ref(false)
const bindTicket = ref({ ticket: '', deepLink: '', expiresAt: '' })
const bindQrCode = ref('')

async function fetchNodes() {
  loadingNodes.value = true
  try { const { data } = await request.get('/admin/nodes'); nodes.value = data || [] } catch {}
  loadingNodes.value = false
}

async function approveNode(id: string) {
  try {
    await request.post(`/admin/nodes/${id}/approve`)
    ElMessage.success('节点已批准')
    fetchNodes()
  } catch { ElMessage.error('操作失败') }
}

async function removeNode(id: string) {
  try {
    await ElMessageBox.confirm(`确定删除节点「${id}」？`, '删除确认', { type: 'warning' })
    await request.delete(`/admin/nodes/${id}`)
    ElMessage.success('节点已删除')
    fetchNodes()
  } catch (err: any) { if (err !== 'cancel' && err !== 'close') ElMessage.error('删除失败') }
}

function openBindDialog() {
  bindMode.value = 'methods'
  bindTicket.value = { ticket: '', deepLink: '', expiresAt: '' }
  bindQrCode.value = ''
  bindDialog.value = true
}

async function createBindTicket(mode: 'qr' | 'manual') {
  loadingBindTicket.value = true
  try {
    const { data } = await request.post('/app-bind/tickets')
    bindTicket.value = data.data
    bindMode.value = mode
    bindQrCode.value = mode === 'qr'
      ? await QRCode.toDataURL(bindTicket.value.deepLink, { width: 220, margin: 1 })
      : ''
    bindDialog.value = true
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '生成绑定码失败')
  } finally {
    loadingBindTicket.value = false
  }
}

async function copyBindText(value: string, successMessage: string) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(successMessage)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

// ---- admin file browser ----
const selectedUser = ref('')
const adminFiles = ref<any[]>([])
const adminPath = ref('')
const loadingFiles = ref(false)

onMounted(() => { fetchStats(); fetchUsers(); fetchNodes() })

async function fetchStats() {
  try { const { data } = await request.get('/admin/stats'); stats.value = data } catch {}
}

async function fetchUsers() {
  loadingUsers.value = true
  try { const { data } = await request.get('/admin/users'); users.value = data } catch {}
  loadingUsers.value = false
}

function openCreate() {
  editingUser.value = ''
  userForm.value = { email: '', password: '', role: 'USER', membership: 'FREE', quota: 5, enabled: true }
  userDialog.value = true
}

function openEdit(u: any) {
  editingUser.value = u.email
  userForm.value = {
    email: u.email,
    password: '',
    role: u.role,
    membership: u.membership || 'FREE',
    quota: u.quota > 0 ? u.quota / 1073741824 : 5,
    enabled: u.enabled
  }
  userDialog.value = true
}

async function saveUser() {
  const payload: any = { ...userForm.value, quota: userForm.value.quota * 1073741824 }
  try {
    if (editingUser.value) {
      await request.put(`/admin/users/${encodeURIComponent(editingUser.value)}`, payload)
      ElMessage.success('用户已更新')
    } else {
      await request.post('/admin/users', payload)
      ElMessage.success('用户已创建')
    }
    userDialog.value = false
    fetchUsers()
    fetchStats()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '操作失败')
  }
}

async function deleteUser(email: string) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${email}」？其文件将被永久删除。`, '删除确认', { type: 'warning' })
    await request.delete(`/admin/users/${encodeURIComponent(email)}`)
    ElMessage.success('用户已删除')
    fetchUsers()
    fetchStats()
  } catch (err: any) { if (err !== 'cancel' && err !== 'close') ElMessage.error('删除失败') }
}

async function resetUserPassword(email: string) {
  try {
    const { value: newPwd } = await ElMessageBox.prompt('请输入新密码（至少6位）', '重置密码', {
      confirmButtonText: '确认重置', inputType: 'password', inputValidator: (v) => v && v.length >= 6 ? true : '至少6位',
    })
    const { data } = await request.post(`/admin/users/${encodeURIComponent(email)}/reset-password`, { password: newPwd })
    ElMessage.success(data.message || '密码已重置')
  } catch (err: any) { if (err !== 'cancel' && err !== 'close') ElMessage.error('重置失败') }
}

async function browseUserFiles(user: string) {
  selectedUser.value = user
  adminPath.value = ''
  await loadAdminFiles()
}

async function loadAdminFiles() {
  loadingFiles.value = true
  try {
    const { data } = await request.get('/admin/files', { params: { user: selectedUser.value, path: adminPath.value } })
    adminFiles.value = data || []
    if (data && data.length > 0) activeTab.value = 'files'
  } catch { ElMessage.error('加载失败') }
  loadingFiles.value = false
}

function adminEnterDir(dirName: string) {
  adminPath.value = adminPath.value ? adminPath.value + '/' + dirName : dirName
  loadAdminFiles()
}

function adminBreadcrumb(path: string) {
  adminPath.value = path
  loadAdminFiles()
}

function formatSize(bytes: number): string {
  if (!bytes || bytes === 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0; let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function formatQuota(bytes: number): string {
  if (!bytes || bytes === 0) return '不限'
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(0) + ' GB'
  return (bytes / 1048576).toFixed(0) + ' MB'
}
</script>

<template>
  <div class="admin-root">
    <h2 class="page-title">管理后台</h2>

    <el-tabs v-model="activeTab">
      <!-- ====== 仪表盘 ====== -->
      <el-tab-pane label="仪表盘" name="dashboard">
        <div class="stat-cards">
          <div class="stat-card">
            <div class="stat-value">{{ stats.totalUsers }}</div>
            <div class="stat-label">用户总数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ stats.enabledUsers }}</div>
            <div class="stat-label">已启用</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ stats.vipUsers }}</div>
            <div class="stat-label">VIP用户</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ stats.totalUsers - stats.enabledUsers }}</div>
            <div class="stat-label">已禁用</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ====== 用户管理 ====== -->
      <el-tab-pane label="用户管理" name="users">
        <div style="margin-bottom: 16px;">
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
        <el-table :data="users" v-loading="loadingUsers" stripe>
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column prop="role" label="角色" width="90">
            <template #default="{ row }">
              <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="会员" width="80">
            <template #default="{ row }">
              <el-tag :type="row.membership === 'VIP' ? 'warning' : 'info'" size="small">{{ row.membership }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="配额" width="90">
            <template #default="{ row }">{{ formatQuota(row.quota) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="220">
            <template #default="{ row }">
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" @click="browseUserFiles(row.email)">查看文件</el-button>
              <el-button size="small" type="warning" @click="resetUserPassword(row.email)">重置密码</el-button>
              <el-button size="small" type="danger" @click="deleteUser(row.email)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ====== 节点管理 ====== -->
      <el-tab-pane label="节点管理" name="nodes">
        <div style="margin-bottom: 16px;">
          <el-button type="primary" @click="openBindDialog">添加旧手机节点</el-button>
        </div>
        <el-table :data="nodes" v-loading="loadingNodes" stripe>
          <el-table-column prop="id" label="节点ID" width="100" />
          <el-table-column prop="name" label="名称" width="100" />
          <el-table-column prop="frpPort" label="端口" width="70" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'online' ? 'success' : row.status === 'offline' ? 'danger' : row.status === 'activating' ? 'info' : 'warning'" size="small">
                {{ row.status === 'online' ? '在线' : row.status === 'offline' ? '离线' : row.status === 'activating' ? '等待激活' : '待批准' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="容量/剩余" width="140">
            <template #default="{ row }">{{ formatSize(row.quota) }} / {{ formatSize(row.deviceFree) }}</template>
          </el-table-column>
          <el-table-column label="已用" width="90">
            <template #default="{ row }">{{ formatSize(row.usedCapacity) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="140">
            <template #default="{ row }">
              <el-button v-if="row.status === 'pending'" size="small" type="success" @click="approveNode(row.id)">批准</el-button>
              <el-button size="small" type="danger" @click="removeNode(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ====== 管理员文件浏览 ====== -->
      <el-tab-pane label="用户文件" name="files">
        <div v-if="!selectedUser">
          <el-empty description="请先在「用户管理」中点击「查看文件」" />
        </div>
        <div v-else>
          <div style="margin-bottom: 12px; color: #6b7280; font-size: 14px;">
            当前用户：<strong>{{ selectedUser }}</strong>
            <el-button size="small" style="margin-left: 12px;" @click="loadAdminFiles">刷新</el-button>
          </div>
          <el-breadcrumb separator="/" style="margin-bottom: 12px;">
            <el-breadcrumb-item><a @click="adminBreadcrumb('')">根目录</a></el-breadcrumb-item>
            <el-breadcrumb-item v-for="(p, idx) in adminPath.split('/').filter(Boolean)" :key="idx">
              <a @click="adminBreadcrumb(adminPath.split('/').slice(0, idx + 1).join('/'))">{{ p }}</a>
            </el-breadcrumb-item>
          </el-breadcrumb>
          <el-table :data="adminFiles" v-loading="loadingFiles" stripe @row-click="(row: any) => row.isDirectory ? adminEnterDir(row.fileName) : null">
            <el-table-column label="文件名" min-width="280">
              <template #default="{ row }">
                <el-icon :size="18" :color="row.isDirectory ? '#b8934e' : '#9ca3af'" style="margin-right:8px;">
                  <Folder v-if="row.isDirectory" /><Document v-else />
                </el-icon>
                {{ row.fileName }}
              </template>
            </el-table-column>
            <el-table-column label="大小" width="120" align="right">
              <template #default="{ row }">{{ row.isDirectory ? '-' : formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column label="修改时间" width="170" align="right" prop="lastModifiedTime" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 用户表单弹窗 -->
    <el-dialog v-model="userDialog" :title="editingUser ? '编辑用户' : '新增用户'" width="460px">
      <el-form :model="userForm" label-width="80px">
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" :disabled="!!editingUser" placeholder="user@example.com" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="userForm.password" type="password" :placeholder="editingUser ? '留空不修改' : '请输入密码'" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员">
          <el-select v-model="userForm.membership">
            <el-option label="免费 (5GB)" value="FREE" />
            <el-option label="VIP (30GB)" value="VIP" />
          </el-select>
        </el-form-item>
        <el-form-item label="配额(GB)">
          <el-input-number v-model="userForm.quota" :min="0" :max="100" :step="1" /> <span style="margin-left:8px;color:#9ca3af;">(GB, 0=不限)</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="userForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindDialog" title="添加旧手机节点" width="620px">
      <div v-if="bindMode === 'methods'" class="bind-methods">
        <button class="bind-method" type="button" :disabled="loadingBindTicket" @click="createBindTicket('qr')">
          <span class="bind-method-title">扫描二维码</span>
          <span class="bind-method-desc">旧手机打开 Yunpan App，扫描网页上的二维码完成绑定。</span>
        </button>
        <button class="bind-method" type="button" :disabled="loadingBindTicket" @click="createBindTicket('manual')">
          <span class="bind-method-title">输入绑定码</span>
          <span class="bind-method-desc">无法扫码、模拟器测试或相机不可用时，手动复制绑定码到 App。</span>
        </button>
      </div>

      <div v-else-if="bindMode === 'qr'" class="bind-panel" v-loading="loadingBindTicket">
        <div class="qr-wrap">
          <img v-if="bindQrCode" :src="bindQrCode" alt="旧手机节点绑定二维码" class="qr-image" />
        </div>
        <div class="bind-info">
          <div class="bind-title">使用旧手机 App 扫描二维码</div>
          <div class="bind-desc">二维码只包含一次性绑定信息，过期或绑定成功后不可再次使用。</div>
          <div class="bind-expire">有效期至：{{ bindTicket.expiresAt }}</div>
          <div class="bind-actions">
            <el-button @click="copyBindText(bindTicket.deepLink, '扫码内容已复制')">复制扫码内容</el-button>
            <el-button @click="createBindTicket('qr')">重新生成</el-button>
          </div>
        </div>
      </div>

      <el-form v-else label-width="90px">
        <el-form-item label="绑定码">
          <el-input v-model="bindTicket.ticket" readonly>
            <template #append>
              <el-button @click="copyBindText(bindTicket.ticket, '绑定码已复制')">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="扫码内容">
          <el-input v-model="bindTicket.deepLink" type="textarea" :rows="3" readonly />
        </el-form-item>
        <el-form-item label="有效期至">
          <span>{{ bindTicket.expiresAt }}</span>
        </el-form-item>
        <el-form-item>
          <el-button @click="copyBindText(bindTicket.deepLink, '扫码内容已复制')">复制扫码内容</el-button>
          <el-button @click="createBindTicket('manual')">重新生成</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="bindMode !== 'methods'" @click="bindMode = 'methods'">返回选择方式</el-button>
        <el-button @click="bindDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-root { padding: 24px 28px; }
.page-title { font-size: 20px; font-weight: 600; color: #374151; margin-bottom: 20px; }
.stat-cards { display: flex; gap: 20px; }
.stat-card { flex: 1; max-width: 200px; padding: 24px; background: #fff; border-radius: 10px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.stat-value { font-size: 32px; font-weight: 700; color: #374151; }
.stat-label { font-size: 13px; color: #9ca3af; margin-top: 6px; }
.bind-methods { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.bind-method {
  min-height: 132px;
  padding: 18px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}
.bind-method:hover:not(:disabled) { border-color: #409eff; box-shadow: 0 8px 22px rgba(64,158,255,.14); transform: translateY(-1px); }
.bind-method:disabled { cursor: not-allowed; opacity: .6; }
.bind-method-title { display: block; margin-bottom: 10px; color: #1f2937; font-size: 16px; font-weight: 600; }
.bind-method-desc { display: block; color: #6b7280; font-size: 13px; line-height: 1.7; }
.bind-panel { display: grid; grid-template-columns: 244px 1fr; gap: 22px; align-items: center; }
.qr-wrap { width: 244px; height: 244px; display: flex; align-items: center; justify-content: center; border: 1px solid #e5e7eb; border-radius: 8px; background: #f9fafb; }
.qr-image { width: 220px; height: 220px; }
.bind-info { min-width: 0; }
.bind-title { color: #1f2937; font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.bind-desc { color: #6b7280; font-size: 13px; line-height: 1.7; margin-bottom: 12px; }
.bind-expire { color: #374151; font-size: 13px; margin-bottom: 16px; }
.bind-actions { display: flex; gap: 10px; flex-wrap: wrap; }
@media (max-width: 720px) {
  .bind-methods { grid-template-columns: 1fr; }
  .bind-panel { grid-template-columns: 1fr; }
  .qr-wrap { width: 100%; }
}
</style>
