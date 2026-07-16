<template>
  <view class="page">
    <view class="header">
      <text class="title">绑定MAID</text>
      <text class="subtitle">输入你的舞萌MAID以同步成绩数据</text>
    </view>

    <view class="form-section">
      <view class="input-group">
        <text class="input-label">MAID</text>
        <input
          v-model="maId"
          placeholder="请输入你的MAID"
          class="input-field"
          :maxlength="12"
          type="text"
        />
      </view>

      <button class="btn-submit" :disabled="!maId || submitting" @tap="handleSubmit">
        {{ submitting ? '绑定中...' : '确认绑定' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { bindMaId } from '../../api'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const maId = ref('')
const submitting = ref(false)

async function handleSubmit() {
  if (!maId.value.trim()) {
    uni.showToast({ title: '请输入MAID', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    await bindMaId(maId.value.trim())
    uni.showToast({ title: '绑定成功', icon: 'success' })
    await userStore.refreshPlayerInfo()
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (err: any) {
    uni.showToast({ title: err.message || '绑定失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f5f5f5; }
.header { padding: 30rpx; background: linear-gradient(135deg, #1A1A2E, #16213E); }
.title { font-size: 22px; font-weight: 700; color: #fff; }
.subtitle { display: block; font-size: 13px; color: rgba(255,255,255,0.6); margin-top: 8rpx; }

.form-section { padding: 60rpx 30rpx; }
.input-group { margin-bottom: 40rpx; }
.input-label { display: block; font-size: 14px; font-weight: 600; color: #333; margin-bottom: 16rpx; }
.input-field {
  background: #fff;
  border: 1rpx solid #ddd;
  border-radius: 12rpx;
  padding: 24rpx;
  font-size: 16px;
}
.btn-submit {
  width: 100%;
  height: 88rpx;
  background: #FF6B35;
  color: #fff;
  border: none;
  border-radius: 16rpx;
  font-size: 16px;
  font-weight: 600;
}
.btn-submit:disabled { opacity: 0.5; }
</style>
