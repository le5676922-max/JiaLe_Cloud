<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Message } from '@element-plus/icons-vue'
import request from '../utils/request'
import { sha256 } from '../utils/crypto'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = ref({
  email: '',
  password: '',
})

const rules = {
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (loading.value) return
  if (!formRef.value) return

  try { await formRef.value.validate() } catch { return }

  loading.value = true
  try {
    const pwd = form.value.password
    form.value.password = '' // 立即清空明文，防止泄露到控制台
    const hash = await sha256(pwd)
    const { data } = await request.post('/auth/login', {
      email: form.value.email.trim(),
      password: hash,
    })
    if (data.code === 200) {
      localStorage.setItem('cloud_token', data.data.token)
      localStorage.setItem('cloud_role', data.data.role)
      localStorage.setItem('cloud_email', data.data.email)
      localStorage.setItem('cloud_membership', data.data.membership || 'FREE')
      if (data.data.role === 'ADMIN') {
        router.push('/admin')
      } else {
        router.push('/')
      }
    } else {
      ElMessage.error(data.message || '登录失败')
    }
  } catch (err: any) {
    if (err?.response?.status === 401) {
      ElMessage.error(err?.response?.data?.message || '邮箱或密码错误')
    } else if (err?.response?.status) {
      ElMessage.error('服务器错误，请稍后重试')
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrapper">
    <div class="login-card">
      <div class="login-header">
        <el-icon :size="36" color="#6b7280"><Cloudy /></el-icon>
        <h1>JiaLe Cloud</h1>
        <p>登录以访问您的文件</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱"
            :prefix-icon="Message"
            size="large"
            type="email"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div style="text-align:center;font-size:13px;color:#9ca3af;">
        <router-link to="/register" style="color:#6b7280;text-decoration:none;">没有账号？立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #f5f6f8; }
.login-card { width: 400px; max-width: 92vw; padding: 48px 40px 40px; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 4px 16px rgba(0,0,0,0.04); }
.login-header { text-align: center; margin-bottom: 36px; }
.login-header h1 { margin: 12px 0 6px; font-size: 22px; font-weight: 600; color: #374151; letter-spacing: 0.5px; }
.login-header p { margin: 0; font-size: 14px; color: #9ca3af; }
.login-btn { width: 100%; margin-top: 4px; }
</style>
