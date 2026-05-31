<script setup lang="ts">
import { ref, computed } from 'vue'
const copied = ref(false)
const email = ref(localStorage.getItem('cloud_email') || '')
const cmd = computed(() => email.value ? `bash /sdcard/Download/setup.sh ${email.value}` : 'bash /sdcard/Download/setup.sh')
async function doCopy() {
  try { await navigator.clipboard.writeText(cmd.value); copied.value = true; setTimeout(() => copied.value = false, 2000) } catch {}
}
</script>

<template>
  <div class="join-root">
    <div class="join-card">
      <h1>加入存储网络</h1>
      <p>将旧手机变成云盘的一部分，只需 3 步</p>

      <div class="step">
        <div class="step-num">1</div>
        <div>
          <strong>安装 Termux</strong>
          <p>Termux 是安卓上的 Linux 终端，免费开源</p>
          <a href="https://f-droid.org/packages/com.termux/" target="_blank" class="btn-outline">去 F-Droid 下载</a>
        </div>
      </div>

      <div class="step">
        <div class="step-num">2</div>
        <div>
          <strong>下载安装脚本</strong>
          <p>点击下方按钮，下载 setup.sh 到手机 Download 文件夹</p>
          <a href="/setup.sh" download class="btn-outline">下载 setup.sh</a>
        </div>
      </div>

      <div class="step">
        <div class="step-num">3</div>
        <div>
          <strong>Termux 中执行</strong>
          <p>打开 Termux，粘贴下面这行命令，回车</p>
          <div class="cmd-box">
            <code>{{ cmd }}</code>
              <p v-if="!email" style="color:#b8934e;font-size:12px;margin-top:4px">未登录状态下部署的节点需管理员批准。建议先登录。</p>
            <button class="btn-copy" @click="doCopy">{{ copied ? '已复制' : '复制' }}</button>
          </div>
        </div>
      </div>

      <div class="note">
        脚本自动完成：<strong>安装 Java、下载服务端、启动存储</strong>。完成后去管理后台批准节点即可。
      </div>
    </div>
  </div>
</template>

<style scoped>
.join-root { min-height: 100vh; background: #f5f6f8; display: flex; align-items: flex-start; justify-content: center; padding: 60px 20px; }
.join-card { max-width: 560px; width: 100%; background: #fff; border-radius: 12px; padding: 40px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.join-card h1 { font-size: 24px; font-weight: 700; color: #374151; margin: 0 0 4px; }
.join-card > p { color: #9ca3af; font-size: 14px; margin: 0 0 32px; }
.step { display: flex; gap: 16px; margin-bottom: 24px; }
.step-num { width: 32px; height: 32px; background: #e5e7eb; color: #374151; font-weight: 700; font-size: 16px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.step strong { display: block; font-size: 16px; color: #374151; margin-bottom: 4px; }
.step p { font-size: 13px; color: #9ca3af; margin: 0 0 8px; }
.btn-outline { display: inline-block; padding: 8px 20px; border: 1px solid #dde0e4; border-radius: 6px; color: #374151; text-decoration: none; font-size: 14px; cursor: pointer; background: #fff; }
.btn-outline:hover { background: #f5f6f8; }
.cmd-box { display: flex; align-items: center; background: #1e293b; border-radius: 8px; padding: 12px 16px; gap: 12px; }
.cmd-box code { color: #e2e8f0; font-size: 13px; flex: 1; word-break: break-all; }
.btn-copy { background: #334155; color: #e2e8f0; border: none; padding: 6px 16px; border-radius: 6px; font-size: 13px; cursor: pointer; white-space: nowrap; }
.btn-copy:hover { background: #475569; }
.note { margin-top: 24px; padding: 16px; background: #f0f9ff; border-radius: 8px; font-size: 13px; color: #475569; }
</style>
