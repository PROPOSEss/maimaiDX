<template>
  <view class="page">
    <view class="login-container">
      <view class="logo-section">
        <text class="logo-text">MaiDX</text>
        <text class="logo-sub">Insight</text>
      </view>
      <text class="login-desc">舞萌 DX 玩家成长分析与推歌推荐系统</text>

      <button class="btn-wx-login" @tap="handleLogin">
        本地 Mock 登录
      </button>

      <text class="login-hint">本地演示模式：点击后使用测试用户 tester 登录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

async function handleLogin() {
  try {
    await userStore.mockLogin('tester')
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 800)
  } catch {
    uni.showToast({ title: '登录失败', icon: 'none' })
  }
}
</script>

<style scoped>
.page { min-height: 100vh; background: linear-gradient(180deg, #1A1A2E 0%, #16213E 100%); }

.login-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 60rpx;
}

.logo-section { margin-bottom: 40rpx; }
.logo-text { display: block; font-size: 48px; font-weight: 700; color: #FF6B35; text-align: center; }
.logo-sub { display: block; font-size: 24px; font-weight: 300; color: rgba(255,255,255,0.6); text-align: center; margin-top: 8rpx; }

.login-desc { font-size: 14px; color: rgba(255,255,255,0.5); margin-bottom: 100rpx; }

.btn-wx-login {
  width: 100%;
  height: 96rpx;
  background: #07C160;
  color: #fff;
  border: none;
  border-radius: 16rpx;
  font-size: 17px;
  font-weight: 600;
}

.login-hint {
  font-size: 12px;
  color: rgba(255,255,255,0.3);
  margin-top: 40rpx;
}
</style>