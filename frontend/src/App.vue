<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const isGuest = computed(() => route.path === '/login' || route.path === '/register')

const role = ref(localStorage.getItem('cloud_role'))
const email = ref(localStorage.getItem('cloud_email'))

watch(() => route.path, () => {
  role.value = localStorage.getItem('cloud_role')
  email.value = localStorage.getItem('cloud_email')
})

function handleLogout() {
  localStorage.removeItem('cloud_token')
  localStorage.removeItem('cloud_role')
  localStorage.removeItem('cloud_email')
  localStorage.removeItem('cloud_membership')
  router.push('/login')
}
</script>

<template>
  <router-view v-if="isGuest" />
  <div v-else class="layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <el-icon :size="20" color="#6b7280"><Cloudy /></el-icon>
        <span>JiaLe Cloud</span>
      </div>
      <div class="sidebar-user">
        <el-icon :size="16"><User /></el-icon>
        <span>{{ email }}</span>
        <el-tag v-if="role === 'ADMIN'" size="small" type="danger" style="margin-left:4px;">管理员</el-tag>
      </div>
      <el-menu :default-active="route.path" router>
        <el-menu-item v-if="role === 'ADMIN'" index="/admin">
          <el-icon><Setting /></el-icon> 管理后台
        </el-menu-item>
        <el-menu-item index="/">
          <el-icon><FolderOpened /></el-icon> 文件管理
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <el-button text @click="handleLogout">
          <el-icon><SwitchButton /></el-icon> 退出
        </el-button>
      </div>
    </aside>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", Arial, sans-serif; }
.layout { display: flex; height: 100vh; }
.sidebar { width: 220px; background: #fff; border-right: 1px solid #e8eaed; display: flex; flex-direction: column; }
.sidebar-header { padding: 20px 20px 12px; display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 600; color: #374151; }
.sidebar-user { padding: 0 20px 16px; display: flex; align-items: center; gap: 6px; font-size: 13px; color: #9ca3af; }
.sidebar-footer { margin-top: auto; padding: 12px 20px; }
.main-content { flex: 1; overflow-y: auto; background: #f5f6f8; }
</style>
