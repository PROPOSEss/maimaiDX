<template>
  <view class="page">
    <view class="header">
      <text class="title">个人中心</text>
    </view>

    <!-- 玩家信息 -->
    <view class="info-card" v-if="userStore.playerInfo">
      <view class="info-row">
        <text class="info-label">昵称</text>
        <text class="info-value">{{ userStore.playerInfo.playerName || '未设置' }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">MAID</text>
        <text class="info-value">{{ userStore.playerInfo.maId }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">Rating</text>
        <text class="info-value rating">{{ userStore.playerInfo.rating }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">最后同步</text>
        <text class="info-value">{{ userStore.playerInfo.lastSyncTime || '未同步' }}</text>
      </view>
    </view>

    <!-- 操作按钮 -->
    <view class="action-section">
      <button class="action-btn" @tap="goBind" v-if="!userStore.bindStatus">绑定MAID</button>
      <button class="action-btn sync" @tap="handleSync" v-else>同步成绩</button>
      <button class="action-btn unbind" @tap="handleUnbind" v-if="userStore.bindStatus">解绑MAID</button>
      <button class="action-btn logout" @tap="handleLogout">退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useUserStore } from '../../stores/user'
import { syncScores, unbindMaId } from '../../api'

const userStore = useUserStore()

function goBind() {
  uni.navigateTo({ url: '/pages/bind/bind' })
}

async function handleSync() {
  uni.showLoading({ title: '同步中...' })
  try {
    await syncScores()
    await userStore.refreshPlayerInfo()
    uni.showToast({ title: '同步成功', icon: 'success' })
  } catch {
    uni.showToast({ title: '同步失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function handleUnbind() {
  uni.showModal({
    title: '确认解绑',
    content: '解绑后将清除所有成绩数据，确定？',
    success: async (res) => {
      if (res.confirm) {
        await unbindMaId()
        userStore.refreshPlayerInfo()
        uni.showToast({ title: '已解绑', icon: 'success' })
      }
    },
  })
}

function handleLogout() {
  userStore.logout()
  uni.showToast({ title: '已退出登录', icon: 'success' })
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f5f5f5; }
.header { padding: 30rpx; background: linear-gradient(135deg, #1A1A2E, #16213E); }
.title { font-size: 22px; font-weight: 700; color: #fff; }

.info-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 30rpx;
  margin: 30rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}
.info-row:last-child { border-bottom: none; }
.info-label { font-size: 14px; color: #999; }
.info-value { font-size: 14px; color: #333; font-weight: 500; }
.info-value.rating { color: #FF6B35; font-weight: 700; font-size: 18px; }

.action-section { padding: 30rpx; }
.action-btn {
  width: 100%;
  height: 88rpx;
  border-radius: 16rpx;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 20rpx;
  background: #FF6B35;
  color: #fff;
  border: none;
}
.action-btn.sync { background: #2A9D8F; }
.action-btn.unbind { background: #fff; color: #999; border: 1rpx solid #ddd; }
.action-btn.logout { background: #fff; color: #E63946; border: 1rpx solid #E63946; }
</style>
