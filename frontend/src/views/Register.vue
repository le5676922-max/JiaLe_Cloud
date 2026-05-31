<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { sha256, generateFileKey, encryptFileKey } from '../utils/crypto'

const router = useRouter()

const step = ref(1)
const email = ref('')
const code = ref('')
const password = ref('')
const loading = ref(false)
const sendCountdown = ref(0)

async function sendCode() {
  if (!email.value || !email.value.includes('@')) {
    ElMessage.error('请输入有效的邮箱地址')
    return
  }
  if (sendCountdown.value > 0) return
  loading.value = true
  try {
    await request.post('/register/send-code', { email: email.value.trim() })
    ElMessage.success('验证码已发送，请查收邮件')
    sendCountdown.value = 60
    const timer = setInterval(() => {
      sendCountdown.value--
      if (sendCountdown.value <= 0) clearInterval(timer)
    }, 1000)
    step.value = 2
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '发送失败')
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!code.value.trim() || !password.value) {
    ElMessage.error('请填写所有字段')
    return
  }
  if (password.value.length < 6) {
    ElMessage.error('密码至少6位')
    return
  }
  loading.value = true
  try {
    // 浏览器端：密码哈希 + 生成文件加密密钥
    const pwd = password.value
    password.value = '' // 立即清空明文
    const passwordHash = await sha256(pwd)
    const fileKey = await generateFileKey()
    const encryptedKey = await encryptFileKey(passwordHash, fileKey)

    const { data } = await request.post('/register', {
      email: email.value.trim(),
      code: code.value.trim(),
      password: passwordHash,
      encryptedKey,
    })
    if (data.code === 200) {
      ElMessage.success('注册成功！请登录')
      router.push('/login')
    } else {
      ElMessage.error(data.message || '注册失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-wrapper">
    <div class="register-card">
      <div class="register-header">
        <el-icon :size="36" color="#6b7280"><Cloudy /></el-icon>
        <h1>注册 JiaLe Cloud</h1>
        <p>已有账号？<router-link to="/login">立即登录</router-link></p>
      </div>

      <!-- Step 1: 邮箱 + 发送验证码 -->
      <el-form v-if="step === 1" @submit.prevent="sendCode" label-position="top">
        <el-form-item label="邮箱地址">
          <el-input v-model="email" placeholder="请输入邮箱" size="large" type="email">
            <template #prefix><el-icon><Message /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="full-btn" :loading="loading" @click="sendCode">
            发送验证码
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Step 2: 验证码 + 密码 -->
      <el-form v-else @submit.prevent="doRegister" label-position="top">
        <el-form-item label="验证码">
          <el-input v-model="code" placeholder="请输入6位验证码" size="large" maxlength="6" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" placeholder="至少6位" size="large" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="full-btn" :loading="loading" @click="doRegister">
            注册
          </el-button>
          <el-button size="large" class="full-btn" style="margin-top:8px;" @click="step = 1; sendCountdown = 0">
            返回修改邮箱
          </el-button>
        </el-form-item>
        <div style="text-align:center;font-size:12px;color:#9ca3af;">
          <template v-if="sendCountdown > 0">{{ sendCountdown }}秒后可重新发送</template>
          <a v-else @click="sendCode" style="color:#6b7280;cursor:pointer;">重新发送验证码</a>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.register-wrapper { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #f5f6f8; }
.register-card { width: 420px; max-width: 92vw; padding: 48px 40px 40px; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 4px 16px rgba(0,0,0,0.04); }
.register-header { text-align: center; margin-bottom: 32px; }
.register-header h1 { margin: 12px 0 6px; font-size: 22px; font-weight: 600; color: #374151; }
.register-header p { font-size: 14px; color: #9ca3af; }
.register-header a { color: #6b7280; text-decoration: none; }
.register-header a:hover { color: #374151; }
.full-btn { width: 100%; }
</style>
